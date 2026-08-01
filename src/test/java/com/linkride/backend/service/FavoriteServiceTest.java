package com.linkride.backend.service;

import com.linkride.backend.dto.favorite.FavoriteReorderItem;
import com.linkride.backend.dto.favorite.FavoriteRequest;
import com.linkride.backend.entity.Favorite;
import com.linkride.backend.entity.User;
import com.linkride.backend.enums.FavoriteIcon;
import com.linkride.backend.enums.FavoriteType;
import com.linkride.backend.exception.BusinessRuleViolationException;
import com.linkride.backend.exception.ResourceNotFoundException;
import com.linkride.backend.repository.FavoriteRepository;
import com.linkride.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FavoriteService favoriteService;

    private final UUID userId = UUID.randomUUID();

    private FavoriteRequest validRequest(FavoriteType type) {
        FavoriteRequest request = new FavoriteRequest();
        request.setLabel("Gym");
        request.setType(type);
        request.setAddress("123 Fitness Ave");
        request.setLatitude(12.97);
        request.setLongitude(77.59);
        request.setIcon(FavoriteIcon.GYM);
        return request;
    }

    @Test
    void addFavorite_userNotFound_throwsResourceNotFoundWithUserNotFoundCode() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.addFavorite(userId, validRequest(FavoriteType.CUSTOM)))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(e -> assertThat(((ResourceNotFoundException) e).getCode()).isEqualTo("USER_NOT_FOUND"));
    }

    @Test
    void addFavorite_capReached_throwsBusinessRuleViolationWithBusinessRuleCode() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        when(favoriteRepository.countByUser_Id(userId)).thenReturn(6L);

        assertThatThrownBy(() -> favoriteService.addFavorite(userId, validRequest(FavoriteType.CUSTOM)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(e -> assertThat(((BusinessRuleViolationException) e).getCode()).isEqualTo("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void addFavorite_duplicateHomeType_throwsBusinessRuleViolation() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        when(favoriteRepository.countByUser_Id(userId)).thenReturn(1L);
        when(favoriteRepository.existsByUser_IdAndType(userId, FavoriteType.HOME)).thenReturn(true);

        assertThatThrownBy(() -> favoriteService.addFavorite(userId, validRequest(FavoriteType.HOME)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void updateFavorite_notFound_throwsResourceNotFoundWithFavoriteNotFoundCode() {
        UUID favoriteId = UUID.randomUUID();
        when(favoriteRepository.findByFavoriteIdAndUser_Id(favoriteId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.updateFavorite(userId, favoriteId, validRequest(FavoriteType.CUSTOM)))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(e -> assertThat(((ResourceNotFoundException) e).getCode()).isEqualTo("FAVORITE_NOT_FOUND"));
    }

    @Test
    void deleteFavorite_notFound_throwsResourceNotFound() {
        UUID favoriteId = UUID.randomUUID();
        when(favoriteRepository.findByFavoriteIdAndUser_Id(favoriteId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.deleteFavorite(userId, favoriteId))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(e -> assertThat(((ResourceNotFoundException) e).getCode()).isEqualTo("FAVORITE_NOT_FOUND"));
    }

    @Test
    void reorderFavorites_sizeMismatch_throwsBusinessRuleViolationWithInvalidReorderCode() {
        when(favoriteRepository.findByUser_IdOrderByDisplayOrderAsc(userId)).thenReturn(List.of(new Favorite()));

        assertThatThrownBy(() -> favoriteService.reorderFavorites(userId, List.of()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(e -> assertThat(((BusinessRuleViolationException) e).getCode()).isEqualTo("INVALID_REORDER"));
    }

    @Test
    void reorderFavorites_nonContiguousOrders_throwsInvalidReorder() {
        Favorite existing = new Favorite();
        existing.setFavoriteId(UUID.randomUUID());
        when(favoriteRepository.findByUser_IdOrderByDisplayOrderAsc(userId)).thenReturn(List.of(existing));

        FavoriteReorderItem item = new FavoriteReorderItem();
        item.setFavoriteId(existing.getFavoriteId());
        item.setDisplayOrder(5); // not 1

        assertThatThrownBy(() -> favoriteService.reorderFavorites(userId, List.of(item)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(e -> assertThat(((BusinessRuleViolationException) e).getCode()).isEqualTo("INVALID_REORDER"));
    }

    @Test
    void reorderFavorites_unownedFavoriteId_throwsResourceNotFound() {
        Favorite existing = new Favorite();
        existing.setFavoriteId(UUID.randomUUID());
        when(favoriteRepository.findByUser_IdOrderByDisplayOrderAsc(userId)).thenReturn(List.of(existing));

        FavoriteReorderItem item = new FavoriteReorderItem();
        item.setFavoriteId(UUID.randomUUID()); // different ID -- not owned
        item.setDisplayOrder(1);

        assertThatThrownBy(() -> favoriteService.reorderFavorites(userId, List.of(item)))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(e -> assertThat(((ResourceNotFoundException) e).getCode()).isEqualTo("FAVORITE_NOT_FOUND"));
    }

    @Test
    void reorderFavorites_validInput_succeeds() {
        Favorite existing = new Favorite();
        existing.setFavoriteId(UUID.randomUUID());
        existing.setDisplayOrder(1);
        when(favoriteRepository.findByUser_IdOrderByDisplayOrderAsc(userId)).thenReturn(List.of(existing));
        lenient().when(favoriteRepository.saveAll(any())).thenReturn(List.of(existing));

        FavoriteReorderItem item = new FavoriteReorderItem();
        item.setFavoriteId(existing.getFavoriteId());
        item.setDisplayOrder(1);

        List<?> result = favoriteService.reorderFavorites(userId, List.of(item));

        assertThat(result).hasSize(1);
    }
}
