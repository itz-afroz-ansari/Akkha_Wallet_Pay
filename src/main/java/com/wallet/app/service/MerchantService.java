package com.wallet.app.service;

import com.wallet.app.entity.MerchantProfile;
import com.wallet.app.entity.User;
import com.wallet.app.repository.MerchantProfileRepository;
import com.wallet.app.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class MerchantService {
    private final MerchantProfileRepository merchantRepo;
    private final UserRepository userRepo;

    public MerchantService(MerchantProfileRepository merchantRepo, UserRepository userRepo) {
        this.merchantRepo = merchantRepo; this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    public Optional<MerchantProfile> profile(Long userId) { return merchantRepo.findByUserId(userId); }

    @Transactional
    public boolean register(Long userId, String businessName, String category) {
        String name = businessName == null ? "" : businessName.trim();
        String type = category == null ? "" : category.trim();
        if (name.isBlank() || name.length() > 100 || type.isBlank() || type.length() > 40
                || merchantRepo.findByUserId(userId).isPresent()) return false;
        User user = userRepo.findById(userId).orElse(null);
        if (user == null || !"CUSTOMER".equals(user.getRole())) return false;
        user.setRole("MERCHANT");
        user.setKycStatus("PENDING");
        userRepo.save(user);
        merchantRepo.save(new MerchantProfile(user, name, type, LocalDateTime.now()));
        return true;
    }
}
