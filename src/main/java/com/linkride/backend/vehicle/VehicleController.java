package com.linkride.backend.vehicle;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    /**
     * Registers a new vehicle for the currently authenticated user.
     *
     * <p>Security: endpoint is protected by Spring Security. The principal (user UUID)
     * is extracted inside {@link VehicleService} — never from the request body.</p>
     *
     * @param request validated vehicle registration payload
     * @return {@code 201 Created} with the persisted {@link VehicleResponseDTO}
     */
    @PostMapping
    public ResponseEntity<VehicleResponseDTO> registerVehicle(
            @Valid @RequestBody VehicleRequestDTO request) {

        VehicleResponseDTO response = vehicleService.registerVehicle(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
