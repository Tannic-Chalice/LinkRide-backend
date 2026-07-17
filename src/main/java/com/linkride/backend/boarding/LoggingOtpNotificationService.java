package com.linkride.backend.boarding;

import com.linkride.backend.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Development-only {@link OtpNotificationService}: logs the OTP instead of emailing/texting it.
 * There's no real notification provider in this codebase yet — this exists so the rest of the
 * boarding flow can be built and tested end-to-end without one, not as a production delivery
 * mechanism. Replace with a real email/SMS-backed implementation before this reaches anything but
 * local dev/test.
 */
@Slf4j
@Service
public class LoggingOtpNotificationService implements OtpNotificationService {

    @Override
    public void sendOtp(User passenger, String otp) {
        log.info("[DEV ONLY, no real delivery] Boarding OTP for passenger {} ({}): {}",
                passenger.getId(), passenger.getCollegeEmail(), otp);
    }
}
