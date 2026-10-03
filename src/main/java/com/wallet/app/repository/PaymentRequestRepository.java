package com.wallet.app.repository;

import com.wallet.app.entity.PaymentRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Long> {
    List<PaymentRequest> findByPayerIdOrderByCreatedAtDesc(Long payerId);
    List<PaymentRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);
    long countByStatus(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PaymentRequest r where r.id = :id")
    Optional<PaymentRequest> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("update PaymentRequest r set r.status = 'EXPIRED' where r.payer.id = :userId and r.status = 'PENDING' and r.expiresAt <= :now")
    int expireForPayer(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("update PaymentRequest r set r.status = 'EXPIRED' where r.requester.id = :userId and r.status = 'PENDING' and r.expiresAt <= :now")
    int expireForRequester(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
