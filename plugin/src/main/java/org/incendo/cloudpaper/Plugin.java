package org.incendo.cloudpaper;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.sql.*;
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
        }
        String type = args[1];
        String description = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        insertTicket(player.getName(), type, description, "submitted");
        player.sendMessage("Ticket submitted");
    }

    private void handleTicketList(Player player){
        List<String> tickets = getPlayerTickets(player.getName());

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
                "PlayerName VARCHAR(45) NOT NULL," +
                "Type VARCHAR (45) NOT NULL," +
                "Description VARCHAR(255) NOT NULL," +
                "Status VARCHAR(45) NOT NULL" +
                ")"
        )) {
            statement.executeUpdate();
        } catch (SQLException e){
            e.printStackTrace();
        }
    }

    private void insertTicket(String playerName,String type, String description, String status) {
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "INSERT INTO tickets (PlayerName, Type, Description, Status) VALUES (?, ?, ?, ?)")){
            preparedStatement.setString(1, playerName);
            preparedStatement.setString(2, type);
            preparedStatement.setString(3, description);
            preparedStatement.setString(4, status);
            preparedStatement.executeUpdate();
        } catch(SQLException e) {
            e.printStackTrace();
        }
    }

    private List<String> getPlayerTickets(String playerName){
        List<String> tickets = new ArrayList<>();
        try(PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT * FROM tickets WHERE PlayerName = ?")){
            preparedStatement.setString(1, playerName);
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()){
                int id = resultSet.getInt("ID");
                String type = resultSet.getString("Type");
                String description = resultSet.getString("Description");
                String status = resultSet.getString("Status");
                String ticketInfo = "ID: " + id + " | Description: " + description + " | Status: " + status;
                tickets.add(ticketInfo);
            }
        } catch (SQLException e){
            e.printStackTrace();
        }
        return tickets;
    }
}
