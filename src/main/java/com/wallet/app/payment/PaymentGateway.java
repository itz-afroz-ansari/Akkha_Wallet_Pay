package com.wallet.app.payment;

import java.math.BigDecimal;

public interface PaymentGateway {
    ProviderResult process(String serviceType, String accountReference, BigDecimal amount);
}
