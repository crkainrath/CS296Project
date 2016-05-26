package com.cs296.kainrath.cs296project.backend;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by kainrath on 3/25/16.
 */

// Represents a User
public class User {
    private String userId;
    private String email;
    private String gcmToken;
    private Set<String> interests;

    // A user's current location
    private Location location;

    public User(String userId, String email, String gcmToken) {
        this(userId, email, gcmToken, null);
    }

    public User(String userId, String email, String gcmToken, Set<String> interests) {
        this.userId = userId;
        this.email = email;
        this.gcmToken = gcmToken;
        if (interests == null) {
            this.interests = new TreeSet<>();
        } else {
            this.interests = interests;
        }
    }

    public String getId() { return this.userId; }

    public String getEmail() { return this.email; }

    public Set<String> getInterests() { return this.interests; }

    public String getToken() { return this.gcmToken; }

    // Methods for adding interest(s)
    public void addInterests(List<String> interests) { this.interests.addAll(interests); }
    public void addInterests(Set<String> interests) { this.interests.addAll(interests); }
    public void addInterest(String interest) { this.interests.add(interest); }

    // Replace current interests with a new set of interests
    public void setInterests(Set<String> interests) { this.interests = interests; }

    public void setToken(String gcmToken) { this.gcmToken = gcmToken; }

    public void setLocation(Location location) {
        this.location = location;
    }

    public void setLocation(double lat, double lon) {
        if (location == null) {
            location = new Location(lat, lon);
        } else {
            location.setLatitude(lat);
            location.setLongitude(lon);
        }
    }
}
