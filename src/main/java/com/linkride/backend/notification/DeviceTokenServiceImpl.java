package com.linkride.backend.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceTokenServiceImpl implements DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    @Override
    @Transactional
    public DeviceTokenResponse registerToken(UUID userId, String fcmToken, DevicePlatform platform) {
        DeviceToken token = deviceTokenRepository.findByFcmToken(fcmToken)
                .orElseGet(DeviceToken::new);

        token.setUserId(userId);
        token.setFcmToken(fcmToken);
        token.setPlatform(platform);
        token.setActive(true);
        token.setLastSeenAt(OffsetDateTime.now());

        return DeviceTokenResponse.from(deviceTokenRepository.save(token));
    }

    @Override
    @Transactional
    public void deactivateToken(UUID userId, String fcmToken) {
        DeviceToken token = deviceTokenRepository.findByFcmToken(fcmToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device token not found"));

        if (!token.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This device token does not belong to you");
        }

        token.setActive(false);
        deviceTokenRepository.save(token);
    }

    @Override
    @Transactional
    public void deactivateByToken(String fcmToken) {
        deviceTokenRepository.findByFcmToken(fcmToken).ifPresent(token -> {
            token.setActive(false);
            deviceTokenRepository.save(token);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceToken> listActiveTokensForUser(UUID userId) {
        return deviceTokenRepository.findByUserIdAndActiveTrue(userId);
    }
}
