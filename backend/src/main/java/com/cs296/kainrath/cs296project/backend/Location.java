package com.cs296.kainrath.cs296project.backend;



/**
 * Created by kainrath on 4/3/16.
 * Location object that keeps track of latitude and longitude
 * Latitude and longitude are not checked to see if they are within a valid range
 * since all latitude and longitude values originate from Google Api calls.
 */

public class Location {

    private double latitude;
    private double longitude;

    private static final double DEGREE_TO_RADIANS = 0.017453292519943295; // PI / 180 precomputed to save time
    private static final int EARTH_DIAMETER = 12742000; // Earth's diameter in meters

    public Location (double latitude, double longitude) {
        setLocation(latitude, longitude);
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLocation(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(double longitude) throws IllegalArgumentException {
        this.longitude = longitude;
    }

    // Calculates and returns the distance between two locations in meters
    public double distanceTo(Location other) {
        return distanceTo(other.getLatitude(), other.getLongitude());
    }

    // Calculates and returns the distance between two locations in meters
    public double distanceTo(double lat, double lon) {
        double distance = 0.5 - Math.cos((lat - this.latitude) * DEGREE_TO_RADIANS)/2 +
                Math.cos(this.latitude * DEGREE_TO_RADIANS) * Math.cos(lat * DEGREE_TO_RADIANS) *
                        (1 - Math.cos((lon - this.longitude) * DEGREE_TO_RADIANS))/2;
        return EARTH_DIAMETER * Math.asin(Math.sqrt(distance));
    }
}
