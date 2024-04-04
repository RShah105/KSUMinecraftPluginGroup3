package org.incendo.cloudpaper;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.sql.*;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

public final class Plugin extends JavaPlugin {
    private Connection connection;
    @Override
    public void onEnable() {
        getLogger().info("Enabled!");
        connectToDatabase();
        createTable();
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabled!");
        disconnectFromDatabase();
    }

    public boolean onCommand(CommandSender player, Command command, String label, String[] args){
        if (label.equalsIgnoreCase("ticket")){
            if (args.length == 0){
                player.sendMessage("Usage: /ticket create <type> <description>");
                player.sendMessage("Usage: /ticket list");
                return true;
            }

            if (args[0].equalsIgnoreCase("create") && player instanceof Player){
                handleTicketCreation((Player) player, args);
                return true;
            }

            if (args[0].equalsIgnoreCase("list") && player instanceof Player){
                handleTicketList((Player) player);
                return true;
            }
        }
        return false;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args){
        if (command.getName().equalsIgnoreCase("ticket")){
            if (args.length == 1) {
                List<String> completions = new ArrayList<>();
                completions.add("create");
                completions.add("list");
                return completions;
            } else if (args.length == 2 && args[0].equalsIgnoreCase("create")){
                List<String> completions = new ArrayList<>();
                completions.add("bug");
                completions.add("player");
                return completions;
            } else if (args.length == 3 && args[0].equalsIgnoreCase("create")){
                List<String> completions = new ArrayList<>();
                completions.add("<description>");
                return completions;
            }
        }
        return null;
    }

    private void handleTicketCreation(Player player, String[] args){ //will switch to uuid
        if (args.length < 3){
            player.sendMessage("Usage: /ticket create <type> <description>");
            return;
        }
        String type = args[1];
        String description = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        double x = player.getLocation().getX();
        double y = player.getLocation().getY();
        double z = player.getLocation().getZ();
        long creationTime = System.currentTimeMillis();

        insertTicket(player.getUniqueId(), type, description, "submitted", x, y, z, creationTime);
        player.sendMessage("Ticket submitted");
    }

    private void handleTicketList(Player player){
        UUID playerUUID = player.getUniqueId();
        List<String> tickets = getPlayerTickets(playerUUID);

        if(tickets.isEmpty()) {
            player.sendMessage("You have no tickets.");
        } else {
            player.sendMessage("List of your tickets:");
            for(String ticket : tickets){
                player.sendMessage(ticket);
            }
        }
    }

    private void connectToDatabase(){ //Local database is just a placeholder for now
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        try {
            String url = "jdbc:mysql://localhost:3306/ticket";
            String user = "root";
            String password = "root";
            connection = DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void disconnectFromDatabase(){
        try {
            if (connection != null && !connection.isClosed()){
                connection.close();
                getLogger().info("Disconnected from the database.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createTable(){
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
            statement.executeUpdate();
        } catch (SQLException e){
            e.printStackTrace();
        }
    }

    private void insertTicket(UUID playerUUID,String type, String description, String status, double x, double y, double z, long creationTime) {
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "INSERT INTO tickets (player_uuid, Type, Description, Status, x_coord, y_coord, z_coord, creation_time) VALUES (?, ?, ?, ?, ?, ?, ?,?)")){
            preparedStatement.setString(1, playerUUID.toString());
            preparedStatement.setString(2, type);
            preparedStatement.setString(3, description);
            preparedStatement.setString(4, status);
            preparedStatement.setDouble(5, x);
            preparedStatement.setDouble(6, y);
            preparedStatement.setDouble(7, z);
            preparedStatement.setLong(8, creationTime);
            preparedStatement.executeUpdate();
        } catch(SQLException e) {
            e.printStackTrace();
        }
    }

    private List<String> getPlayerTickets(UUID playerUUID) {
        List<String> tickets = new ArrayList<>();
        try(PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT * FROM tickets WHERE player_uuid = ?")){
            preparedStatement.setString(1, playerUUID.toString());
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()){
                int id = resultSet.getInt("ID");
                String type = resultSet.getString("Type");
                String description = resultSet.getString("Description");
                String status = resultSet.getString("Status");
                String ticketInfo = "ID: " + id + " | Type: " + type + " | Description: " + description + " | Status: " + status;
                tickets.add(ticketInfo);
            }
        } catch (SQLException e){
            e.printStackTrace();
        }
        return tickets;
    }
}
