package com.linkride.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkride.backend.dto.AuthResponse;
import com.linkride.backend.dto.LoginRequest;
import com.linkride.backend.dto.SignupRequest;
import com.linkride.backend.entity.User;
import com.linkride.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Covers the Phase 5 §10 migration (see backend/docs/phase-5-platform-hardening.md): login's
 * client-fault failures (bad credentials, missing local profile) must throw
 * {@link IllegalArgumentException} -- not a plain {@link RuntimeException} -- so
 * {@code GlobalExceptionHandler} maps them to {@code 400 VALIDATION_ERROR} instead of falling
 * through to the generic {@code 500} handler.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestTemplate restTemplate;

    private UserService userService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
        ReflectionTestUtils.setField(userService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(userService, "supabaseUrl", "https://example.supabase.co");
        ReflectionTestUtils.setField(userService, "supabasePublishableKey", "test-key");
    }

    private SignupRequest signupRequest(String password, String confirmPassword) {
        SignupRequest r = new SignupRequest();
        r.setName("Test User");
        r.setPhone("1234567890");
        r.setEmail("test@college.edu");
        r.setPassword(password);
        r.setConfirmPassword(confirmPassword);
        return r;
    }

    private LoginRequest loginRequest() {
        LoginRequest r = new LoginRequest();
        r.setEmail("test@college.edu");
        r.setPassword("secret");
        return r;
    }

    @Test
    void registerUser_passwordMismatch_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> userService.registerUser(signupRequest("a", "b")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Passwords do not match");
    }

    @Test
    void loginUser_supabaseRejectsCredentials_throwsIllegalArgumentExceptionNotPlainRuntimeException() {
        when(restTemplate.postForEntity(anyString(), any(), eq(JsonNode.class)))
                .thenThrow(new RuntimeException("401 from Supabase"));

        assertThatThrownBy(() -> userService.loginUser(loginRequest()))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void loginUser_unexpectedSupabaseResponse_throwsIllegalArgumentException() throws Exception {
        JsonNode body = objectMapper.readTree("{}"); // no access_token
        when(restTemplate.postForEntity(anyString(), any(), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(body));

        assertThatThrownBy(() -> userService.loginUser(loginRequest()))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unexpected response from Supabase login");
    }

    @Test
    void loginUser_noLocalProfile_translatesToIllegalArgumentException() throws Exception {
        UUID supabaseId = UUID.randomUUID();
        JsonNode body = objectMapper.readTree(
                "{\"access_token\":\"tok\",\"refresh_token\":\"ref\",\"expires_in\":3600,"
                        + "\"user\":{\"id\":\"" + supabaseId + "\"}}");
        when(restTemplate.postForEntity(anyString(), any(), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(body));
        when(userRepository.findById(supabaseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loginUser(loginRequest()))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loginUser_success_returnsAuthResponse() throws Exception {
        UUID supabaseId = UUID.randomUUID();
        JsonNode body = objectMapper.readTree(
                "{\"access_token\":\"tok\",\"refresh_token\":\"ref\",\"expires_in\":3600,"
                        + "\"user\":{\"id\":\"" + supabaseId + "\"}}");
        when(restTemplate.postForEntity(anyString(), any(), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(body));
        User user = new User();
        user.setId(supabaseId);
        when(userRepository.findById(supabaseId)).thenReturn(Optional.of(user));

        AuthResponse response = userService.loginUser(loginRequest());

        assertThat(response.getAccessToken()).isEqualTo("tok");
        assertThat(response.getUser().getId()).isEqualTo(supabaseId);
    }
}
