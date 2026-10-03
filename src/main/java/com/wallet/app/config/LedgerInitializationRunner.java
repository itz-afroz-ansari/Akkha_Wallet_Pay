package com.wallet.app.config;

import com.wallet.app.service.LedgerInitializationService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class LedgerInitializationRunner implements ApplicationRunner {
    private final LedgerInitializationService initializationService;

    public LedgerInitializationRunner(LedgerInitializationService initializationService) {
        this.initializationService = initializationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        initializationService.carryForwardLegacyBalances();
    }
}
