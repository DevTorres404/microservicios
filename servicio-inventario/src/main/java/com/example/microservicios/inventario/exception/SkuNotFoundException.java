package com.example.microservicios.inventario.exception;

public class SkuNotFoundException extends DomainException {

    public static final String CODE = "SKU_NOT_FOUND";

    private final String sku;

    public SkuNotFoundException(String sku) {
        super("SKU not found: " + sku);
        this.sku = sku;
    }

    public String sku() {
        return sku;
    }

    @Override
    public String code() {
        return CODE;
    }
}
