package com.wallet.app.payment;

public record ProviderResult(String status, String reference, String message) {
}
