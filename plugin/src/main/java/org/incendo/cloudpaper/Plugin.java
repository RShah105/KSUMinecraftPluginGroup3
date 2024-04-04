package org.incendo.cloudpaper;

import okhttp3.*;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.sql.*;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import java.io.IOException;

public final class Plugin extends JavaPlugin {
    private Connection connection; // Connection to the database
    private String webhookUrl = "https://discord.com/api/webhooks/1225576162122797117/-73cUaCpH48c7U0qDyjZQIFxxNdYXt1Oz_I_R_r_aC9E5r1zwujPXvdOmhoLfH57u-AC"; // Discord webhook URL for posting ticket information

    @Override
    public void onEnable() {
        getLogger().info("Enabled!"); // Log plugin enable status
        connectToDatabase(); // Connect to the database
        createTable(); // Create the 'tickets' table if it doesn't exist
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabled!"); // Log plugin disable status
        disconnectFromDatabase(); // Disconnect from the database
    }

    // Handle commands executed by players
    public boolean onCommand(CommandSender player, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("ticket")) { // If the command label is 'ticket'
            if (args.length == 0) { // If no arguments provided
                player.sendMessage("Usage: /ticket create <type> <description>"); // Provide usage instructions
                player.sendMessage("Usage: /ticket list"); // Provide usage instructions
                return true; // Command execution completed
            }

            // Handle ticket creation command
            if (args[0].equalsIgnoreCase("create") && player instanceof Player) {
                handleTicketCreation((Player) player, args); // Handle ticket creation
                return true; // Command execution completed
            }

            // Handle ticket list command
            if (args[0].equalsIgnoreCase("list") && player instanceof Player) {
                handleTicketList((Player) player); // Handle ticket list
                return true; // Command execution completed
            }
        }
        return false; // Command execution not completed
    }

    // Handle tab completion for commands
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("ticket")) { // If the command is 'ticket'
            if (args.length == 1) { // If there's one argument
                List<String> completions = new ArrayList<>();
                completions.add("create"); // Add 'create' as a completion suggestion
                completions.add("list"); // Add 'list' as a completion suggestion
                return completions; // Return the list of completions
            } else if (args.length == 2 && args[0].equalsIgnoreCase("create")) { // If there are two arguments and the first one is 'create'
                List<String> completions = new ArrayList<>();
                completions.add("bug"); // Add 'bug' as a completion suggestion
                completions.add("player"); // Add 'player' as a completion suggestion
                return completions; // Return the list of completions
            } else if (args.length == 3 && args[0].equalsIgnoreCase("create")) { // If there are three arguments and the first one is 'create'
                List<String> completions = new ArrayList<>();
                completions.add("<description>"); // Add '<description>' as a completion suggestion
                return completions; // Return the list of completions
            }
        }
        return null; // No completions
    }

    // Method to handle ticket creation
    private void handleTicketCreation(Player player, String[] args) {
        if (args.length < 3) { // If insufficient arguments provided
            player.sendMessage("Usage: /ticket create <type> <description>"); // Provide usage instructions
            return; // Exit the method
        }
        String type = args[1]; // Extract ticket type from arguments
        String description = String.join(" ", Arrays.copyOfRange(args, 2, args.length)); // Extract ticket description from arguments
        double x = player.getLocation().getX(); // Get player's X coordinate
        double y = player.getLocation().getY(); // Get player's Y coordinate
        double z = player.getLocation().getZ(); // Get player's Z coordinate
        long creationTime = System.currentTimeMillis(); // Get current system time

        insertTicket(player.getUniqueId(), type, description, "submitted", x, y, z, creationTime); // Insert ticket into the database
        player.sendMessage("Ticket submitted"); // Notify player that ticket has been submitted

        // Post ticket information to Discord webhook
        postTicketToDiscord(player.getName(), type, description);
    }

    // Method to handle listing player's tickets
    private void handleTicketList(Player player) {
        UUID playerUUID = player.getUniqueId(); // Get player's UUID
        List<String> tickets = getPlayerTickets(playerUUID); // Retrieve player's tickets from the database

        if (tickets.isEmpty()) { // If player has no tickets
            player.sendMessage("You have no tickets."); // Notify player
        } else {
            player.sendMessage("List of your tickets:"); // Notify player
            for (String ticket : tickets) { // Iterate through player's tickets
                player.sendMessage(ticket); // Send each ticket information to the player
            }
        }
    }

    // Method to connect to the database
    private void connectToDatabase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver"); // Load MySQL driver
        } catch (ClassNotFoundException e) {
            e.printStackTrace(); // Print stack trace if driver not found
        }

        try {
            String url = "jdbc:mysql://localhost:3306/ticket"; // Database URL
            String user = "root"; // Database username
            String password = "root"; // Database password
            connection = DriverManager.getConnection(url, user, password); // Establish connection
        } catch (SQLException e) {
            e.printStackTrace(); // Print stack trace if connection fails
        }
    }

    // Method to disconnect from the database
    private void disconnectFromDatabase() {
        try {
            if (connection != null && !connection.isClosed()) { // If connection is not null and is not closed
                connection.close(); // Close the connection
                getLogger().info("Disconnected from the database."); // Log disconnection
            }
        } catch (SQLException e) {
            e.printStackTrace(); // Print stack trace if disconnection fails
        }
    }

    // Method to create the 'tickets' table in the database if it doesn't exist
    private void createTable() {
        try (PreparedStatement statement = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS tickets (" +
                        "ID INT PRIMARY KEY AUTO_INCREMENT NOT NULL," +
                        "player_uuid VARCHAR(36) NOT NULL," +
                        "Type VARCHAR (45) NOT NULL," +
                        "Description VARCHAR(255) NOT NULL," +
                        "Status VARCHAR(45) NOT NULL," +
                        "x_coord DOUBLE NOT NULL," +
                        "y_coord DOUBLE NOT NULL," +
                        "z_coord DOUBLE NOT NULL," +
                        "creation_time BIGINT NOT NULL" +
                        ")"
        )) {
            statement.executeUpdate(); // Execute SQL statement to create the table
        } catch (SQLException e) {
            e.printStackTrace(); // Print stack trace if table creation fails
        }
    }

    // Method to insert a new ticket into the database
    private void insertTicket(UUID playerUUID, String type, String description, String status, double x, double y, double z, long creationTime) {
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "INSERT INTO tickets (player_uuid, Type, Description, Status, x_coord, y_coord, z_coord, creation_time) VALUES (?, ?, ?, ?, ?, ?, ?,?)")){
            preparedStatement.setString(1, playerUUID.toString()); // Set player UUID
            preparedStatement.setString(2, type); // Set ticket type
            preparedStatement.setString(3, description); // Set ticket description
            preparedStatement.setString(4, status); // Set ticket status
            preparedStatement.setDouble(5, x); // Set X coordinate
            preparedStatement.setDouble(6, y); // Set Y coordinate
            preparedStatement.setDouble(7, z); // Set Z coordinate
            preparedStatement.setLong(8, creationTime); // Set creation time
            preparedStatement.executeUpdate(); // Execute SQL statement to insert the ticket
        } catch(SQLException e) {
            e.printStackTrace(); // Print stack trace if ticket insertion fails
        }
    }

    // Method to retrieve player's tickets from the database
    private List<String> getPlayerTickets(UUID playerUUID) {
        List<String> tickets = new ArrayList<>();
        try(PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT * FROM tickets WHERE player_uuid = ?")){
            preparedStatement.setString(1, playerUUID.toString()); // Set player UUID in the SQL query
            ResultSet resultSet = preparedStatement.executeQuery(); // Execute SQL query

            while (resultSet.next()){ // Iterate through query results
                int id = resultSet.getInt("ID"); // Get ticket ID
                String type = resultSet.getString("Type"); // Get ticket type
                String description = resultSet.getString("Description"); // Get ticket description
                String status = resultSet.getString("Status"); // Get ticket status
                String ticketInfo = "ID: " + id + " | Type: " + type + " | Description: " + description + " | Status: " + status; // Construct ticket information string
                tickets.add(ticketInfo); // Add ticket information to the list
            }
        } catch (SQLException e){
            e.printStackTrace(); // Print stack trace if ticket retrieval fails
        }
        return tickets; // Return the list of tickets
    }

    // Method to post ticket information to Discord webhook
    private void postTicketToDiscord(String playerName, String type, String description) {
        String message = "New ticket created by: " + playerName + "\nType: " + type + "\nDescription: " + description; // Construct message to be sent to Discord

        OkHttpClient client = new OkHttpClient(); // Create OkHttpClient instance
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), "{\"content\":\"" + message + "\"}"); // Create request body with JSON content
        Request request = new Request.Builder() // Create HTTP request
                .url(webhookUrl) // Set URL
                .post(body) // Set POST method with request body
                .build(); // Build the request

        try {
            Response response = client.newCall(request).execute(); // Execute the request
            response.close(); // Close the response
        } catch (IOException e) {
            e.printStackTrace(); // Print stack trace if request execution fails
        }
    }
}
