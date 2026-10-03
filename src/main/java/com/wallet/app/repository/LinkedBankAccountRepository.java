package com.wallet.app.repository;

import com.wallet.app.entity.LinkedBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LinkedBankAccountRepository extends JpaRepository<LinkedBankAccount, Long> {
    
    // This allows us to fetch all bank accounts connected to a specific user
    List<LinkedBankAccount> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
