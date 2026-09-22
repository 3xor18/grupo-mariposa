package com.grupomariposa.orders.infrastructure.http;

public enum Dependency {
    CLIENTS_API("clients-api"),
    PRODUCTS_API("products-api");

    private final String id;

    Dependency(final String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
