package com.wallet.app.controller;

import com.wallet.app.entity.User;
import com.wallet.app.repository.UserRepository;
import com.wallet.app.service.AuthService;
import com.wallet.app.service.SmsService; // 👈 Make sure to import this!
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private AuthService authService;

    // 👈 FIX 1: Autowired SmsService must be up here at the class level
    @Autowired
    private SmsService smsService; 

    @GetMapping("/")
    public String showLogin() { 
        return "login"; 
    }

    @GetMapping("/register")
    public String showRegister() { 
        return "register"; 
    }

    @GetMapping("/forgot-password")
    public String showForgotPassword(HttpSession session, Model model) {
        model.addAttribute("mailConfigured", authService.isEmailDeliveryConfigured());
        String email = (String) session.getAttribute("passwordResetEmail");
        Long issuedAt = (Long) session.getAttribute("passwordResetIssuedAt");
        boolean codeSent = email != null && issuedAt != null
                && System.currentTimeMillis() - issuedAt <= 5 * 60 * 1000L;
        model.addAttribute("codeSent", codeSent);
        if (codeSent) model.addAttribute("email", email);
        return "forgot-password";
    }

    @PostMapping("/forgot-password/send-code")
    public String sendPasswordResetCode(@RequestParam String email, HttpSession session, Model model) {
        model.addAttribute("mailConfigured", authService.isEmailDeliveryConfigured());
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        if (!normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || normalizedEmail.length() > 254) {
            model.addAttribute("error", "Enter a valid email address.");
            return "forgot-password";
        }

        Object lastSentAt = session.getAttribute("passwordResetLastSentAt");
        if (lastSentAt instanceof Long && System.currentTimeMillis() - (Long) lastSentAt < 60_000L) {
            String activeEmail = (String) session.getAttribute("passwordResetEmail");
            if (activeEmail != null) {
                model.addAttribute("codeSent", true);
                model.addAttribute("email", activeEmail);
                model.addAttribute("error", "Please wait a minute before requesting another code.");
            } else {
                model.addAttribute("message", "If an active account matches that email, a recovery code will be sent.");
            }
            return "forgot-password";
        }

        User user = userRepo.findByEmail(normalizedEmail).orElse(null);
        if (user != null && "ACTIVE".equals(user.getAccountStatus())) {
            String otp = authService.generateOTP();
            if (authService.sendOtpEmail(normalizedEmail, otp)) {
                authService.storeOtp(user, otp);
                userRepo.save(user);
                long now = System.currentTimeMillis();
                session.setAttribute("passwordResetEmail", normalizedEmail);
                session.setAttribute("passwordResetIssuedAt", now);
                session.setAttribute("passwordResetLastSentAt", now);
                session.setAttribute("passwordResetAttempts", 0);
            }
        }

        // Keep the response the same whether an account exists to prevent email discovery.
        model.addAttribute("message", "If an active account matches that email, a recovery code will be sent.");
        // Render the same next step for known and unknown emails to avoid account discovery.
        model.addAttribute("codeSent", true);
        model.addAttribute("email", normalizedEmail);
        return "forgot-password";
    }

    @PostMapping("/forgot-password/reset")
    public String resetPassword(@RequestParam String email, @RequestParam String otp,
                                @RequestParam String newPassword, @RequestParam String confirmPassword,
                                HttpSession session, Model model) {
        model.addAttribute("mailConfigured", authService.isEmailDeliveryConfigured());
        String resetEmail = (String) session.getAttribute("passwordResetEmail");
        Long issuedAt = (Long) session.getAttribute("passwordResetIssuedAt");
        Integer attempts = (Integer) session.getAttribute("passwordResetAttempts");
        if (attempts == null) attempts = 0;
        boolean validWindow = issuedAt != null && System.currentTimeMillis() - issuedAt <= 5 * 60 * 1000L;
        User user = resetEmail == null || !resetEmail.equals(email.trim().toLowerCase())
                ? null : userRepo.findByEmail(resetEmail).orElse(null);

        if (!validWindow || user == null || attempts >= 5) {
            clearPasswordReset(session, user);
            model.addAttribute("error", "The recovery code expired or is no longer valid. Request a new code.");
            model.addAttribute("codeSent", true);
            model.addAttribute("email", email);
            return "forgot-password";
        }
        if (!authService.otpMatchesAndUpgrade(user, otp)) {
            attempts++;
            session.setAttribute("passwordResetAttempts", attempts);
            if (attempts >= 5) {
                clearPasswordReset(session, user);
                model.addAttribute("error", "Too many incorrect codes. Request a new recovery code.");
                return "forgot-password";
            }
            model.addAttribute("codeSent", true);
            model.addAttribute("email", resetEmail);
            model.addAttribute("error", "That code is incorrect. Check it and try again.");
            return "forgot-password";
        }

        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 128) {
            model.addAttribute("codeSent", true);
            model.addAttribute("email", resetEmail);
            model.addAttribute("error", "Choose a password between 8 and 128 characters.");
            return "forgot-password";
        }
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("codeSent", true);
            model.addAttribute("email", resetEmail);
            model.addAttribute("error", "The passwords do not match.");
            return "forgot-password";
        }

        user.setPassword(authService.hashSecret(newPassword));
        user.setCurrentOtp(null);
        userRepo.save(user);
        clearPasswordReset(session, null);
        return "redirect:/?passwordReset";
    }

    @PostMapping("/register")
    public String handleRegistration(@ModelAttribute User user, HttpSession session, Model model) {
        String email = user.getEmail() == null ? "" : user.getEmail().trim().toLowerCase();
        String phone = user.getPhoneNumber() == null ? "" : user.getPhoneNumber().replaceAll("[\\s()-]", "");
        String name = user.getName() == null ? "" : user.getName().trim();
        String password = user.getPassword();
        if (name.isBlank() || name.length() > 100 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || email.length() > 254 || !phone.matches("\\+?[0-9]{7,15}")
                || password == null || password.length() < 8 || password.length() > 128) {
            model.addAttribute("error", "Enter a valid name, email, phone number, and password of at least 8 characters.");
            return "register";
        }
        if (userRepo.findByEmail(email).isPresent()) {
            model.addAttribute("error", "That email is already registered. Sign in or use a different email.");
            return "register";
        }
        if (userRepo.findByPhoneNumber(phone).isPresent()) {
            model.addAttribute("error", "That phone number is already registered.");
            return "register";
        }

        String otp = authService.generateOTP();
        if (!authService.sendOtpEmail(email, otp)) {
            model.addAttribute("error", "We couldn't send a verification code. Check your email settings and try again.");
            return "register";
        }

        user.setName(name);
        user.setEmail(email);
        user.setPhoneNumber(phone);
        User savedUser = authService.registerUser(user);
        authService.storeOtp(savedUser, otp);
        userRepo.save(savedUser);
        beginOtpFlow(session, email, "REGISTER");
        return "redirect:/verify-otp";
    }

    @PostMapping("/login")
    public String handleLogin(@RequestParam String email, @RequestParam String password, HttpSession session, Model model) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        User user = userRepo.findByEmail(normalizedEmail).orElse(null);
        
        if (user != null && "ACTIVE".equals(user.getAccountStatus())
                && authService.passwordMatchesAndUpgrade(user, password)) {
            String otp = authService.generateOTP();
            authService.storeOtp(user, otp);
            userRepo.save(user);
            
            if (!authService.sendOtpEmail(user.getEmail(), otp)) {
                user.setCurrentOtp(null);
                userRepo.save(user);
                model.addAttribute("error", "We couldn't send your sign-in code. Please try again later.");
                return "login";
            }
            smsService.sendSmsOtp(user.getPhoneNumber(), otp);
            beginOtpFlow(session, normalizedEmail, user.isVerified() ? "LOGIN" : "REGISTER");
            return "redirect:/verify-otp";
        }
        
        // If execution reaches here, it means login failed
        model.addAttribute("error", "Invalid Account Authentication Details.");
        return "login";
    }

    @GetMapping("/verify-otp")
    public String showVerifyOtp() { 
        return "verify-otp"; 
    }

    @PostMapping("/verify-otp")
    public String verifyOtp(@RequestParam String otp, HttpSession session, HttpServletRequest request, Model model) {
        String email = (String) session.getAttribute("tempEmail");
        String purpose = (String) session.getAttribute("otpPurpose");
        User user = userRepo.findByEmail(email).orElse(null);
        Object issuedAt = session.getAttribute("otpIssuedAt");
        boolean notExpired = issuedAt instanceof Long && System.currentTimeMillis() - (Long) issuedAt <= 5 * 60 * 1000L;
        Integer attempts = (Integer) session.getAttribute("otpAttempts");
        if (attempts == null) attempts = 0;

        if (user != null && notExpired && attempts < 5 && authService.otpMatchesAndUpgrade(user, otp)) {
            user.setCurrentOtp(null);
            if ("REGISTER".equals(purpose)) user.setVerified(true);
            userRepo.save(user);
            request.changeSessionId();
            session.setAttribute("user", user);
            clearOtpFlow(session);
            return "redirect:/dashboard";
        }

        attempts++;
        session.setAttribute("otpAttempts", attempts);
        if (attempts >= 5 && user != null) {
            user.setCurrentOtp(null);
            userRepo.save(user);
            model.addAttribute("error", "Too many incorrect codes. Request a new code to continue.");
        } else if (!notExpired) {
            model.addAttribute("error", "That code has expired. Request a new one to continue.");
        } else {
            model.addAttribute("error", "That code is incorrect. Check it and try again.");
        }
        return "verify-otp";
    }

    @PostMapping("/resend-otp")
    public String resendOtp(HttpSession session, Model model) {
        String email = (String) session.getAttribute("tempEmail");
        String purpose = (String) session.getAttribute("otpPurpose");
        if (email == null || purpose == null) return "redirect:/";
        Object lastSentAt = session.getAttribute("otpLastSentAt");
        if (lastSentAt instanceof Long && System.currentTimeMillis() - (Long) lastSentAt < 60_000L) {
            model.addAttribute("error", "Please wait a moment before requesting another code.");
            return "verify-otp";
        }
        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null || ("REGISTER".equals(purpose) && user.isVerified())) return "redirect:/";
        String otp = authService.generateOTP();
        if (!authService.sendOtpEmail(email, otp)) {
            model.addAttribute("error", "We couldn't send a new code. Please try again later.");
            return "verify-otp";
        }
        authService.storeOtp(user, otp);
        userRepo.save(user);
        beginOtpFlow(session, email, purpose);
        return "redirect:/verify-otp?resent=true";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    private void beginOtpFlow(HttpSession session, String email, String purpose) {
        long now = System.currentTimeMillis();
        session.setAttribute("tempEmail", email);
        session.setAttribute("otpPurpose", purpose);
        session.setAttribute("otpIssuedAt", now);
        session.setAttribute("otpLastSentAt", now);
        session.setAttribute("otpAttempts", 0);
    }

    private void clearOtpFlow(HttpSession session) {
        session.removeAttribute("tempEmail");
        session.removeAttribute("otpPurpose");
        session.removeAttribute("otpIssuedAt");
        session.removeAttribute("otpLastSentAt");
        session.removeAttribute("otpAttempts");
    }

    private void clearPasswordReset(HttpSession session, User user) {
        if (user != null) {
            user.setCurrentOtp(null);
            userRepo.save(user);
        }
        session.removeAttribute("passwordResetEmail");
        session.removeAttribute("passwordResetIssuedAt");
        session.removeAttribute("passwordResetLastSentAt");
        session.removeAttribute("passwordResetAttempts");
    }
}
