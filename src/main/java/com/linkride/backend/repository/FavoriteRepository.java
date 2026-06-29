package com.linkride.backend.repository;

import com.linkride.backend.entity.Favorite;
import com.linkride.backend.enums.FavoriteType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {

    /**
     * Primary fetch for the Home Screen — returns all favourites for a user
     * sorted by {@code displayOrder} ascending (lowest number = first in list).
     */
    List<Favorite> findByUser_IdOrderByDisplayOrderAsc(UUID userId);

    /** Counts a user's total favourites to enforce the {@code maxFavorites} cap. */
    long countByUser_Id(UUID userId);

    /**
     * Checks whether a user already has a favourite of the given type.
     * Used to enforce the one-HOME / one-WORK business rule before the DB
     * partial-unique-index constraint fires.
     */
    boolean existsByUser_IdAndType(UUID userId, FavoriteType type);

    /**
     * Ownership check — returns the favourite only if it belongs to the given user.
     * Used before update and delete operations to prevent cross-user access.
     */
    Optional<Favorite> findByFavoriteIdAndUser_Id(UUID favoriteId, UUID userId);

    /**
     * Returns the highest {@code displayOrder} value currently used by a user,
     * or {@code 0} when the user has no favourites yet.
     * Used on creation to auto-assign the next position (max + 1).
     */
    @Query("SELECT COALESCE(MAX(f.displayOrder), 0) FROM Favorite f WHERE f.user.id = :userId")
    int findMaxDisplayOrderByUserId(@Param("userId") UUID userId);
}
