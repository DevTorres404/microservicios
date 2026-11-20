-- Inventory service schema, owned exclusively by servicio-inventario (inventario_db).
-- No table of this file references, joins or mirrors the order service schema.
-- The order identifier (order_id) is an opaque external value received over HTTP.

CREATE TABLE stock (
    sku            VARCHAR(64)    NOT NULL,
    available      INTEGER        NOT NULL,
    unit_price     NUMERIC(19, 2) NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_stock PRIMARY KEY (sku),
    CONSTRAINT ck_stock_available_non_negative CHECK (available >= 0),
    CONSTRAINT ck_stock_unit_price_non_negative CHECK (unit_price >= 0)
);

-- No secondary index is required on stock: every access path (read one, list all ordered by sku,
-- conditional decrement by sku) uses the primary key index.

CREATE TABLE reservations (
    id          BIGSERIAL      NOT NULL,
    order_id    BIGINT         NOT NULL,
    status      VARCHAR(20)    NOT NULL DEFAULT 'APLICADA',
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    released_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_reservations PRIMARY KEY (id),
    -- Idempotency key: one reservation per order, so a retried request can never consume twice.
    CONSTRAINT uq_reservations_order UNIQUE (order_id),
    CONSTRAINT ck_reservations_status CHECK (status IN ('APLICADA', 'LIBERADA'))
);

CREATE TABLE reservation_lines (
    id           BIGSERIAL      NOT NULL,
    reservation_id BIGINT       NOT NULL,
    order_id     BIGINT         NOT NULL,
    sku          VARCHAR(64)    NOT NULL,
    quantity     INTEGER        NOT NULL,
    unit_price   NUMERIC(19, 2) NOT NULL,
    CONSTRAINT pk_reservation_lines PRIMARY KEY (id),
    CONSTRAINT fk_reservation_lines_reservation FOREIGN KEY (reservation_id)
        REFERENCES reservations (id) ON DELETE CASCADE,
    -- Second idempotency guard: the same order cannot hold two lines for the same SKU.
    CONSTRAINT uq_reservation_lines_order_sku UNIQUE (order_id, sku),
    CONSTRAINT ck_reservation_lines_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_reservation_lines_unit_price_non_negative CHECK (unit_price >= 0)
);

-- Serves the reservation release path (load the lines of one reservation) and the FK cascade.
CREATE INDEX idx_reservation_lines_reservation ON reservation_lines (reservation_id);
