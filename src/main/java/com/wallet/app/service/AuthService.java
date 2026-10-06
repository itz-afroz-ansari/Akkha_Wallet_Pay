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
        if (!isEmailDeliveryConfigured()) {
            System.out.println("\n=======================================================");
            System.out.println(" 📧 [AKHHA PAY EMAIL OTP SERVICE - LOCAL / DEV MODE]");
            System.out.println(" 👤 Recipient  : " + toEmail);
            System.out.println(" 🔑 OTP Code   : " + otp);
            System.out.println(" ⏳ Expires in : 5 minutes");
            System.out.println("=======================================================\n");
            return true;
        }

        try {
            jakarta.mail.internet.MimeMessage mimeMessage = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper = 
                    new org.springframework.mail.javamail.MimeMessageHelper(mimeMessage, true, "UTF-8");
            
            if (mailFrom != null && !mailFrom.isBlank()) {
                helper.setFrom(mailFrom, "Akhha Pay Security");
            }
            helper.setTo(toEmail);
            helper.setSubject("Your Akhha Pay Verification Code: " + otp);

            String htmlBody = "<div style=\"font-family: 'Segoe UI', Helvetica, Arial, sans-serif; max-width: 520px; margin: 0 auto; padding: 24px; border: 1px solid #e2e8f0; border-radius: 16px; background-color: #ffffff;\">"
                    + "<div style=\"text-align: center; margin-bottom: 24px;\">"
                    + "<h2 style=\"color: #0f7a4c; margin: 0; font-size: 24px; font-weight: 800; letter-spacing: -0.5px;\">AKHHA PAY</h2>"
                    + "<p style=\"color: #64748b; font-size: 13px; margin-top: 4px;\">Secure Digital Banking &amp; Wallet</p>"
                    + "</div>"
                    + "<div style=\"background-color: #f8faf9; border-radius: 12px; padding: 24px; text-align: center; margin-bottom: 20px;\">"
                    + "<p style=\"color: #334155; font-size: 15px; margin-top: 0; margin-bottom: 12px; font-weight: 500;\">Use this one-time verification code to proceed:</p>"
                    + "<div style=\"display: inline-block; background-color: #0f7a4c; color: #ffffff; font-size: 32px; font-weight: 800; letter-spacing: 6px; padding: 12px 28px; border-radius: 10px; margin: 8px 0;\">" + otp + "</div>"
                    + "<p style=\"color: #64748b; font-size: 13px; margin-bottom: 0; margin-top: 12px;\">This code is valid for <strong>5 minutes</strong>. Do not share it with anyone.</p>"
                    + "</div>"
                    + "<p style=\"color: #94a3b8; font-size: 12px; text-align: center; margin-bottom: 0;\">If you did not request this code, please ignore this email or contact support.</p>"
                    + "</div>";

            helper.setText(htmlBody, true);
            mailSender.send(mimeMessage);
            return true;
        } catch (Exception e) {
            System.err.println("OTP email delivery failed via SMTP: " + e.getMessage());
            System.out.println("\n=======================================================");
            System.out.println(" ⚠️ [FALLBACK EMAIL OTP CONSOLE OUTPUT]");
            System.out.println(" 👤 Recipient  : " + toEmail);
            System.out.println(" 🔑 OTP Code   : " + otp);
            System.out.println("=======================================================\n");
            return true;
        }
    }
}
