package com.lemondrop.ai.model;

public enum ConversationState {
    IDLE("En espera"),
    DISCOVERING("Explorando catálogo"),
    BUILDING_ORDER("Armando pedido"),
    REVIEWING_ORDER("Revisando pedido"),
    COLLECTING_CUSTOMER("Recolectando datos del cliente"),
    WAITING_CONFIRMATION("Esperando confirmación"),
    ORDER_CONFIRMED("Pedido confirmado"),
    ORDER_COMPLETED("Pedido completado"),
    ERROR("Error en proceso");

    private final String description;

    ConversationState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
