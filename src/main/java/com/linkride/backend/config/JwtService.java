package com.linkride.backend.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.security.PublicKey;

/**
 * Utility component that validates and parses JWTs issued by Supabase.
 *
 * <p>Supabase edge functions and Auth use asymmetric ECDSA (ES256) signatures.</p>
 */
@Slf4j
@Component
public class JwtService {

    private PublicKey signingKey;

    public JwtService(@Value("${supabase.url}") String supabaseUrl) {
        try {
            String jwksUrl = supabaseUrl + "/auth/v1/.well-known/jwks.json";
            RestTemplate restTemplate = new RestTemplate();
            String jwksJson = restTemplate.getForObject(jwksUrl, String.class);
            JwkSet jwkSet = Jwks.setParser().build().parse(jwksJson);
            
            // Get the first key from the JWKS (the ES256 key used for signing)
            this.signingKey = (PublicKey) jwkSet.getKeys().iterator().next().toKey();
        } catch (Exception e) {
            log.error("Failed to fetch or parse Supabase JWKS from {}: {}", supabaseUrl, e.getMessage());
        }
    }

    /**
     * Validates a Bearer token and extracts all claims.
     *
     * @param token the raw JWT string (without "Bearer " prefix)
     * @return the parsed {@link Claims} if the token is valid and not expired
     * @throws JwtException if the token is malformed, expired, or signature fails
     */
    public Claims validateAndExtractClaims(String token) {
        if (signingKey == null) {
            throw new IllegalStateException("JWT signing key was not properly initialized from JWKS");
        }
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Convenience method: extracts the Supabase user UUID (stored as the JWT subject "sub").
     *
     * @param token the raw JWT string
     * @return the user UUID string
     */
    public String extractUserId(String token) {
        return validateAndExtractClaims(token).getSubject();
    }
}
