package com.linkride.backend.route.geometry;

/** A raw decoded coordinate, before any cumulative-distance/time annotation. */
public record LatLng(double lat, double lng) {
}
