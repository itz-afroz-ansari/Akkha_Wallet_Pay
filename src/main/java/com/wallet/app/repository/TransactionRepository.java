package com.wallet.app.repository;

import com.wallet.app.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findBySenderPhoneOrReceiverPhoneOrderByTimestampDesc(String senderPhone, String receiverPhone);
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
    boolean existsByReferenceId(String referenceId);
    long countByStatus(String status);
}
