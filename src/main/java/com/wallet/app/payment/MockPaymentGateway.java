package com.wallet.app.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** Demo adapter. It never contacts or transfers funds to a real provider. */
@Component
public class MockPaymentGateway implements PaymentGateway {
    @Value("${paywallet.demo.provider-outcome:SUCCESS}")
    private String configuredOutcome;

    @Override
    public ProviderResult process(String serviceType, String accountReference, BigDecimal amount) {
        String outcome = "FAILED".equalsIgnoreCase(configuredOutcome) ? "FAILED" : "SUCCESS";
        String reference = "DEMO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String message = "SUCCESS".equals(outcome)
                ? "Simulated " + serviceType.toLowerCase() + " payment accepted"
                : "Simulated provider rejection; no wallet funds were captured";
        return new ProviderResult(outcome, reference, message);
    }
}
