package com.linkride.backend.service;

import com.linkride.backend.entity.User;
import com.linkride.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.linkride.backend.dto.AuthResponse;
import com.linkride.backend.dto.LoginRequest;
import com.linkride.backend.dto.SignupRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RestTemplate restTemplate;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.anon.key:${supabase.publishable.key:}}")
    private String supabasePublishableKey;

    // Spring Boot automatically injects the repository here
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Fetches a user by their Supabase UUID.
     */
    public User getUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found in the system."));
    }

    /**
     * Business Logic: Verifies a user's college email.
     */
    public User verifyUser(UUID userId) {
        User user = getUserById(userId);
        user.setIsVerified(true);
        // The repository translates this save() into an SQL UPDATE statement
        return userRepository.save(user); 
    }
    public User createTestUser(User user) {
        // The repository automatically translates this into an SQL INSERT statement
        return userRepository.save(user);
    }

    /**
     * Business Logic: Registers a user with Supabase and saves their profile locally.
     */
    public AuthResponse registerUser(SignupRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        
        // 1. Register with Supabase Auth API
        String url = supabaseUrl + "/auth/v1/signup";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", supabasePublishableKey);
        
        Map<String, Object> body = new HashMap<>();
        body.put("email", request.getEmail());
        body.put("password", request.getPassword());
        // Optionally put data in user_metadata
        Map<String, Object> data = new HashMap<>();
        data.put("full_name", request.getName());
        data.put("phone", request.getPhone());
        body.put("data", data);
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        
        ResponseEntity<JsonNode> response;
        try {
            response = restTemplate.postForEntity(url, entity, JsonNode.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to register with Supabase: " + e.getMessage());
        }
        
        JsonNode responseBody = response.getBody();
        
        // Extract tokens if Supabase returned a session (e.g. if email confirmation is off)
        String accessToken = null;
        String refreshToken = null;
        Integer expiresIn = null;
        
        if (responseBody != null && responseBody.has("access_token")) {
            accessToken = responseBody.get("access_token").asText();
            refreshToken = responseBody.get("refresh_token").asText();
            expiresIn = responseBody.get("expires_in").asInt();
        }

        if (responseBody != null && responseBody.has("user")) {
            // When email confirmations are enabled, the response might be nested or we have a session
            responseBody = responseBody.get("user");
        }
        
        if (responseBody == null || !responseBody.has("id")) {
             throw new RuntimeException("Unexpected response from Supabase signup");
        }
        
        String supabaseUserId = responseBody.get("id").asText();
        
        // 2. Save user profile to local PostgreSQL database
        User user = new User();
        user.setId(UUID.fromString(supabaseUserId));
        user.setFullName(request.getName());
        user.setCollegeEmail(request.getEmail());
        user.setPhoneNumber(request.getPhone());
        user.setIsVerified(false);
        
        User savedUser = userRepository.save(user);
        return new AuthResponse(savedUser, accessToken, refreshToken, expiresIn);
    }

    /**
     * Business Logic: Logs in a user via Supabase and returns tokens + profile.
     */
    public AuthResponse loginUser(LoginRequest request) {
        String url = supabaseUrl + "/auth/v1/token?grant_type=password";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", supabasePublishableKey);
        
        Map<String, Object> body = new HashMap<>();
        body.put("email", request.getEmail());
        body.put("password", request.getPassword());
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        
        ResponseEntity<JsonNode> response;
        try {
            response = restTemplate.postForEntity(url, entity, JsonNode.class);
        } catch (Exception e) {
            throw new RuntimeException("Invalid email or password");
        }
        
        JsonNode responseBody = response.getBody();
        if (responseBody == null || !responseBody.has("access_token")) {
             throw new RuntimeException("Unexpected response from Supabase login");
        }
        
        String accessToken = responseBody.get("access_token").asText();
        String refreshToken = responseBody.get("refresh_token").asText();
        Integer expiresIn = responseBody.get("expires_in").asInt();
        
        JsonNode userNode = responseBody.get("user");
        String supabaseUserId = userNode.get("id").asText();
        
        User user = getUserById(UUID.fromString(supabaseUserId));
        
        return new AuthResponse(user, accessToken, refreshToken, expiresIn);
    }
}