package com.wallet.app.service;

import org.springframework.stereotype.Service;

/** SMS is deliberately mocked in the local build; authentication codes are delivered by email. */
@Service
public class SmsService {
    public void sendSmsOtp(String targetPhoneNumber, String otpCode) {
        // Do not log OTPs or contact a real SMS provider in demo mode.
    }
}
