package com.wallet.app.repository;

import com.wallet.app.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    boolean existsByWalletId(Long walletId);
}
