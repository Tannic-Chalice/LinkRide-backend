package com.linkride.backend.vehicle;

import com.linkride.backend.entity.Vehicle;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class VehicleResponseDTO {

    private UUID carId;
    private UUID ownerId;
    private String ownerName;
    private String numberPlate;
    private String carMake;
    private String carModel;
    private String colour;
    private Integer noOfSeats;
    private Boolean hasAc;
    private Boolean isVerified;
    private OffsetDateTime createdAt;

    /**
     * Computed field: dynamically generated S3 asset URL.
     * Format: https://car-imagery-linkride.s3.eu-north-1.amazonaws.com/{carMake}/{carMake}+{carModel}.png
     * Spaces are replaced with '+' to form a clean URL path.
     */
    private String assetUrl;

    // ─── Static factory ───────────────────────────────────────────────────────

    private static final String S3_BASE_URL =
            "https://car-imagery-linkride.s3.eu-north-1.amazonaws.com";

    public static VehicleResponseDTO from(Vehicle vehicle) {
        String make  = vehicle.getCarMake().trim().replace(' ', '+');
        String model = vehicle.getCarModel().trim().replace(' ', '+');
        String assetUrl = S3_BASE_URL + "/" + make + "/" + make + "+" + model + ".png";

        return VehicleResponseDTO.builder()
                .carId(vehicle.getCarId())
                .ownerId(vehicle.getOwner().getId())
                .ownerName(vehicle.getOwner().getFullName())
                .numberPlate(vehicle.getNumberPlate())
                .carMake(vehicle.getCarMake())
                .carModel(vehicle.getCarModel())
                .colour(vehicle.getColour())
                .noOfSeats(vehicle.getNoOfSeats())
                .hasAc(vehicle.getHasAc())
                .isVerified(vehicle.getIsVerified())
                .createdAt(vehicle.getCreatedAt())
                .assetUrl(assetUrl)
                .build();
    }
}
