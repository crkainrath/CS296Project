package com.cs296.kainrath.cs296project.backend;

import com.google.android.gcm.server.Message;
import com.google.android.gcm.server.Sender;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.response.NotFoundException;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Named;

@Api(
        name = "userApi",
        version = "v1",
        resource = "user",
        namespace = @ApiNamespace(
                ownerDomain = "backend.cs296project.kainrath.cs296.com",
                ownerName = "backend.cs296project.kainrath.cs296.com",
                packagePath = ""
        )
)
public class UserEndpoint {

    private static final String URL = "jdbc:google:mysql://cs296-backend:cs296-app-location-data/UserLocation?user=root";
    private static final String DRIVER = "com.mysql.jdbc.GoogleDriver";
    private static final Logger logger = Logger.getLogger(UserEndpoint.class.getName());

    private static final String API_KEY = "AIzaSyAJuwfy0EoirghnDaThupzrqNTDVxsm650";

    /**
     * Sends a message to all users in a ChatGroup
     * @param email The email of the user sending the message
     * @param chatId The id of the ChatGroup which the message is being sent to
     * @param message The message to be sent to the ChatGroup
     */
    @ApiMethod(name = "sendMessage")
    public void sendMessage(@Named("email") String email, @Named("chatId") Integer chatId, @Named("message") String message) {
        logger.log(Level.FINEST, "Calling sendMessage method");

        // Make sure parameters are valid
        if (message == null || message.isEmpty() || email == null || email.isEmpty()) {
            // TODO: Notify sender that message failed to send
            logger.log(Level.FINE, "Invalid parameters");
            return;
        }

        try {
            // Connect to the database to retrieve gcm tokens of the users in the ChatGroup
            Class.forName(DRIVER);
            Connection conn = DriverManager.getConnection(URL);

            // Retrieve tokens of users in the chat group
            String tokenQuery = "SELECT Token FROM ChatUsers WHERE ChatId=" + chatId;
            ResultSet tokenSet = conn.createStatement().executeQuery(tokenQuery);
            List<String> tokens = new ArrayList<>();
            while (tokenSet.next()) {
                tokens.add(tokenSet.getString("Token"));
            }

            // Send GCM message
            Sender sender = new Sender(API_KEY);
            Message msg = new Message.Builder().addData("Action", "NewMessage").addData("ChatId", "" + chatId)
                    .addData("Message", message).addData("Email", email).build();
            sender.send(msg, tokens, 3);
        } catch (ClassNotFoundException | SQLException | IOException e) {
            logger.log(Level.SEVERE, "Failed to send GCM message");
            // TODO: Notify sender that message failed to send
        }
    }

