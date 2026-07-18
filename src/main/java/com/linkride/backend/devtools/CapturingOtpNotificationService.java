package com.linkride.backend.devtools;

import com.linkride.backend.boarding.OtpNotificationService;
import com.linkride.backend.entity.User;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Devtools-only {@link OtpNotificationService}: {@code BoardingService.generateOtp} never returns
 * the plaintext OTP (by design -- it's meant to be delivered out-of-band), so {@link
 * DemoScenarioService}/{@link DemoSeedService} can't drive the real {@code verifyOtp} path without
 * some way to read back what was "sent". This captures it in memory instead, the same way {@link
 * com.linkride.backend.boarding.LoggingOtpNotificationService} already stands in for a real
 * provider in plain local dev.
 *
 * <p>{@code @Primary} so it wins over {@code LoggingOtpNotificationService} when both beans are on
 * the classpath -- only registered at all when devtools is enabled, so production/plain-dev
 * behavior (logging only) is unaffected.</p>
 */
@Service
@Primary
@ConditionalOnProperty(prefix = "linkride.devtools", name = "enabled", havingValue = "true")
public class CapturingOtpNotificationService implements OtpNotificationService {

    private final Map<UUID, String> lastOtpByPassengerId = new ConcurrentHashMap<>();
    private final OtpNotificationService delegate;

    public CapturingOtpNotificationService(
            @Qualifier("loggingOtpNotificationService") OtpNotificationService delegate) {
        this.delegate = delegate;
    }

    @Override
    public void sendOtp(User passenger, String otp) {
        lastOtpByPassengerId.put(passenger.getId(), otp);
        delegate.sendOtp(passenger, otp);
    }

    /** The most recent OTP "sent" to this passenger, for the scenario/seeder to submit back via {@code verifyOtp}. */
    public String getLastOtp(UUID passengerId) {
        String otp = lastOtpByPassengerId.get(passengerId);
        if (otp == null) {
            throw new IllegalStateException("No OTP was captured for passenger " + passengerId);
        }
        return otp;
    }
}
