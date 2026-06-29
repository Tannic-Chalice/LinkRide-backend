package com.linkride.backend.entity;

import com.linkride.backend.enums.FavoriteIcon;
import com.linkride.backend.enums.FavoriteType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a saved/favourite location for a user.
 *
 * <p>Business rules (enforced both in service layer and at DB level):</p>
 * <ul>
 *   <li>Each user may have at most one {@code HOME} and one {@code WORK} favourite
 *       (partial unique indexes in the migration).</li>
 *   <li>Total favourites per user are capped at {@code maxFavorites} (currently 6),
 *       enforced in {@link com.linkride.backend.service.FavoriteService}.</li>
 *   <li>{@code displayOrder} is 1-based and unique per user; managed via
 *       {@code PATCH /api/favorites/reorder}.</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "favorites")
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "favorite_id", updatable = false, nullable = false)
    private UUID favoriteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    @Column(name = "label", nullable = false, length = 60)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private FavoriteType type;

    @Column(name = "address", nullable = false, columnDefinition = "TEXT")
    private String address;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "icon", nullable = false, length = 20)
    private FavoriteIcon icon;

    @Column(name = "is_pinned", nullable = false)
    private Boolean isPinned = false;

    /**
     * Client-controlled display order (1-based, unique per user).
     * Auto-assigned to {@code (currentMax + 1)} on creation so new
     * favourites always append to the end of the list.
     * Updated in bulk via {@code PATCH /api/favorites/reorder}.
     */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
