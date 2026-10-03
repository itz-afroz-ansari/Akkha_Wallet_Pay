package com.wallet.app.service;

import com.wallet.app.entity.Transaction;
import com.wallet.app.entity.User;
import com.wallet.app.repository.SavingsGoalRepository;
import com.wallet.app.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class FinanceInsightService {
    private final WalletService walletService;
    private final WalletRepository walletRepo;
    private final SavingsGoalRepository savingsRepo;

    public FinanceInsightService(WalletService walletService, WalletRepository walletRepo,
                                 SavingsGoalRepository savingsRepo) {
        this.walletService = walletService; this.walletRepo = walletRepo; this.savingsRepo = savingsRepo;
    }

    @Transactional(readOnly = true)
    public FinanceSummary summary(User user) {
        LocalDate today = LocalDate.now();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime nextMonth = monthStart.plusMonths(1);
        LocalDateTime previousMonth = monthStart.minusMonths(1);
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal spending = BigDecimal.ZERO;
        BigDecimal previousSpending = BigDecimal.ZERO;
        Map<String, BigDecimal> categories = new LinkedHashMap<>();

        for (Transaction tx : walletService.fetchTransactionHistory(user.getPhoneNumber())) {
            if (tx.getTimestamp() == null || !"SUCCESS".equals(tx.getStatus()) && !"BANK_DEPOSIT".equals(tx.getStatus())) continue;
            if ("OPENING_BALANCE".equals(tx.getTransactionType()) || "OPENING_BALANCE".equals(tx.getStatus())) continue;
            String type = tx.getTransactionType() == null ? "SEND_MONEY" : tx.getTransactionType();
            boolean outgoing = user.getPhoneNumber().equals(tx.getSenderPhone());
            boolean incoming = user.getPhoneNumber().equals(tx.getReceiverPhone());
            if (tx.getTimestamp().isBefore(monthStart) && !tx.getTimestamp().isBefore(previousMonth)) {
                if (outgoing && !isSavingsMove(type)) previousSpending = previousSpending.add(tx.getAmount());
            }
            if (tx.getTimestamp().isBefore(monthStart) || !tx.getTimestamp().isBefore(nextMonth)) continue;
            if (incoming) income = income.add(tx.getAmount());
            if (outgoing && !isSavingsMove(type)) {
                spending = spending.add(tx.getAmount());
                String category = category(type);
                categories.merge(category, tx.getAmount(), BigDecimal::add);
            }
        }

        BigDecimal savings = savingsRepo.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(goal -> goal.getSavedAmount() == null ? BigDecimal.ZERO : goal.getSavedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FinanceSummary(income, spending, previousSpending, savings, user.getRewardPoints(), categories);
    }

    @Transactional(readOnly = true)
    public String answer(User user, String question) {
        if (question == null || question.isBlank()) return "Ask about your balance, spending, savings, or recent activity.";
        String query = question.toLowerCase(Locale.ROOT);
        FinanceSummary summary = summary(user);
        if (query.contains("balance") || query.contains("available")) {
            var wallet = walletRepo.findByUserId(user.getId());
            return wallet == null ? "I couldn't find a wallet for your account."
                    : "Your available wallet balance is ₹" + wallet.getAvailableBalance().toPlainString() + ".";
        }
        if (query.contains("save") || query.contains("goal")) {
            return "You have ₹" + summary.savingsBalance().toPlainString() + " set aside across your savings goals.";
        }
        if (query.contains("last month") || query.contains("previous month")) {
            return "Your recorded wallet spending last month was ₹" + summary.previousMonthSpending().toPlainString() + ".";
        }
        if (query.contains("spend") || query.contains("food") || query.contains("bill") || query.contains("recharge")) {
            return "Your recorded wallet spending this month is ₹" + summary.spendingThisMonth().toPlainString()
                    + ". Your service and transfer categories are shown in Finance. Some older activity may not have a category.";
        }
        if (query.contains("income") || query.contains("received")) {
            return "Your recorded wallet credits this month total ₹" + summary.incomeThisMonth().toPlainString() + ".";
        }
        List<Transaction> recent = walletService.fetchTransactionHistory(user.getPhoneNumber()).stream().limit(3).toList();
        if (query.contains("recent") || query.contains("transaction")) {
            if (recent.isEmpty()) return "There are no transactions in your wallet yet.";
            return "Your latest activity: " + recent.stream().map(tx -> {
                String label = tx.getDescription() != null ? tx.getDescription()
                        : tx.getTransactionType() != null ? tx.getTransactionType().replace('_', ' ').toLowerCase(Locale.ROOT) : "Wallet activity";
                return label + " ₹" + tx.getAmount().toPlainString();
            }).reduce((a, b) -> a + "; " + b).orElse("") + ".";
        }
        return "I can summarize your current balance, this month’s spending or income, savings goals, and recent wallet activity. I can’t initiate a payment.";
    }

    private boolean isSavingsMove(String type) { return "SAVINGS".equals(type) || "SAVINGS_WITHDRAWAL".equals(type); }

    private String category(String type) {
        if (type.contains("RECHARGE")) return "Recharge";
        if (type.contains("ELECTRICITY") || type.contains("WATER") || type.contains("GAS") || type.contains("BROADBAND")) return "Bills";
        if ("FASTAG".equals(type)) return "Travel";
        if ("SEND_MONEY".equals(type)) return "Transfers";
        return "Other";
    }
}
