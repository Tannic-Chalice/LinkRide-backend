package com.linkride.backend.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestUserRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void validRequest_hasNoViolations() {
        TestUserRequest request = new TestUserRequest();
        request.setId(UUID.randomUUID());
        request.setFullName("Jane Doe");
        request.setCollegeEmail("jane@college.edu");
        request.setPhoneNumber("+1234567890");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void missingId_isRejected() {
        TestUserRequest request = new TestUserRequest();
        request.setFullName("Jane Doe");
        request.setCollegeEmail("jane@college.edu");
        request.setPhoneNumber("+1234567890");

        Set<ConstraintViolation<TestUserRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("id"));
    }

    @Test
    void malformedEmail_isRejected() {
        TestUserRequest request = new TestUserRequest();
        request.setId(UUID.randomUUID());
        request.setFullName("Jane Doe");
        request.setCollegeEmail("not-an-email");
        request.setPhoneNumber("+1234567890");

        Set<ConstraintViolation<TestUserRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("collegeEmail"));
    }

    @Test
    void blankFullNameAndPhoneNumber_areRejected() {
        TestUserRequest request = new TestUserRequest();
        request.setId(UUID.randomUUID());
        request.setFullName(" ");
        request.setCollegeEmail("jane@college.edu");
        request.setPhoneNumber(" ");

        Set<ConstraintViolation<TestUserRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("fullName", "phoneNumber");
    }
}
