package com.linkride.backend.vehicle;

import com.linkride.backend.entity.User;
import com.linkride.backend.entity.Vehicle;
import com.linkride.backend.repository.UserRepository;
import com.linkride.backend.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;

    /**
     * Registers a new vehicle for the currently authenticated user.
     *
     * <p>Business rules enforced:</p>
     * <ul>
     *   <li>Owner is resolved from the security context — never from the request body.</li>
     *   <li>Duplicate number plates are rejected with {@code 409 Conflict}.</li>
     *   <li>The returned DTO contains a dynamically computed S3 asset URL.</li>
     * </ul>
     *
     * @param request the validated vehicle registration payload
     * @return a {@link VehicleResponseDTO} representing the persisted vehicle
     */
    @Transactional
    public VehicleResponseDTO registerVehicle(VehicleRequestDTO request) {

        // 1. Resolve authenticated owner — never trust the request body for identity
        String rawUserId = (String) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        UUID ownerId = UUID.fromString(rawUserId);

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Authenticated user not found in the system: " + ownerId));

        // 2. Enforce unique number plate constraint before hitting the DB
        String normalizedPlate = request.getNumberPlate().trim().toUpperCase();
        if (vehicleRepository.existsByNumberPlate(normalizedPlate)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A vehicle with number plate '" + normalizedPlate + "' is already registered.");
        }

        // 3. Build and persist the Vehicle entity
        Vehicle vehicle = new Vehicle();
        vehicle.setOwner(owner);
        vehicle.setNumberPlate(normalizedPlate);
        vehicle.setCarMake(request.getCarMake().trim());
        vehicle.setCarModel(request.getCarModel().trim());
        vehicle.setColour(request.getColour() != null ? request.getColour().trim() : null);
        vehicle.setNoOfSeats(request.getNoOfSeats());
        vehicle.setHasAc(request.getHasAc() != null ? request.getHasAc() : Boolean.TRUE);
        vehicle.setIsVerified(Boolean.FALSE); // Vehicles start unverified; admin verifies later

        Vehicle saved = vehicleRepository.save(vehicle);

        // 4. Map to response DTO (assetUrl is computed inside VehicleResponseDTO.from())
        return VehicleResponseDTO.from(saved);
    }
}
