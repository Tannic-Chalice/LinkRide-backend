package com.linkride.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Live-tracking realtime transport (§11 of backend/docs/phase-7-live-trip-management.md):
 * Spring STOMP over WebSocket, used <em>only</em> for its unicast
 * {@code SimpUserRegistry}/{@code convertAndSendToUser} capability (ADR-1) — no topic
 * destinations, no client-driven subscriptions beyond one fixed personal queue, no subscribe-time
 * ACLs. In-process {@code SimpleBroker}, V1 (§16) — the migration to a broker relay for
 * horizontal scale-out is a config-only change here, precisely because STOMP was kept instead of
 * a hand-rolled session registry.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class TrackingWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // No SockJS fallback -- §2 non-goals: no browser client to support, only native mobile.
        registry.addEndpoint("/ws/tracking")
                .addInterceptors(new StompHandshakeInterceptor(jwtService))
                .setHandshakeHandler(new PrincipalHandshakeHandler());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Clients subscribe to /user/queue/ride-progress; convertAndSendToUser strips the
        // /user/{sessionId} prefix before handing off to the broker, so the broker itself only
        // ever sees /queue/... destinations (§11.2 step 5, §11.3).
        registry.enableSimpleBroker("/queue");
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * Reads the user UUID {@link StompHandshakeInterceptor} resolved during
     * {@code beforeHandshake} and turns it into the STOMP session's {@link Principal} — the
     * propagation step §11.2 calls "Spring's handshake-to-STOMP-session principal propagation."
     */
    private static class PrincipalHandshakeHandler extends DefaultHandshakeHandler {
        @Override
        protected Principal determineUser(
                ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
            Object userId = attributes.get(StompHandshakeInterceptor.PRINCIPAL_ATTRIBUTE);
            if (userId == null) {
                return null;
            }
            String userIdString = userId.toString();
            return () -> userIdString;
        }
    }
}
