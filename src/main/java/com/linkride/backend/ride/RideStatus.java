package com.linkride.backend.ride;

/** Phase 1 only ever produces SCHEDULED — matching/cancellation lifecycle states land in a later phase. */
public enum RideStatus {
    SCHEDULED
}
