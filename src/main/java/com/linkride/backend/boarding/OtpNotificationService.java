package com.linkride.backend.boarding;

import com.linkride.backend.entity.User;

/**
 * Delivers a boarding OTP to the passenger it belongs to (Phase 4.4). No real email/SMS provider
 * is wired into this codebase yet — {@link LoggingOtpNotificationService} is a development-only
 * stand-in. Swap in a real implementation the same way {@code GoogleRouteProvider}/
 * {@code NoOpRouteProvider} split a real provider from a local-dev one.
 */
public interface OtpNotificationService {

    void sendOtp(User passenger, String otp);
}
