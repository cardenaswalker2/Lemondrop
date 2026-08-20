package com.lemondrop.model;

public enum OrderStatus {
    RECEIVED("Pedido Recibido"),
    ACCEPTED("Aceptado"),
    PREPARING("En Preparación"),
    ALMOST_READY("Casi Listo"),
    READY("Listo para Recoger"),
    DELIVERED("Entregado"),
    CANCELLED("Cancelado");

    private final String displayName;

    OrderStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
