package com.wallet.app.service;

import java.math.BigDecimal;
import java.util.Map;

public record FinanceSummary(BigDecimal incomeThisMonth, BigDecimal spendingThisMonth,
                             BigDecimal previousMonthSpending, BigDecimal savingsBalance,
                             long rewardPoints, Map<String, BigDecimal> categories) {
}
