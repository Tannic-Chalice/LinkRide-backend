package com.linkride.backend.controller;

import com.linkride.backend.entity.User;
import com.linkride.backend.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // This exposes a POST endpoint at http://localhost:8080/api/users/test
    @PostMapping("/test")
    public ResponseEntity<User> createTestUser(@RequestBody User user) {
        User savedUser = userService.createTestUser(user);
        return ResponseEntity.ok(savedUser);
    }
}