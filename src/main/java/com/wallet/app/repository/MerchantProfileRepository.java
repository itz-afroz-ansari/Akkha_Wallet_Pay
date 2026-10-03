package com.wallet.app.repository;

import com.wallet.app.entity.MerchantProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MerchantProfileRepository extends JpaRepository<MerchantProfile, Long> {
    Optional<MerchantProfile> findByUserId(Long userId);
    List<MerchantProfile> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
}
