package com.cs296.kainrath.cs296project.backend;

import com.google.android.gcm.server.Message;
import com.google.android.gcm.server.MulticastResult;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.android.gcm.server.Sender;
import com.google.api.server.spi.config.Nullable;
import com.google.api.server.spi.response.NotFoundException;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Named;

/**
 * An endpoint class for inserting into and querying the Location database as well as for joining
 * and leaving chat groups.
 */
@Api(
        name = "locationApi",
        version = "v1",
        resource = "location",
        namespace = @ApiNamespace(
                ownerDomain = "backend.cs296project.kainrath.cs296.com",
                ownerName = "backend.cs296project.kainrath.cs296.com",
                packagePath = ""
        )
)
public class LocationEndpoint {

    private static final Logger logger = Logger.getLogger(LocationEndpoint.class.getName());
    private static final String URL = "jdbc:google:mysql://cs296-backend:cs296-app-location-data/UserLocation?user=root";
    private static final String DRIVER = "com.mysql.jdbc.GoogleDriver";

    private static final String API_KEY = "AIzaSyAJuwfy0EoirghnDaThupzrqNTDVxsm650";

    // A user or chatgroup's radius
    private static final double DIST = 25;
    private static final double RAD_EARTH = 6371000; // In meters

    /**
     * This method gets the location of the user with the specified ID.  If the user is offline,
     * the location will be null.
     *
     * @param userId The id of the object to be returned.
     * @return The location of the user
     */
    @ApiMethod(name = "getLocation",
               path = "location",
               httpMethod = ApiMethod.HttpMethod.GET)
    public Location getLocation(@Named("userId") String userId) throws NotFoundException {
        logger.log(Level.FINE, "Calling getLocation method");
        Location location = null;
        try {
            // Connect to the database
            Class.forName(DRIVER);
            Connection conn = DriverManager.getConnection(URL);

            // Query the database
            String userQuery = "SELECT * FROM UserInfo WHERE UserId=\"" + userId + "\"";
            ResultSet userResult = conn.createStatement().executeQuery(userQuery);

            // Check if User exists
            if (userResult.next()) {
                // Check if User is online
                if (userResult.getString("Online").equals("Y")) {
                    location = new Location(userResult.getDouble("Latitude"), userResult.getDouble("Longitude"));
                }
            } else { // User does not exist
                logger.log(Level.FINEST, userId + " not in database");
                throw new NotFoundException(userId + " not in database");
            }

            conn.close();
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "MySql driver not found: " + e.getMessage());
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQLException: " + e.getErrorCode());
        }
        return location;
    }

    /**
     * This method activates and updates a User's location in the database as well as looks for groups to join and/or leave
     * @param userId The id associated with the user calling the function
     * @param email The email associated with the user calling the function
     * @param lat The current latitude of the user
     * @param lon The current longitude of the user
     * @param gcmToken The gcm token of the user, used for notifying other users if the current user joins/leaves a group
     * @param interests List of the user interests, used to find/create chatGroups
     * @param currChatGroups List of the chatGroups that the user is currently in
     * @return List of nearby chat groups
     */
    @ApiMethod(name = "updateLocation")
    public ChatGroupList updateLocation(@Named("userId") String userId, @Named("email") String email,
                                        @Named("lat") double lat, @Named("lon") double lon,
                                        @Named("gcmToken") String gcmToken,
                                        @Named("interests") String interests,
                                        @Named("currChatGroups") @Nullable String currChatGroups) {

        logger.log(Level.FINE, "Calling updateLocation method");

        if (interests == null || interests.isEmpty()) { // Need to have some interests
            logger.log(Level.FINEST, userId + " has no interests");
            return null;
        }

        // Interests is comma separated, single String
        // Lists aren't being sent correctly
        String[] splitInterests = interests.split(",");
        List<String> interestsList = Arrays.asList(splitInterests);

        // Check to see if the User just activated or is updating their location
        // currChatGroups will be null only when the user is initially activating
        if (currChatGroups == null) {
            logger.log(Level.FINEST, userId + " activated");
            return activateUser(userId, email, lat, lon, gcmToken, interestsList);
        }

        // Was sending a list of chatGroups, but the list was always empty
        // Retrieve chatIds from the String
        String[] currChatStrings = currChatGroups.split(",");
        List<Integer> currChatIds = new ArrayList<>();
        for (String s : currChatStrings) {
            currChatIds.add(Integer.parseInt(s));
        }

        // Update location, check for new nearby groups, and leave groups that the user is not near anymore
        ChatGroupList chatGroupList = null;
        try {
            // Create a connection to the database
            Class.forName(DRIVER);
            Connection conn = DriverManager.getConnection(URL);

            Location userLocation = new Location(lat, lon);

            // Get old location
            String oldLocationQuery = "SELECT Latitude, Longitude FROM UserInfo WHERE UserId=\"" + userId + "\"";
            ResultSet oldLocationResult = conn.createStatement().executeQuery(oldLocationQuery);
            double oldLat, oldLon;
            if (oldLocationResult.next()) {
                oldLat = oldLocationResult.getDouble("Latitude");
                oldLon = oldLocationResult.getDouble("Longitude");
            } else {
                logger.log(Level.SEVERE, "User not found in the database");
                return null;
            }

            // Update location in the database
            String updateUserLocation = "UPDATE UserInfo SET Latitude=" + lat + ", Longitude=" + lon +
                    " WHERE UserId=\"" + userId + "\"";
            conn.createStatement().executeUpdate(updateUserLocation);

            // Get nearby chat groups
            List<ChatGroup> nearbyChats = findChatGroupsInRadius(conn, userLocation);
            if (nearbyChats == null) {
                nearbyChats = new ArrayList<>();
            }

            // Compare interests to see if they match with any new nearby chats
            if (!nearbyChats.isEmpty()) {
                Iterator<ChatGroup> chatIterator = nearbyChats.iterator();
                while (chatIterator.hasNext()) {
                    ChatGroup chat = chatIterator.next();
                    if (currChatIds.contains(chat.getChatId())) { // Already in chatGroup
                        // Update the location of the chat group.  The user may not be in the radius of the chat group
                        // anymore.  If that happens, the user will be removed from the chat group.
                        if (updateCurrentChatGroup(conn, chat, userLocation, oldLat, oldLon, email, userId)) {
                            chatIterator.remove(); // User outside group range
                        }
                    } else if (interests.contains(chat.getInterest())) { // New chatGroup with matching interest
                        joinNewGroup(conn, chat, userLocation, userId, gcmToken, email);
                    } else { // Non-matching group
                        chatIterator.remove();
                    }
                }
            }

            chatGroupList = new ChatGroupList(nearbyChats);

            conn.close();
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "MySql driver not found: " + e.getMessage());
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQLException: " + e.getErrorCode());
        }

        return chatGroupList;
    }

    // This method activates a user, joins nearby chatGroups that match the user's interests, and creates chatGroups
    // for any unmatched user interest.
    // Returns a list of chatGroups that the user is in.
    private  ChatGroupList activateUser(String userId, String email, double lat, double lon, String gcmToken, List<String> interestsList) {
        logger.log(Level.FINE, "Calling activateUser method");

        ChatGroupList chatGroupList = null;

        try {
            // Connect to database
            Class.forName(DRIVER);
            Connection conn = DriverManager.getConnection(URL);

            // Update active status and location
            conn.createStatement().executeUpdate("UPDATE UserInfo SET Latitude=" + lat + ", Longitude=" + lon +
                    ", Active=\"Y\" WHERE UserId=\"" + userId + "\"");

            // Find nearby chatGroups
            Location userLocation = new Location(lat, lon);
            List<ChatGroup> nearbyChatGroups = findChatGroupsInRadius(conn, userLocation);
            if (nearbyChatGroups == null) {
                nearbyChatGroups = new ArrayList<>();
            }

            // Check interests of nearby chatGroups
            if (!nearbyChatGroups.isEmpty()) {
                Iterator<ChatGroup> chatGroupIterator = nearbyChatGroups.iterator();
                while (chatGroupIterator.hasNext()) {
                    ChatGroup chat = chatGroupIterator.next();
                    if (interestsList.contains(chat.getInterest())) { // chatGroup with matching interest, join the group
                        joinNewGroup(conn, chat, userLocation, userId, gcmToken, email);
                    } else { // The interest does not match, remove the group
                        chatGroupIterator.remove();
                    }
                }
            }

            // Start a chat group for any interests that don't already have a corresponding chatGroup
            List<String> unmatchedInterests = new ArrayList<>();
            unmatchedInterests.addAll(interestsList);
            for (ChatGroup chat : nearbyChatGroups) { // Remove interests that are already covered by another chatGroup
                unmatchedInterests.remove(chat.getInterest());
            }
            if (!unmatchedInterests.isEmpty()) { // There are unmatched interests, start new chatGroup
                nearbyChatGroups.addAll(startNewChatGroups(conn, unmatchedInterests, lat, lon, gcmToken, userId, email));
            }

            chatGroupList = new ChatGroupList(nearbyChatGroups);

            conn.close();
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "MySql driver not found: " + e.getMessage());
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQLException: " + e.getErrorCode());
        }
        return chatGroupList;
    }

    /**
     * This method activates and updates a User's location in the database as well as looks for groups to join and/or leave
     * @param userId The id associated with the user calling the function
     * @param email The email associated with the user calling the function
     * @param lat The current latitude of the user
     * @param lon The current longitude of the user
     * @param currChats List of the chatGroups that the user is currently in
     * @return List of nearby chat groups
     */
    @ApiMethod(name = "deactivateUser")
    public void deactivateUser(@Named("userId") String userId, @Named("email") String email,
                               @Named("lat") double lat, @Named("lon") double lon,
                               @Named("currChats") String currChats) {
        logger.log(Level.FINE, "Calling deactivateUser method");

        // chatIds is list of comma separated numbers, not actual list
        String[] splitChatIds = currChats.split(",");
        List<Integer> currChatIds = new ArrayList<>();
        for (String s : splitChatIds) {
            currChatIds.add(Integer.parseInt(s));
        }

        try {
            // Connect to the database
            Class.forName(DRIVER);
            Connection conn = DriverManager.getConnection(URL);

            // Get ChatGroup information
            String groupQuery = "SELECT * FROM ChatGroups WHERE ChatId=" + currChatIds.get(0);
            for (int i = 1; i < currChatIds.size(); ++i) {
                groupQuery += " OR ChatId=" + currChatIds.get(i);
            }
            ResultSet groupResult = conn.createStatement().executeQuery(groupQuery);
            List<ChatGroup> currChatGroups = new ArrayList<>();
            while (groupResult.next()) {
                ChatGroup group = new ChatGroup(groupResult.getString("Interest"), groupResult.getInt("ChatId"),
                        groupResult.getInt("GroupSize"), groupResult.getDouble("Latitude"), groupResult.getDouble("Longitude"));
                currChatGroups.add(group);
            }

            // Make user "Offline"
            String offlineUpdate = "UPDATE UserInfo SET Active=\"N\" WHERE UserId=\"" + userId + "\"";
            conn.createStatement().executeUpdate(offlineUpdate);

            // Should not be empty, but make sure
            if (!currChatGroups.isEmpty()) {
                // These destroy updates are only for chats with size 1
                String destroyChatGroupUpdate = "DELETE FROM ChatGroups WHERE ChatId=";
                String destroyChatUserUpdate = "DELETE FROM ChatUsers WHERE ChatId=";
                boolean first = true;
                for (ChatGroup group : currChatGroups) {
                    if (group.getChatSize() == 1) { // User is only member, destroy group
                        if (first) {
                            destroyChatGroupUpdate += group.getChatId();
                            destroyChatUserUpdate += group.getChatId();
                            first = false;
                        } else {
                            destroyChatGroupUpdate += " OR ChatId=" + group.getChatId();
                            destroyChatUserUpdate += " OR ChatId=" + group.getChatId();
                        }
                    } else { // Remove user from a larger group, group will not be destroyed
                        group.removeUserFromGroup(lat, lon);
                        leaveChatGroup(conn, group, userId, email);
                    }
                }
                if (!first) { // Destroy the chatGroups where the user is the only member
                    Statement stmt = conn.createStatement();
                    stmt.addBatch(destroyChatGroupUpdate);
                    stmt.addBatch(destroyChatUserUpdate);
                    stmt.executeBatch();
                }
            } else {
                logger.log(Level.SEVERE, "currChatGroups is empty");
            }

            conn.close();
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "MySql driver not found: " + e.getMessage());
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQLException: " + e.getErrorCode());
        }
    }

    /**
     * Creates new chatGroups for each interest with the location of the group being the coordinates lat, lon.
     * @param conn A connection to the database
     * @param interests A list of the users interests
     * @param lat The user's latitude
     * @param lon The user's longitude
     * @param gcmToken The user's gcm token
     * @param userId The user's id
     * @param email The user's email
     * @return A list of the generated chatGroups.
     */
    private List<ChatGroup> startNewChatGroups(Connection conn, List<String> interests, double lat, double lon, String gcmToken,
                                               String userId, String email) {
        logger.log(Level.FINE, "Calling startNewChatGroups method");

        List<ChatGroup> newGroups = null;

        // Create insert statement.
        String insertGroups = "INSERT INTO ChatGroups (Interest, Latitude, Longitude, GroupSize) VALUES (\"" +
                interests.get(0) + "\", " + lat + ", " + lon + ", " + 1 + ")";
        for (int i = 1; i < interests.size(); ++i) {
            insertGroups += ", (\"" + interests.get(i) + "\", " + lat + ", " + lon + ", " + 1 + ")";
        }

        try {
            // Insert chatGroups into the ChatGroups table.  The database auto-generates a unique id which is used
            // as the chatId.
            Statement stmt = conn.createStatement();
            conn.createStatement().executeUpdate(insertGroups, Statement.RETURN_GENERATED_KEYS);
            ResultSet rs = stmt.getGeneratedKeys();
            int index = 0;
            newGroups = new ArrayList<>();
            while (rs.next()) {
                int chatId = rs.getInt(1);
                newGroups.add(new ChatGroup(interests.get(index++), chatId, 1, lat, lon));
            }

            // Insert into ChatUsers table
            String insertChatUsers = "INSERT INTO ChatUsers (ChatId, UserId, Token, Email) VALUES (" + newGroups.get(0).getChatId() +
                    ", \"" + userId + "\", \"" + gcmToken + "\", \"" + email + "\")";
            for (int i = 1; i < newGroups.size(); ++i) {
                insertChatUsers += ", (" + newGroups.get(i).getChatId() + ", \"" + userId + "\", \"" + gcmToken +
                        "\", \"" + email + "\")";
            }
            conn.createStatement().executeUpdate(insertChatUsers);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQLException: " + e.getErrorCode());
        }
        return newGroups;
    }

    /**
     * Adds the user into the chatGroup and notifies the current users in the chatGroup of the new member.  The coordinates
     * of the chatGroup are updated.
     * @param conn A connection to the database
     * @param chat The chatGroup to join
     * @param userLocation The location of the user
     * @param userId The user's id
     * @param gcmToken The user's gcm token
     * @param email The user's email
     */
    private void joinNewGroup(Connection conn, ChatGroup chat, Location userLocation, String userId, String gcmToken, String email) {
        logger.log(Level.FINE, "Calling joinNewGroup method");
        try {
            // Update the database
            chat.addUserToGroup(userLocation.getLatitude(), userLocation.getLongitude());
            String chatUserUpdate = "INSERT INTO ChatUsers (ChatId, UserId, Token, Email) VALUES (" + chat.getChatId() +", " +
                    "\"" + userId + "\", \"" + gcmToken + "\", \"" + email + "\")";
            String chatGroupUpdate = "UPDATE ChatGroups SET Latitude=" + chat.getLatitude() + ", Longitude=" + chat.getLongitude() +
                    ", GroupSize=GroupSize + 1 WHERE ChatId=" + chat.getChatId();
            Statement stmt = conn.createStatement();
            stmt.addBatch(chatGroupUpdate);
            stmt.addBatch(chatUserUpdate);
            stmt.executeBatch();

            // Get the gcm tokens of the users in the chatGroup in order to notify them
            String tokenQuery = "SELECT Token FROM ChatUsers WHERE ChatId=" + chat.getChatId();
            ResultSet tokenResult = conn.createStatement().executeQuery(tokenQuery);
            List<String> tokens = new ArrayList<>();
            while (tokenResult.next()) {
                tokens.add(tokenResult.getString("Token"));
            }

            // Send GCM Message
            Sender sender = new Sender(API_KEY);
            Message msg = new Message.Builder().addData("UserId", userId).addData("Email", email)
                    .addData("ChatId", "" + chat.getChatId()).addData("Action", "JoiningGroup").build();
            sender.send(msg, tokens, 3); // Retries 3 times
            // MulticastResult result = sender.send(msg, tokens, 3);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQLException: " + e.getErrorCode());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "IOException: " + e.getMessage());
        }
    }

    /**
     * Searches the database for chatGroups with the user's radius.
     * @param conn A connection to the database
     * @param userLocation The location of the user
     * @return A list of chatGroups that are near the user.
     */
    private List<ChatGroup> findChatGroupsInRadius(Connection conn, Location userLocation) {
        logger.log(Level.FINE, "Calling findChatGroupsInRadius method");

        // Calculate the boundaries for latitude and longitude when searching in the database
        double ang_dist = DIST / RAD_EARTH;
        double latitude = userLocation.getLatitude();
        double longitude = userLocation.getLongitude();
        double ang_cos = Math.cos(ang_dist);
        double ang_sin = Math.sin(ang_dist);
        double sin_lat1 = Math.sin(latitude * Math.PI / 180); // Need radians
        double cos_lat1 = Math.cos(latitude * Math.PI / 180); // Need radians    1 = Cos(0)
        double dLat = Math.abs(Math.asin(sin_lat1 * ang_cos + cos_lat1 * ang_sin * 1) * 180 / Math.PI - latitude);
        double dLong = Math.abs(Math.atan2(1 * ang_sin * cos_lat1, ang_cos - sin_lat1 * sin_lat1) * 180 / Math.PI); // Lat2 = Lat 1;
                                        // 1 = sin(90)
        double lat1 = latitude + dLat;
        double lat2 = latitude - dLat;
        double long1 = longitude + dLong;
        double long2 = longitude - dLong;

        List<ChatGroup> nearbyChatGroups = new ArrayList<>();
        try {
            String groupQuery = "SELECT * FROM ChatGroups WHERE Latitude BETWEEN " + lat2 + " AND " + lat1 +
                    " AND Longitude BETWEEN " + long2 + " AND " + long1;

            ResultSet rs = conn.createStatement().executeQuery(groupQuery);

            while (rs.next()) {
                nearbyChatGroups.add(new ChatGroup(rs.getString("Interest"), rs.getInt("ChatId"), rs.getInt("GroupSize"),
                        rs.getDouble("Latitude"), rs.getDouble("Longitude")));
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQLException: " + e.getErrorCode());
        }
        return nearbyChatGroups;
    }

    /**
     * Updates the chatGroups location when the user's location changes.  If the user's location is outside of the chatGroup radius,
     * the user will be removed from the group.
     * @param conn A connection to the database
     * @param chat The chatGroup to update
     * @param userLocation The location of the user
     * @param oldLat The user's previous location
     * @param oldLon The user's previous longitude
     * @param email The user's email
     * @param userId The user's id
     * @return True if the user leaves the chatGroup, false otherwise.
     */
    private boolean updateCurrentChatGroup(Connection conn, ChatGroup chat, Location userLocation,
                                                    double oldLat, double oldLon, String email, String userId) {
        logger.log(Level.FINE, "Calling updateCurrentChatGroup method");

        // Update the location of the chatGroup and calculate the distance between the user and the chatGroup
        chat.moveMember(oldLat, oldLon, userLocation.getLatitude(), userLocation.getLongitude());
        double newDist = userLocation.distanceTo(chat.getLatitude(), chat.getLongitude());
        if (newDist > DIST && chat.getChatSize() > 1) { // Out of group range
            logger.log(Level.FINE, "User out of chatGroup range");
            chat.removeUserFromGroup(oldLat, oldLon); // If only 1 user, then the location of the group will just be the users new location
            leaveChatGroup(conn, chat, userId, email);
            return true;
        } else {
            logger.log(Level.FINE, "User still in chatGroup range");
            try { // Still in group range
                String updateGroup = "UPDATE ChatGroups SET Latitude=" + chat.getLatitude() + ", Longitude=" + chat.getLongitude() +
                        " WHERE ChatId=" + chat.getChatId();
                conn.createStatement().executeUpdate(updateGroup);
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "SQLException: " + e.getErrorCode());
            }
            return false;
        }
    }

    /**
     * Removes the user from the chatGroup and updates the chatGroups location.  Other users are notified through GCM.
     * @param conn A connection to the database
     * @param chat The chatGroup to leave
     * @param userId The user's id
     * @param email The user's email
     */
    private void leaveChatGroup(Connection conn, ChatGroup chat, String userId, String email) {
        logger.log(Level.FINE, "Calling leaveChatGroup method");
        try {
            // Remove the user from
            Statement stmt = conn.createStatement();
            String deleteChatUser = "DELETE FROM ChatUsers WHERE UserId=\"" + userId + "\" AND ChatId=" +
                    chat.getChatId();
            String updateChatGroup = "UPDATE ChatGroups SET Latitude=" + chat.getLatitude() + ", Longitude=" + chat.getLongitude() +
                    ", GroupSize=GroupSize - 1 WHERE ChatId=" + chat.getChatId();
            stmt.addBatch(deleteChatUser);
            stmt.addBatch(updateChatGroup);
            stmt.executeBatch();

            // Get other users from DB
            String groupUsers = "SELECT Token FROM ChatUsers WHERE ChatId=" + chat.getChatId();
            ResultSet userSet = conn.createStatement().executeQuery(groupUsers);

            List<String> userTokens = new ArrayList<>();
            while (userSet.next()) {
                userTokens.add(userSet.getString("Token"));
            }

            if (userTokens.isEmpty()) {
                return;
            }

            // Notify through GCM
            Sender sender = new Sender(API_KEY);
            Message message = new Message.Builder().addData("Action", "LeavingGroup").addData("ChatId", "" + chat.getChatId())
                    .addData("Email", email).build();
            try {
                MulticastResult result = sender.send(message, userTokens, 3); // Retry sending 3 times
            } catch (IOException e) {
                String error = e.getMessage();
            }

        } catch (SQLException e) {
            String error = e.getSQLState();
        }
    }
}