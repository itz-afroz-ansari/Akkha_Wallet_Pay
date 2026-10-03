package com.wallet.app.controller;

import com.wallet.app.entity.LinkedBankAccount;
import com.wallet.app.entity.User;
import com.wallet.app.entity.Wallet;
import com.wallet.app.service.PaymentRequestService;
import com.wallet.app.repository.UserRepository;
import com.wallet.app.repository.WalletRepository;
import com.wallet.app.service.WalletService;
import com.wallet.app.service.ServicePaymentService;
import com.wallet.app.service.SavingsGoalService;
import com.wallet.app.service.FinanceInsightService;
import com.wallet.app.service.MerchantService;
import com.wallet.app.service.AuditLogService;
import com.wallet.app.entity.MerchantProfile;
import com.wallet.app.repository.MerchantProfileRepository;
import com.wallet.app.repository.PaymentRequestRepository;
import com.wallet.app.repository.TransactionRepository;
import com.wallet.app.entity.SavingsGoal;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/dashboard")
public class WalletController {
    
    @Autowired
    private com.wallet.app.repository.LinkedBankAccountRepository bankRepo;
    
    @Autowired
    private WalletService walletService;

    @Autowired
    private PaymentRequestService paymentRequestService;

    @Autowired
    private ServicePaymentService servicePaymentService;

    @Autowired
    private SavingsGoalService savingsGoalService;

    @Autowired
    private FinanceInsightService financeInsightService;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private MerchantProfileRepository merchantProfileRepo;

    @Autowired
    private PaymentRequestRepository paymentRequestRepo;

    @Autowired
    private TransactionRepository txRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private WalletRepository walletRepo; 

    // --- MAIN DASHBOARD ---
    @GetMapping
    public String showDashboard(HttpSession session, Model model) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/";

        User user = userRepo.findById(sessionUser.getId()).orElse(null);
        if (user == null) return "redirect:/logout";

        Wallet wallet = walletRepo.findByUserId(user.getId()); 
        model.addAttribute("wallet", wallet); 
        model.addAttribute("user", user);
        
        String txSuccess = (String) session.getAttribute("txSuccess");
        String txError = (String) session.getAttribute("txError");
        if (txSuccess != null) { model.addAttribute("txSuccess", txSuccess); session.removeAttribute("txSuccess"); }
        if (txError != null) { model.addAttribute("txError", txError); session.removeAttribute("txError"); }
        