    /**
     * Retrieves and returns the user associated with userId
     * @param userId The ID of the entity to be retrieved
     * @return User with the corresponding ID or null if there is no associated user
     */
    @ApiMethod(name = "get")
    public User getUser(@Named("userId") String userId) throws NotFoundException {
        logger.log(Level.FINEST, "Calling get method");

        User user = null;
        Connection conn = null;
        try {
            // Connect to database
            Class.forName(DRIVER);
            conn = DriverManager.getConnection(URL);

            // Get user info
            user = getUserHelper(conn, userId);

            // Close connection
            conn.close();
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "MySql driver not found: " + e.getMessage());
            return null;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQLException: " + e.getErrorCode());
            return null;
        }
        return user;
    }

    /**
     * Function is called when the app starts up and needs to retrieve the user's info
     * as well as set the user's current GCM token.  If the user is not in the database,
     * the user will be added to it.
     * @param userId The ID of the user.
     * @param email The email of the user.
     * @param token The token associated with the user's current session.
     * @return The user with the corresponding ID.
     */
    @ApiMethod(name = "getUserAndSetToken")
    public User getUserAndSetToken(@Named("userId") String userId, @Named("email") String email, @Named("token") String token) {
        logger.log(Level.FINEST, "Calling getUserAndSetToken method");
        User user;
        try {
            // Connect to the database
            Class.forName(DRIVER);
            Connection conn = DriverManager.getConnection(URL);

            // Get user info
            user = getUserHelper(conn, userId);

            if (user != null) { // Update user's token in the database
                conn.createStatement().executeUpdate("UPDATE UserInfo SET Token=\"" + token + "\" WHERE UserId=\""
                            + userId + "\"");
            } else { // New user, insert into the database
                String insertUser = "INSERT INTO UserInfo (UserId, Email, Token, Active) VALUES (\"" + userId +
                        "\", \"" + email + "\", \"" + token + "\", \"N\")";
                conn.createStatement().executeUpdate(insertUser);

                user = new User(userId, email, token);
            }

            // Close connection
            conn.close();
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "MySql driver not found: " + e.getMessage());
            return null;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQLException: " + e.getErrorCode());
            return null;
        }
        return user;
    }

    // Retrieves and returns the user's information associated with the userId from the established connection conn.
    private User getUserHelper(Connection conn, String userId) {
        User user;
        try {
            // Query database for the user
            String userInfoQuery = "SELECT * FROM UserInfo WHERE UserId=\"" + userId + "\"";
            ResultSet userQueryResult = conn.createStatement().executeQuery(userInfoQuery);
            if (!userQueryResult.next()) { // User not in the database
                logger.log(Level.FINE, "User not in database");
                return null;
            }
            user = new User(userId, userQueryResult.getString("Email"), userQueryResult.getString("Token"));

            // Query database for the user's interests
            String userInterestsQuery = "SELECT Interest FROM UserInterests WHERE UserId=\"" + userId + "\"";
            ResultSet interestsQueryResult = conn.createStatement().executeQuery(userInterestsQuery);
            Set<String> interests = new TreeSet<>();
            while (interestsQueryResult.next()) {
                interests.add(interestsQueryResult.getString("Interests"));
            }
            user.setInterests(interests);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQLException: " + e.getErrorCode());
            return null;
        }
        return user;
    }

    // Inserts a User into the database.  The function was used for testing, but isn't used anymore.
    @ApiMethod(name = "insert")
    public void insert(User user) {
        logger.log(Level.FINEST, "Calling insert method");

        try {
            Class.forName(DRIVER);
            Connection conn = DriverManager.getConnection(URL);

            // Insert user data into table
            String insertUser = "INSERT INTO UserInfo (UserId, Email, Token, Active) VALUES (\"" + user.getId() +
                    "\", \"" + user.getEmail() + "\", \"" + user.getToken() + "\", \"N\")";
            conn.createStatement().executeUpdate(insertUser);

            // Insert user interests into table
            List<String> interests = new ArrayList<>(user.getInterests());
            if (!interests.isEmpty()) {
                String insertInts = "INSERT INTO UserInterests (UserId, Interest) VALUES (\"" + user.getId() + "\", \"" +
                        interests.get(0) + "\")";
                for (int i = 0; i < interests.size(); ++i) {
                    insertInts += ", (\"" + user.getId() + "\", \"" + interests.get(i) + "\")";
                }

                conn.createStatement().executeUpdate(insertInts);
            }

            conn.close();
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "MySql driver not found: " + e.getMessage());
            return;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQLException: " + e.getErrorCode());
            return;
        }

    }

    /**
     * Updates the user's interests in the database.
     * @param userId The ID of the entity to be updated.
     * @param newInterests The interests to be added to the database.
     * @param removeInterests The interests to be removed from the database.
     * @throws NotFoundException if user is not in the database
     */
    @ApiMethod(name = "updateInterests")
    public void updateInterests(@Named("userId") String userId, @Named("newInterests") String newInterests,
                                @Named("removeInterests") String removeInterests) throws NotFoundException {

        logger.log(Level.FINEST, "Calling updateInterests method");

        // Need to parse interests since lists are not accurately formatted.
        String[] newInterestsArray = null;
        if (!newInterests.equals("")) { // Parse new interests
            newInterestsArray = newInterests.split(",,,");
        }

        String[] oldInterestsArray = null;
        if (!removeInterests.equals("")) { // Parse interests to remove
            oldInterestsArray = removeInterests.split(",,,");
        }

        if (newInterestsArray != null || oldInterestsArray != null) {
            try {
                // Connect to the database
                Class.forName(DRIVER);
                Connection conn = DriverManager.getConnection(URL);

                // Make sure userId is a valid user
                ResultSet validUser = conn.createStatement().executeQuery("SELECT UserId FROM UserInfo WHERE UserId=\"" + userId + "\"");
                if (!validUser.next() || !validUser.getString("UserId").equals(userId)) {
                    logger.log(Level.FINE, "User not in database, cannot update");
                    conn.close();
                    throw new NotFoundException("User not in the Database");
                }

                // Add interests if there are new interests
                if (newInterestsArray != null) {
                    String adds = "INSERT INTO UserInterests (UserId, Interest) VALUES (\"" +
                            userId + "\", \"" + newInterestsArray[0] + "\")";
                    for (int i = 1; i < newInterestsArray.length; ++i) {
                        adds += ", (\"" + userId + "\", \"" + newInterestsArray[i] + "\")";
                    }
                    conn.createStatement().executeUpdate(adds);
                }

                // Remove interests if there are any to remove
                if (oldInterestsArray != null) {
                    String delete = "DELETE FROM UserInterests WHERE UserId=\"" + userId + "\" AND Interest IN (\"" +
                            oldInterestsArray[0] + "\"";
                    for (int i = 1; i < oldInterestsArray.length; ++i) {
                        delete += ", \"" + oldInterestsArray[i] + "\"";
                    }
                    delete += ")";
                    conn.createStatement().executeUpdate(delete);
                }

                // Close connection
                conn.close();
            } catch (ClassNotFoundException e) {
                logger.log(Level.SEVERE, "MySql driver not found: " + e.getMessage());
                return;
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "SQLException: " + e.getErrorCode());
                return;
            }
        }
    }

    /**
     * Removes the user from the database.
     * @param userId The ID of the entity to delete
     * @throws NotFoundException if the userId does not correspond to an existing
     */
    @ApiMethod(name = "remove")
    public void remove(@Named("userId") String userId) throws NotFoundException {
        logger.log(Level.FINE, "Calling remove method");
        try {
            // Connect to the database
            Class.forName(DRIVER);
            Connection conn = DriverManager.getConnection(URL);

            // Remove from the database
            String delete = "DELETE FROM UserInfo, UserInterests WHERE UserId=\"" + userId + "\"";
            if (conn.createStatement().executeUpdate(delete) == -1) { // If true, the user is not in the database
                logger.log(Level.SEVERE, "User not in database, cannot remove");
                throw new NotFoundException("User Not in the Database");
            }

            // Close connection
            conn.close();
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "MySql driver not found: " + e.getMessage());
            return;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQLException: " + e.getErrorCode());
            return;
        }
    }
}