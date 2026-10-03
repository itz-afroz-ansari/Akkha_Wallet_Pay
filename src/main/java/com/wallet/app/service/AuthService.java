package com.wallet.app.service;

import com.wallet.app.entity.User;
import com.wallet.app.entity.Wallet;
import com.wallet.app.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender; // Explicitly fixed import path
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

@Service
public class AuthService {

    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int HASH_BITS = 256;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    public boolean isEmailDeliveryConfigured() {
        return mailFrom != null && !mailFrom.isBlank()
                && mailPassword != null && !mailPassword.isBlank();
    }

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private JavaMailSender mailSender;

    public User registerUser(User user) {
        user.setId(null);
        user.setPassword(hashSecret(user.getPassword()));
        // Never accept verification state or a PIN from registration form binding.
        user.setVerified(false);
        user.setCurrentOtp(null);
        user.setPin(null);
        user.setRole("CUSTOMER");
        user.setKycStatus("NOT_STARTED");
        user.setRewardPoints(0);
        user.setAccountStatus("ACTIVE");

        // Initialize the new wallet instance safely
        Wallet newWallet = new Wallet();
        newWallet.setBalance(BigDecimal.ZERO);
        newWallet.setUser(user);
        
        // Form the bi-directional association
        user.setWallet(newWallet);
        
        return userRepo.save(user);
    }

    public String generateOTP() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    public void storeOtp(User user, String otp) {
        user.setCurrentOtp(hashSecret(otp));
    }

    public boolean otpMatchesAndUpgrade(User user, String candidate) {
        if (user == null || candidate == null || user.getCurrentOtp() == null) return false;
        String stored = user.getCurrentOtp();
        if (stored.startsWith("pbkdf2$")) return verifySecret(candidate, stored);
        if (!stored.equals(candidate)) return false;
        storeOtp(user, candidate);
        userRepo.save(user);
        return true;
    }

    public boolean passwordMatchesAndUpgrade(User user, String candidate) {
        if (user == null || candidate == null || user.getPassword() == null) return false;
        String stored = user.getPassword();
        if (stored.startsWith("pbkdf2$")) return verifySecret(candidate, stored);
        if (!stored.equals(candidate)) return false;
        user.setPassword(hashSecret(candidate));
        userRepo.save(user);
        return true;
    }

    public boolean pinMatchesAndUpgrade(User user, String candidate) {
        if (user == null || candidate == null || user.getPin() == null) return false;
        String stored = user.getPin();
        if (stored.startsWith("pbkdf2$")) return verifySecret(candidate, stored);
        if (!stored.equals(candidate)) return false;
        user.setPin(hashSecret(candidate));
        userRepo.save(user);
        return true;
    }

    public String hashSecret(String secret) {
        if (secret == null || secret.isBlank()) throw new IllegalArgumentException("Secret must not be empty");
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        byte[] hash = derive(secret, salt, PBKDF2_ITERATIONS);
        return "pbkdf2$" + PBKDF2_ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);
    }

    private boolean verifySecret(String secret, String encoded) {
        try {
            String[] parts = encoded.split("\\$", -1);
            if (parts.length != 4 || !"pbkdf2".equals(parts[0])) return false;
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 1 || iterations > 1_000_000) return false;
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(secret, salt, iterations);
            return java.security.MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private byte[] derive(String secret, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash credential", ex);
        } finally {
            spec.clearPassword();
        }
    }

    public boolean sendOtpEmail(String toEmail, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (mailFrom != null && !mailFrom.isBlank()) message.setFrom(mailFrom);
            message.setTo(toEmail);
            message.setSubject("AkhhaPay security code");
            message.setText("Your AkhhaPay security code is " + otp + ". It expires in 5 minutes. Do not share it with anyone.");
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            System.err.println("OTP email delivery failed: " + e.getMessage());
            return false;
        }
    }
}
