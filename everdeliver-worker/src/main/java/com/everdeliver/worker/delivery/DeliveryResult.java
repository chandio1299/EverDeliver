package com.everdeliver.worker.delivery;

public record DeliveryResult(String providerMessageId) {

    public static DeliveryResult none() {
        return new DeliveryResult(null);
    }
}
