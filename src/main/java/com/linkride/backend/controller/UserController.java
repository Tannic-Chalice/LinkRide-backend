package com.linkride.backend.controller;

import com.linkride.backend.dto.TestUserRequest;
import com.linkride.backend.entity.User;
import com.linkride.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only endpoint for creating a test user directly (bypassing real Supabase signup), used by
 * manual API testing. Gated behind {@code linkride.devtools.enabled} like every other dev-only
 * controller (see {@code com.linkride.backend.devtools}) -- previously this was the one endpoint
 * in the app that stayed publicly reachable regardless of environment (Phase 5 §4 — see
 * backend/docs/phase-5-platform-hardening.md).
 */
@RestController
@RequestMapping("/api/v1/users")
@ConditionalOnProperty(prefix = "linkride.devtools", name = "enabled", havingValue = "true")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/test")
    public ResponseEntity<User> createTestUser(@Valid @RequestBody TestUserRequest request) {
        User user = new User();
        user.setId(request.getId());
        user.setFullName(request.getFullName());
        user.setCollegeEmail(request.getCollegeEmail());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setGender(request.getGender());

        User savedUser = userService.createTestUser(user);
        return ResponseEntity.ok(savedUser);
    }
}
