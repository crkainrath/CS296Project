package com.cs296.kainrath.cs296project.backend;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by kainrath on 4/19/16.
 */

public class ChatGroup {
    // chatId is a unique identifier among all ChatGroups, even those with different interests
    private int chatId;
    private String chatInterest;
    private int chatSize;

    // Location of the ChatGroup.  Location is the average of all Users' locations
    private Location chatLocation;

    public ChatGroup(String interest, int chatId, int groupSize, double latitude, double longitude) {
        this.chatInterest = interest;
        this.chatId = chatId;
        this.chatSize = groupSize;
        this.chatLocation = new Location(latitude, longitude);
    }

    public boolean equals(ChatGroup other) {
        return this.chatId == other.chatId;
    }

    public String getInterest() {
        return chatInterest;
    }

    public int getChatId() {
        return chatId;
    }

    public int getChatSize() {
        return chatSize;
    }

    // Relocates the center of the group when a user currently in the groups changes location
    // but remains in the ChatGroup radius
    public void moveMember(double oldLat, double oldLong, double newLat, double newLong) {
        chatLocation.setLatitude((chatLocation.getLatitude() * chatSize - oldLat + newLat) / chatSize);
        chatLocation.setLongitude((chatLocation.getLongitude() * chatSize - oldLong + newLong) / chatSize);
    }

    // Adds a new member to the group, will increase the size
    public void addUserToGroup(double latitude, double longitude) {
        chatLocation.setLatitude((chatLocation.getLatitude() * chatSize + latitude) / (chatSize + 1));
        chatLocation.setLongitude((chatLocation.getLongitude() * chatSize + longitude) / (chatSize + 1));
        ++chatSize;
    }

    // Removes a user from the group and re-centers the group
    public void removeUserFromGroup(double oldLat, double oldLong) {
        if (chatSize >= 1) {
            chatLocation.setLatitude((chatLocation.getLatitude() * chatSize - oldLat) / (chatSize - 1));
            chatLocation.setLongitude((chatLocation.getLongitude() * chatSize - oldLong) / (chatSize - 1));
        }
        --chatSize;
    }

    public double getLatitude() {
        return chatLocation.getLatitude();
    }

    public double getLongitude() {
        return chatLocation.getLongitude();
    }
}
