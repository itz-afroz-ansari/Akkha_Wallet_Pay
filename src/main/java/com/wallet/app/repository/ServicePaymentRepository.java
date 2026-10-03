package com.wallet.app.repository;

import com.wallet.app.entity.ServicePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ServicePaymentRepository extends JpaRepository<ServicePayment, Long> {
    List<ServicePayment> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<ServicePayment> findByIdempotencyKey(String idempotencyKey);
    long countByStatus(String status);
}
