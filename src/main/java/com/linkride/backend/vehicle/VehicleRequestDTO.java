package com.linkride.backend.vehicle;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VehicleRequestDTO {

    @NotBlank(message = "Number plate must not be blank")
    private String numberPlate;

    @NotBlank(message = "Car make must not be blank")
    private String carMake;

    @NotBlank(message = "Car model must not be blank")
    private String carModel;

    private String colour;

    @NotNull(message = "Number of seats must not be null")
    @Min(value = 1, message = "A vehicle must have at least 1 seat")
    private Integer noOfSeats;

    private Boolean hasAc = true;
}
