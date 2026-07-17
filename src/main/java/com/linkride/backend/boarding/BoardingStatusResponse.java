package com.linkride.backend.boarding;

import com.linkride.backend.ride.RideStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BoardingStatusResponse {

    private RideStatus rideStatus;
    private int checkedIn;
    private int totalPassengers;
    private boolean canStartRide;
    private List<PassengerBoardingDto> passengers;
}
