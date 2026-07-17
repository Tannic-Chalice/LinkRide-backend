package com.linkride.backend.boarding;

import com.linkride.backend.booking.Booking;
import com.linkride.backend.booking.BookingStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PassengerBoardingDto {

    private UUID bookingId;
    private String name;
    private BookingStatus status;

    public static PassengerBoardingDto from(Booking booking) {
        return PassengerBoardingDto.builder()
                .bookingId(booking.getBookingId())
                .name(booking.getPassenger().getFullName())
                .status(booking.getStatus())
                .build();
    }
}
