-- Order service schema, owned exclusively by servicio-pedidos (pedidos_db).
-- No table of this file references, joins or mirrors the inventory service schema: stock is only
-- obtained from servicio-inventario over HTTP.

CREATE TABLE orders (
    id          BIGSERIAL      NOT NULL,
    customer    VARCHAR(120)   NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    total       NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    -- The lifecycle is a closed vocabulary: a typo cannot invent a new state.
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDIENTE', 'CONFIRMADO', 'RECHAZADO', 'CANCELADO')),
    CONSTRAINT ck_orders_total_non_negative CHECK (total >= 0),
    CONSTRAINT ck_orders_customer_not_blank CHECK (length(trim(customer)) > 0)
);

CREATE TABLE order_lines (
    id          BIGSERIAL      NOT NULL,
    order_id    BIGINT         NOT NULL,
    sku         VARCHAR(64)    NOT NULL,
    quantity    INTEGER        NOT NULL,
    -- Null until the inventory service confirms the current price; the customer never supplies it.
    unit_price  NUMERIC(19, 2),
    CONSTRAINT pk_order_lines PRIMARY KEY (id),
    CONSTRAINT fk_order_lines_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT ck_order_lines_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_order_lines_unit_price_non_negative CHECK (unit_price >= 0)
);

-- Serves GET /api/pedidos?estado=...: filter by state, newest first within one state.
CREATE INDEX idx_orders_status_created_at ON orders (status, created_at DESC);
-- Serves the unfiltered listing, newest first.
CREATE INDEX idx_orders_created_at ON orders (created_at DESC);
-- Serves the FK check and the cascade when an order is removed.
CREATE INDEX idx_order_lines_order ON order_lines (order_id);