        return "dashboard";
    }

    // --- NAVIGATION ROUTES ---
    @GetMapping("/profile")
    public String showProfilePage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";
        model.addAttribute("user", user);
        return "profile";
    }

    @GetMapping("/security")
    public String showSecurityPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";
        model.addAttribute("user", user);
        Object txSuccess = session.getAttribute("txSuccess");
        Object txError = session.getAttribute("txError");
        if (txSuccess != null) { model.addAttribute("txSuccess", txSuccess); session.removeAttribute("txSuccess"); }
        if (txError != null) { model.addAttribute("txError", txError); session.removeAttribute("txError"); }
        return "security";
    }

    @GetMapping("/settings")
    public String showSettingsPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";
        model.addAttribute("user", user);
        model.addAttribute("wallet", walletRepo.findByUserId(user.getId()));
        String txSuccess = (String) session.getAttribute("txSuccess");
        String txError = (String) session.getAttribute("txError");
        if (txSuccess != null) { model.addAttribute("txSuccess", txSuccess); session.removeAttribute("txSuccess"); }
        if (txError != null) { model.addAttribute("txError", txError); session.removeAttribute("txError"); }
        return "settings";
    }

    @GetMapping("/send-money")
    public String showSendMoneyPage(@RequestParam(required = false) String receiverPhone, HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";
        model.addAttribute("user", user);
        model.addAttribute("receiverPhone", receiverPhone == null ? "" : receiverPhone);
        model.addAttribute("operationKey", UUID.randomUUID().toString());
        return "send-money"; 
    }

    @GetMapping("/my-qr")
    public String showMyQr(HttpSession session, Model model) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/";
        User user = userRepo.findById(sessionUser.getId()).orElse(null);
        Wallet wallet = user == null ? null : walletRepo.findByUserId(user.getId());
        if (user == null || wallet == null) return "redirect:/logout";
        model.addAttribute("user", user);
        model.addAttribute("wallet", wallet);
        return "my-qr";
    }

    @GetMapping("/scan")
    public String showQrScanner(HttpSession session) {
        return session.getAttribute("user") == null ? "redirect:/" : "scan-pay";
    }

    @GetMapping("/pay")
    public String payByWalletQr(@RequestParam String wallet, HttpSession session) {
        if (session.getAttribute("user") == null) return "redirect:/";
        Wallet recipientWallet = walletRepo.findByWalletNumber(wallet);
        if (recipientWallet == null || recipientWallet.getUser() == null) {
            session.setAttribute("txError", "That PayWallet QR could not be found.");
            return "redirect:/dashboard/scan";
        }
        return "redirect:/dashboard/send-money?receiverPhone="
                + java.net.URLEncoder.encode(recipientWallet.getUser().getPhoneNumber(), java.nio.charset.StandardCharsets.UTF_8);
    }

    @GetMapping("/add-funds")
    public String showAddFundsPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";
        model.addAttribute("linkedBanks", bankRepo.findByUserId(user.getId()));
        model.addAttribute("user", user);
        model.addAttribute("operationKey", UUID.randomUUID().toString());
        String txSuccess = (String) session.getAttribute("txSuccess");
        String txError = (String) session.getAttribute("txError");
        if (txSuccess != null) { model.addAttribute("txSuccess", txSuccess); session.removeAttribute("txSuccess"); }
        if (txError != null) { model.addAttribute("txError", txError); session.removeAttribute("txError"); }
        return "add-funds";
    }

    @PostMapping("/add-funds/demo")
    public String addDemoFunds(@RequestParam BigDecimal amount, @RequestParam String paymentMethod,
                               @RequestParam String cardNumber, @RequestParam String expiry,
                               @RequestParam String cvv, @RequestParam String operationKey, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";
        var payment = servicePaymentService.addDemoFunds(user.getId(), amount, paymentMethod,
                cardNumber, expiry, cvv, operationKey);
        if (payment == null) {
            session.setAttribute("txError", "Enter a 12–19 digit card number, valid expiry, 3–4 digit CVV, valid amount, and use an active wallet.");
        } else if ("SUCCESS".equals(payment.getStatus())) {
            session.setAttribute("txSuccess", "₹" + payment.getAmount().toPlainString() + " simulated funds added to your wallet.");
        } else {
            session.setAttribute("txError", payment.getMessage());
        }
        return "redirect:/dashboard/add-funds";
    }

    @GetMapping("/link-bank")
    public String showLinkBankPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";
        model.addAttribute("user", user);
        return "link-bank";
    }

    @GetMapping("/banks")
    public String showBankAccountsPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";

        List<LinkedBankAccount> linkedBanks = bankRepo.findByUserId(user.getId());
        model.addAttribute("linkedBanks", linkedBanks);
        return "bank";
    }

    @GetMapping("/history")
    public String showHistoryPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";
        model.addAttribute("transactions", walletService.fetchTransactionHistory(user.getPhoneNumber()));
        model.addAttribute("user", user);
        return "history";
    }

    @GetMapping("/requests")
    public String showRequestsPage(HttpSession session, Model model) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/";
        User user = userRepo.findById(sessionUser.getId()).orElse(null);
        if (user == null) return "redirect:/logout";
        model.addAttribute("user", user);
        model.addAttribute("incomingRequests", paymentRequestService.inbox(user.getId()));
        model.addAttribute("sentRequests", paymentRequestService.sent(user.getId()));
        String txSuccess = (String) session.getAttribute("txSuccess");
        String txError = (String) session.getAttribute("txError");
        if (txSuccess != null) { model.addAttribute("txSuccess", txSuccess); session.removeAttribute("txSuccess"); }
        if (txError != null) { model.addAttribute("txError", txError); session.removeAttribute("txError"); }
        return "requests";
    }

    @GetMapping("/services")
    public String showServiceCenter(HttpSession session, Model model) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/";
        User user = userRepo.findById(sessionUser.getId()).orElse(null);
        if (user == null) return "redirect:/logout";
        model.addAttribute("user", user);
        model.addAttribute("servicePayments", servicePaymentService.history(user.getId()));
        model.addAttribute("operationKey", UUID.randomUUID().toString());
        String txSuccess = (String) session.getAttribute("txSuccess");
        String txError = (String) session.getAttribute("txError");
        if (txSuccess != null) { model.addAttribute("txSuccess", txSuccess); session.removeAttribute("txSuccess"); }
        if (txError != null) { model.addAttribute("txError", txError); session.removeAttribute("txError"); }
        return "services";
    }

    @PostMapping("/services/pay")
    public String payDemoService(@RequestParam String serviceType, @RequestParam String provider,
                                 @RequestParam String accountReference, @RequestParam BigDecimal amount,
                                 @RequestParam String operationKey, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";
        var payment = servicePaymentService.pay(user.getId(), serviceType, provider,
                accountReference, amount, operationKey);
        if (payment == null) {
            session.setAttribute("txError", "Payment could not be created. Check your wallet balance and the details.");
        } else if ("SUCCESS".equals(payment.getStatus())) {
            session.setAttribute("txSuccess", payment.getMessage() + ". Ref " + payment.getProviderReference());
        } else {
            session.setAttribute("txError", payment.getMessage());
        }
        return "redirect:/dashboard/services";
    }

    @GetMapping("/savings")
    public String showSavingsGoals(HttpSession session, Model model) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/";
        User user = userRepo.findById(sessionUser.getId()).orElse(null);
        if (user == null) return "redirect:/logout";
        List<SavingsGoal> goals = savingsGoalService.list(user.getId());
        Map<Long, String> contributeKeys = new HashMap<>();
        Map<Long, String> withdrawKeys = new HashMap<>();
        Map<Long, Integer> progress = new HashMap<>();
        goals.forEach(goal -> {
            contributeKeys.put(goal.getId(), UUID.randomUUID().toString());
            withdrawKeys.put(goal.getId(), UUID.randomUUID().toString());
            int percent = goal.getSavedAmount().multiply(BigDecimal.valueOf(100))
                    .divide(goal.getTargetAmount(), 0, RoundingMode.DOWN).intValue();
            progress.put(goal.getId(), Math.min(100, percent));
        });
        model.addAttribute("user", user);
        model.addAttribute("goals", goals);
        model.addAttribute("contributeKeys", contributeKeys);
        model.addAttribute("withdrawKeys", withdrawKeys);
        model.addAttribute("progress", progress);
        String txSuccess = (String) session.getAttribute("txSuccess");
        String txError = (String) session.getAttribute("txError");
        if (txSuccess != null) { model.addAttribute("txSuccess", txSuccess); session.removeAttribute("txSuccess"); }
        if (txError != null) { model.addAttribute("txError", txError); session.removeAttribute("txError"); }
        return "savings";
    }

    @PostMapping("/savings")
    public String createSavingsGoal(@RequestParam String name, @RequestParam BigDecimal targetAmount, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";
        boolean created = savingsGoalService.create(user.getId(), name, targetAmount);
        session.setAttribute(created ? "txSuccess" : "txError", created
                ? "Savings goal created." : "Enter a name and valid target amount for your goal.");
        return "redirect:/dashboard/savings";
    }

    @PostMapping("/savings/{id}/contribute")
    public String contributeToSavings(@PathVariable Long id, @RequestParam BigDecimal amount,
                                     @RequestParam String operationKey, @RequestParam String pin, HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/";
        User user = userRepo.findById(sessionUser.getId()).orElse(null);
        if (user == null || !authService.pinMatchesAndUpgrade(user, pin)) {
            session.setAttribute("txError", "Enter your correct wallet MPIN to move money into savings.");
        } else {
            boolean saved = savingsGoalService.contribute(user.getId(), id, amount, operationKey);
            session.setAttribute(saved ? "txSuccess" : "txError", saved
                    ? "Money added to your savings goal." : "Could not save that amount. Check your balance and goal.");
        }
        return "redirect:/dashboard/savings";
    }

    @PostMapping("/savings/{id}/withdraw")
    public String withdrawFromSavings(@PathVariable Long id, @RequestParam BigDecimal amount,
                                      @RequestParam String operationKey, @RequestParam String pin, HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/";
        User user = userRepo.findById(sessionUser.getId()).orElse(null);
        if (user == null || !authService.pinMatchesAndUpgrade(user, pin)) {
            session.setAttribute("txError", "Enter your correct wallet MPIN to withdraw savings.");
        } else {
            boolean withdrawn = savingsGoalService.withdraw(user.getId(), id, amount, operationKey);
            session.setAttribute(withdrawn ? "txSuccess" : "txError", withdrawn
                    ? "Money returned to your wallet." : "Could not withdraw that amount from the goal.");
        }
        return "redirect:/dashboard/savings";
    }

    @PostMapping("/savings/{id}/close")
    public String closeSavingsGoal(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";
        boolean closed = savingsGoalService.close(user.getId(), id);
        session.setAttribute(closed ? "txSuccess" : "txError", closed
                ? "Empty savings goal closed." : "Withdraw the goal balance before closing it.");
        return "redirect:/dashboard/savings";
    }

    @GetMapping("/finance")
    public String showFinancePage(HttpSession session, Model model) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/";
        User user = userRepo.findById(sessionUser.getId()).orElse(null);
        if (user == null) return "redirect:/logout";
        model.addAttribute("user", user);
        var summary = financeInsightService.summary(user);
        Map<String, Integer> categoryPercentages = new HashMap<>();
        summary.categories().forEach((category, value) -> {
            int percent = summary.spendingThisMonth().signum() == 0 ? 0
                    : value.multiply(BigDecimal.valueOf(100)).divide(summary.spendingThisMonth(), 0, RoundingMode.DOWN).intValue();
            categoryPercentages.put(category, Math.min(100, percent));
        });
        model.addAttribute("summary", summary);
        model.addAttribute("categoryPercentages", categoryPercentages);
        String answer = (String) session.getAttribute("assistantAnswer");
        if (answer != null) { model.addAttribute("assistantAnswer", answer); session.removeAttribute("assistantAnswer"); }
        return "finance";
    }

    @PostMapping("/assistant")
    public String askFinanceAssistant(@RequestParam String question, HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/";
        User user = userRepo.findById(sessionUser.getId()).orElse(null);
        if (user == null) return "redirect:/logout";
        session.setAttribute("assistantAnswer", financeInsightService.answer(user, question));
        return "redirect:/dashboard/finance";
    }

    @GetMapping("/kyc")
    public String showKycPage(HttpSession session, Model model) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/";
        User user = userRepo.findById(sessionUser.getId()).orElse(null);
        if (user == null) return "redirect:/logout";
        model.addAttribute("user", user);
        String txSuccess = (String) session.getAttribute("txSuccess");
        String txError = (String) session.getAttribute("txError");
        if (txSuccess != null) { model.addAttribute("txSuccess", txSuccess); session.removeAttribute("txSuccess"); }
        if (txError != null) { model.addAttribute("txError", txError); session.removeAttribute("txError"); }
        return "kyc";
    }

    @PostMapping("/kyc/submit")
    public String submitDemoKyc(HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/";
        User user = userRepo.findById(sessionUser.getId()).orElse(null);
        if (user == null) return "redirect:/logout";
        if (!"APPROVED".equals(user.getKycStatus())) {
            user.setKycStatus("UNDER_REVIEW");
            userRepo.save(user);
            auditLogService.record(user.getId(), "KYC_SUBMISSION", "USER", user.getId().toString(), "Demo verification submitted");
            session.setAttribute("txSuccess", "Demo verification submitted for review. No identity document was collected.");
        }
        return "redirect:/dashboard/kyc";
    }

    @GetMapping("/merchant")
    public String showMerchantDashboard(HttpSession session, Model model) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/";
        User user = userRepo.findById(sessionUser.getId()).orElse(null);
        if (user == null) return "redirect:/logout";
        MerchantProfile profile = merchantService.profile(user.getId()).orElse(null);
        List<com.wallet.app.entity.Transaction> sales = walletService.fetchTransactionHistory(user.getPhoneNumber()).stream()
                .filter(tx -> "SUCCESS".equals(tx.getStatus()) && "SEND_MONEY".equals(tx.getTransactionType())
                        && user.getPhoneNumber().equals(tx.getReceiverPhone())).toList();
        BigDecimal totalSales = sales.stream().map(com.wallet.app.entity.Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("user", user);
        model.addAttribute("merchant", profile);
        model.addAttribute("sales", sales);
        model.addAttribute("totalSales", totalSales);
        model.addAttribute("salesCount", sales.size());
        String txSuccess = (String) session.getAttribute("txSuccess");
        String txError = (String) session.getAttribute("txError");
        if (txSuccess != null) { model.addAttribute("txSuccess", txSuccess); session.removeAttribute("txSuccess"); }
        if (txError != null) { model.addAttribute("txError", txError); session.removeAttribute("txError"); }
        return "merchant";
    }

    @PostMapping("/merchant/register")
    public String registerMerchant(@RequestParam String businessName, @RequestParam String category, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";
        boolean registered = merchantService.register(user.getId(), businessName, category);
        if (registered) auditLogService.record(user.getId(), "MERCHANT_PROFILE_CREATED", "USER", user.getId().toString(), "Demo merchant profile submitted");
        session.setAttribute(registered ? "txSuccess" : "txError", registered
                ? "Merchant profile created. Demo KYC is pending review."
                : "Could not create the merchant profile. Check the details or profile status.");
        return "redirect:/dashboard/merchant";
    }

    @PostMapping("/wallet-status")
    public String updateWalletStatus(@RequestParam String action, @RequestParam String pin, HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/";
        User user = userRepo.findById(sessionUser.getId()).orElse(null);
        if (user == null || !authService.pinMatchesAndUpgrade(user, pin)) {
            session.setAttribute("txError", "Enter your correct MPIN to change wallet status.");
            return "redirect:/dashboard/settings";
        }
        boolean changed = walletService.setWalletStatus(user.getId(), action);
        if (!changed) {
            session.setAttribute("txError", "Wallet status could not be changed.");
        } else {
            session.setAttribute("txSuccess", "UNFREEZE".equals(action) ? "Wallet is active again." : "Wallet frozen. Outgoing payments are paused.");
        }
        return "redirect:/dashboard/settings";
    }

    @GetMapping("/admin")
    public String showAdminDashboard(HttpSession session, Model model) {
        User admin = authenticatedUser(session);
        if (admin == null) return "redirect:/";
        if (!"ADMIN".equals(admin.getRole())) return "redirect:/dashboard";
        model.addAttribute("totalUsers", userRepo.count());
        model.addAttribute("totalMerchants", userRepo.countByRole("MERCHANT"));
        model.addAttribute("totalTransactions", txCount());
        model.addAttribute("failedTransactions", txRepo.countByStatus("FAILED"));
        model.addAttribute("pendingRequests", paymentRequestRepo.countByStatus("PENDING"));
        model.addAttribute("pendingKyc", userRepo.findByKycStatusOrderByIdDesc("UNDER_REVIEW"));
        model.addAttribute("pendingMerchants", merchantProfileRepo.findByStatusOrderByCreatedAtDesc("PENDING"));
        model.addAttribute("auditLogs", auditLogService.recent());
        String txSuccess = (String) session.getAttribute("txSuccess");
        String txError = (String) session.getAttribute("txError");
        if (txSuccess != null) { model.addAttribute("txSuccess", txSuccess); session.removeAttribute("txSuccess"); }
        if (txError != null) { model.addAttribute("txError", txError); session.removeAttribute("txError"); }
        return "admin";
    }

    @PostMapping("/admin/kyc/{userId}")
    public String reviewKyc(@PathVariable Long userId, @RequestParam String action, HttpSession session) {
        User admin = authenticatedUser(session);
        if (admin == null) return "redirect:/";
        if (!"ADMIN".equals(admin.getRole())) return "redirect:/dashboard";
        User customer = userRepo.findById(userId).orElse(null);
        if (customer == null || !"UNDER_REVIEW".equals(customer.getKycStatus())
                || !("APPROVE".equals(action) || "REJECT".equals(action))) {
            session.setAttribute("txError", "That demo KYC item is no longer available.");
            return "redirect:/dashboard/admin";
        }
        customer.setKycStatus("APPROVE".equals(action) ? "APPROVED" : "REJECTED");
        userRepo.save(customer);
        auditLogService.record(admin.getId(), "KYC_REVIEW", "USER", customer.getId().toString(), customer.getKycStatus());
        session.setAttribute("txSuccess", "Demo KYC marked " + customer.getKycStatus().toLowerCase() + ".");
        return "redirect:/dashboard/admin";
    }

    @PostMapping("/admin/merchant/{merchantId}")
    public String reviewMerchant(@PathVariable Long merchantId, @RequestParam String action, HttpSession session) {
        User admin = authenticatedUser(session);
        if (admin == null) return "redirect:/";
        if (!"ADMIN".equals(admin.getRole())) return "redirect:/dashboard";
        MerchantProfile merchant = merchantProfileRepo.findById(merchantId).orElse(null);
        if (merchant == null || !"PENDING".equals(merchant.getStatus())
                || !("APPROVE".equals(action) || "REJECT".equals(action))) {
            session.setAttribute("txError", "That merchant profile is no longer pending.");
            return "redirect:/dashboard/admin";
        }
        merchant.setStatus("APPROVE".equals(action) ? "ACTIVE" : "REJECTED");
        merchantProfileRepo.save(merchant);
        User merchantUser = merchant.getUser();
        merchantUser.setKycStatus("APPROVE".equals(action) ? "APPROVED" : "REJECTED");
        userRepo.save(merchantUser);
        auditLogService.record(admin.getId(), "MERCHANT_REVIEW", "MERCHANT", merchant.getId().toString(), merchant.getStatus());
        session.setAttribute("txSuccess", "Merchant profile marked " + merchant.getStatus().toLowerCase() + ".");
        return "redirect:/dashboard/admin";
    }

    private User authenticatedUser(HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        return sessionUser == null ? null : userRepo.findById(sessionUser.getId()).orElse(null);
    }

    private long txCount() { return txRepo.count(); }

    @PostMapping("/requests")
    public String createPaymentRequest(@RequestParam String payerPhone, @RequestParam BigDecimal amount,
                                       @RequestParam(required = false) String note, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";
        boolean created = paymentRequestService.createRequest(user.getId(), payerPhone, amount, note);
        session.setAttribute(created ? "txSuccess" : "txError", created
                ? "Payment request sent. It will expire in 7 days."
                : "We couldn't create that request. Check the phone number and amount.");
        return "redirect:/dashboard/requests";
    }

    @PostMapping("/requests/split")
    public String splitBill(@RequestParam String participants, @RequestParam BigDecimal amount,
                            @RequestParam(required = false) String note, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";
        boolean created = paymentRequestService.splitRequest(user.getId(), participants, amount, note);
        session.setAttribute(created ? "txSuccess" : "txError", created
                ? "Split requests sent. Shares are rounded to the nearest paise and expire in 7 days."
                : "Could not split this bill. Use 2–10 unique registered phone numbers and a valid total.");
        return "redirect:/dashboard/requests";
    }

    @PostMapping("/requests/{id}/pay")
    public String payPaymentRequest(@PathVariable Long id, @RequestParam String pin, HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/";
        User payer = userRepo.findById(sessionUser.getId()).orElse(null);
        if (payer == null || !authService.pinMatchesAndUpgrade(payer, pin)) {
            session.setAttribute("txError", "Enter your correct 4-digit MPIN to pay this request.");
            return "redirect:/dashboard/requests";
        }
        boolean paid = paymentRequestService.pay(id, payer.getId());
        session.setAttribute(paid ? "txSuccess" : "txError", paid
                ? "Payment complete. The request is now settled."
                : "This request may have expired, or your wallet balance is too low.");
        return "redirect:/dashboard/requests";
    }

    @PostMapping("/requests/{id}/decline")
    public String declinePaymentRequest(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";
        boolean updated = paymentRequestService.decline(id, user.getId());
        session.setAttribute(updated ? "txSuccess" : "txError", updated
                ? "Payment request declined." : "This request can no longer be declined.");
        return "redirect:/dashboard/requests";
    }

    @PostMapping("/requests/{id}/cancel")
    public String cancelPaymentRequest(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";
        boolean updated = paymentRequestService.cancel(id, user.getId());
        session.setAttribute(updated ? "txSuccess" : "txError", updated
                ? "Payment request cancelled." : "This request can no longer be cancelled.");
        return "redirect:/dashboard/requests";
    }
    
    // --- POST ACTIONS ---
    @PostMapping("/transfer")
    public String transferMoney(
            @RequestParam String receiverPhone, 
            @RequestParam BigDecimal amount, 
            @RequestParam String pin,
            @RequestParam String operationKey,
            HttpSession session) {
            
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/";
        
        User user = userRepo.findById(sessionUser.getId()).orElse(null);

        // SECURITY CHECK: Does the entered PIN match the database?
        if (user == null || !authService.pinMatchesAndUpgrade(user, pin)) {
            session.setAttribute("txError", "Transaction Failed: Incorrect Security PIN.");
            return "redirect:/dashboard"; // or wherever your transfer page is
        }

        // If PIN is correct, process the transfer
        boolean success = walletService.sendMoney(user.getPhoneNumber(), receiverPhone, amount, operationKey);
        if (success) {
            session.setAttribute("txSuccess", "Transaction Complete!");
        } else {
            session.setAttribute("txError", "Transaction Failed: Insufficient balance or invalid user.");
        }
        return "redirect:/dashboard";
    }
    
    @PostMapping("/link-bank")
    public String linkBankAccount(@RequestParam String bankName, @RequestParam String accountNumber, @RequestParam String ifscCode, HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/";
        LinkedBankAccount bankAccount = new LinkedBankAccount();
        bankAccount.setBankName(bankName);
        bankAccount.setAccountNumber(accountNumber);
        bankAccount.setIfscCode(ifscCode.toUpperCase()); 
        bankAccount.setUser(sessionUser);
        bankRepo.save(bankAccount);
        session.setAttribute("txSuccess", "Bank linked successfully!");
        return "redirect:/dashboard";
    }

    @PostMapping("/change-pin")
    public String updateSecurityPin(@RequestParam String newPin, @RequestParam String confirmPin,
                                    @RequestParam String otp, HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/";
        
        User user = userRepo.findById(sessionUser.getId()).orElse(null);
        
        // Verify OTP matches what we saved in the database
        Object otpIssuedAt = session.getAttribute("pinOtpIssuedAt");
        boolean otpIsRecent = otpIssuedAt instanceof Long
                && System.currentTimeMillis() - (Long) otpIssuedAt <= 5 * 60 * 1000L;
        if (user != null && otpIsRecent && authService.otpMatchesAndUpgrade(user, otp)
                && newPin != null && newPin.matches("\\d{4}") && newPin.equals(confirmPin)) {
            user.setPin(authService.hashSecret(newPin));
            user.setCurrentOtp(null); // Clear the OTP so it can't be reused
            userRepo.save(user);
            session.removeAttribute("pinOtpIssuedAt");
            session.removeAttribute("pinOtpLastSentAt");
            session.setAttribute("txSuccess", "Security PIN updated successfully!");
        } else {
            session.setAttribute("txError", "The code may be invalid or expired. Request a new email code and try again.");
        }
        return "redirect:/dashboard/security";
    }

    @PostMapping("/delete-account")
    public String deleteUserAccount(@RequestParam String password, HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return "redirect:/";

        User user = userRepo.findById(sessionUser.getId()).orElse(null);
        if (user == null) {
            session.invalidate();
            return "redirect:/";
        }
        if (!authService.passwordMatchesAndUpgrade(user, password)) {
            session.setAttribute("txError", "Password did not match. Account remains active.");
            return "redirect:/dashboard/settings";
        }
        Wallet wallet = walletRepo.findByUserId(user.getId());
        BigDecimal goalsBalance = savingsGoalService.list(user.getId()).stream()
                .map(SavingsGoal::getSavedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean hasPendingRequests = paymentRequestRepo.findByPayerIdOrderByCreatedAtDesc(user.getId()).stream()
                .anyMatch(request -> "PENDING".equals(request.getStatus()))
                || paymentRequestRepo.findByRequesterIdOrderByCreatedAtDesc(user.getId()).stream()
                .anyMatch(request -> "PENDING".equals(request.getStatus()));
        if (wallet == null || wallet.getBalance().signum() != 0 || goalsBalance.signum() != 0 || hasPendingRequests) {
            session.setAttribute("txError", "Move your wallet and savings balances out and resolve pending requests before closing your account.");
            return "redirect:/dashboard/settings";
        }

        wallet.setStatus("CLOSED");
        walletRepo.save(wallet);
        bankRepo.deleteByUserId(user.getId());
        user.setAccountStatus("CLOSED");
        userRepo.save(user);
        auditLogService.record(user.getId(), "ACCOUNT_CLOSED", "USER", user.getId().toString(), "Account closed by customer");
        session.invalidate();
        return "redirect:/?deleted=true";
    }

// --- NOTIFICATION SERVICES ---
    
    @Autowired
    private com.wallet.app.service.AuthService authService; 

    @PostMapping("/send-pin-otp")
    @ResponseBody
    public java.util.Map<String, Object> sendPinOtp(HttpSession session) {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) {
            response.put("success", false);
            response.put("message", "Please sign in again to set your MPIN.");
            return response;
        }

        User user = userRepo.findById(sessionUser.getId()).orElse(null);
        if (user == null) {
            response.put("success", false);
            response.put("message", "Your account could not be found. Please sign in again.");
            return response;
        }

        Object lastSentAt = session.getAttribute("pinOtpLastSentAt");
        if (lastSentAt instanceof Long
                && System.currentTimeMillis() - (Long) lastSentAt < 60_000L) {
            response.put("success", false);
            response.put("message", "Please wait a moment before requesting another code.");
            return response;
        }

        String otp = authService.generateOTP();
        if (authService.sendOtpEmail(user.getEmail(), otp)) {
            authService.storeOtp(user, otp);
            userRepo.save(user);
            long now = System.currentTimeMillis();
            session.setAttribute("pinOtpIssuedAt", now);
            session.setAttribute("pinOtpLastSentAt", now);
            response.put("success", true);
            response.put("message", "A 6-digit code was sent to " + user.getEmail() + ". It expires in 5 minutes.");
            return response;
        }

        response.put("success", false);
        response.put("message", "We couldn't send the email. Check your mail settings and try again.");
        return response;
    }
}
