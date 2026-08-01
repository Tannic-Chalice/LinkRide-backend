package com.linkride.backend.service;

import com.linkride.backend.dto.home.HomeResponse;
import com.linkride.backend.entity.User;
import com.linkride.backend.exception.ResourceNotFoundException;
import com.linkride.backend.repository.FavoriteRepository;
import com.linkride.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FavoriteRepository favoriteRepository;

    @InjectMocks
    private HomeService homeService;

    private final UUID userId = UUID.randomUUID();

    @Test
    void buildHomeResponse_userNotFound_throwsResourceNotFoundWithUserNotFoundCode() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> homeService.buildHomeResponse(userId, 12.9, 77.5, "Home"))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(e -> assertThat(((ResourceNotFoundException) e).getCode()).isEqualTo("USER_NOT_FOUND"));
    }

    @Test
    void buildHomeResponse_userExists_assemblesResponse() {
        User user = new User();
        user.setId(userId);
        user.setFullName("Joseph Mathew");
        user.setRating(new BigDecimal("4.9"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        lenient().when(favoriteRepository.findByUser_IdOrderByDisplayOrderAsc(userId)).thenReturn(List.of());

        HomeResponse response = homeService.buildHomeResponse(userId, 12.97, 77.59, "Hebbal");

        assertThat(response.getUser().getFullName()).isEqualTo("Joseph Mathew");
        assertThat(response.getCurrentLocation().getName()).isEqualTo("Hebbal");
        assertThat(response.getFavorites()).isEmpty();
    }
}
