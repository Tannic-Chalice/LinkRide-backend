package com.linkride.backend.booking;

/** Who cancelled a booking — a passenger cancelling their own request, or a driver removing one. */
public enum CancelInitiator {
    PASSENGER,
    DRIVER
}
