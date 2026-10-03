package com.wallet.app.repository;

import com.wallet.app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhoneNumber(String phoneNumber);
    long countByRole(String role);
    long countByKycStatus(String kycStatus);
    List<User> findByKycStatusOrderByIdDesc(String kycStatus);
}
