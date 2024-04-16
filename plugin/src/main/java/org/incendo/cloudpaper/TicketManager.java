package org.incendo.cloudpaper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.jetbrains.annotations.NotNull;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.logging.Level;

import static org.incendo.cloudpaper.Plugin.LOGGER;

public class TicketManager implements CommandExecutor, TabCompleter, Listener {

    private final Plugin plugin;
    private final DatabaseManager databaseManager;
    private final MiniMessage miniMessage;
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final FileConfiguration config;
    private final HashMap<UUID, PermissionAttachment> permissionAttachments = new HashMap<>();

    public TicketManager(Plugin plugin, DatabaseManager databaseManager,FileConfiguration config) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.miniMessage = MiniMessage.miniMessage();
        this.config = config;
        Objects.requireNonNull(Bukkit.getPluginCommand("ticket")).setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (databaseManager.adminExists(playerId)) {
            givePermission(event.getPlayer(), "ticket.admin");
        }
    }

    public void loadPermissions() {
        List<HashMap<String, String>> admins = databaseManager.getAdmins();
        for (HashMap<String, String> admin : admins) {
            Player player = Bukkit.getPlayer(UUID.fromString(admin.get("player_uuid")));
            if (player != null) {
                givePermission(player, "ticket.admin");
            }
        }
    }

    public void unloadPermissions() {
        for (Map.Entry<UUID, PermissionAttachment> entry : permissionAttachments.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                removePermission(player, "ticket.admin");
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(commandSender instanceof Player)) {
            LOGGER.log(Level.WARNING, "Only players in-game can execute this command!");
            return true;
        }
        Player player = (Player) commandSender;
        if (label.equalsIgnoreCase("ticket")) {
            if (cooldowns.containsKey(player.getUniqueId()) && cooldowns.get(player.getUniqueId()) > System.currentTimeMillis()) {
                player.sendMessage(miniMessage.deserialize("<red>There is a 3 second cooldown between /ticket commands!</red>"));
                return true;
            }
            if (args.length == 0) {
                handleTicketChatUI(player);
            } else {
                try {
                    String subCommand = args[0];
                    if (subCommand.equalsIgnoreCase("create")) {
                        handleTicketCreation(player, args);
                    } else if (subCommand.equalsIgnoreCase("list")) {
                        handleTicketList(player);
                    } else if (subCommand.equalsIgnoreCase("update")) {
                        handleTicketUpdate(player, args);
                    } else if (subCommand.equalsIgnoreCase("close")) {
                        handleTicketClose(player, args);
                    } else if (subCommand.equalsIgnoreCase("reopen")) {
                        handleTicketReopen(player, args);
                    } else if (subCommand.equalsIgnoreCase("teleport") || subCommand.equalsIgnoreCase("tp")) {
                        handleTeleport(player, args);
                    } else if (subCommand.equalsIgnoreCase("help")) {
                        sendHelpMessage(player);
                    } else if (subCommand.equalsIgnoreCase("crown")) {
                        handleTicketCrown(player, args);
                    } else if (subCommand.equalsIgnoreCase("uncrown")) {
                        handleTicketUncrown(player, args);
                    } else if (subCommand.equalsIgnoreCase("claim")) {
                        handleTicketClaim(player, args);
                    }
                    if (!subCommand.equalsIgnoreCase("help")) {
                        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + config.getInt("command-cooldown") * 1000L);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return true;
    }

    private void handleTicketClaim(Player player, String[] args) {
        if (!player.hasPermission("ticket.admin")) {
            player.sendMessage(miniMessage.deserialize("<red>You do not have permission for that command!</red>"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(miniMessage.deserialize("<red>Usage: /ticket claim <id></red>"));
            return;
        }
        int id = Integer.parseInt(args[1]);
        if (!databaseManager.ticketExists(id)) {
            player.sendMessage(miniMessage.deserialize("<red>Invalid ticket ID. Please provide a valid ticket ID. '/ticket list' to get valid IDs</red>")); // Notify player of invalid ticket ID
            return;
        }
        HashMap<String, String> ticketInfo = databaseManager.getTicketInfo(id);
        if (ticketInfo.get("Status").contains("closed")) {
            player.sendMessage(miniMessage.deserialize("<red>Ticket is already closed!</red>"));
            return;
        }
        if (ticketInfo.get("Status").contains("claimed")) {
            player.sendMessage(miniMessage.deserialize("<red>Ticket is already claimed!</red>"));
            return;
        }
        databaseManager.updateTicket(id, ticketInfo.get("Description"), "claimed by " + player.getName());
        player.sendMessage(miniMessage.deserialize("<gold>Ticket claimed successfully!</gold>"));
    }

    private void handleTicketUncrown(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage(miniMessage.deserialize("<red>You do not have permission for that command!</red>"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(miniMessage.deserialize("<red>Usage: /ticket uncrown <username></red>"));
            return;
        }
        String username = args[1];
        Player target = Bukkit.getPlayer(username);
        UUID targetUUID;
        if (target == null) {
            targetUUID = Bukkit.getOfflinePlayer(username).getUniqueId();
        } else {
            targetUUID = target.getUniqueId();
        }
        if (!databaseManager.adminExists(targetUUID)) {
            player.sendMessage(miniMessage.deserialize("<red>Player is not an admin!</red>"));
            return;
        }
        databaseManager.removeAdmin(targetUUID);
        if (target != null) {
            removePermission(target, "ticket.admin");
        }
        player.sendMessage(miniMessage.deserialize("<gold>Admin access has been removed from " + target.getName() + " successfully!</gold>"));
    }

    private void handleTicketCrown(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage(miniMessage.deserialize("<red>You do not have permission for that command!</red>"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(miniMessage.deserialize("<red>Usage: /ticket crown <username></red>"));
            return;
        }
        String username = args[1];
        Player target = Bukkit.getPlayer(username);
        UUID targetUUID;
        String targetUsername;
        if (target == null) {
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(username);
            targetUUID = offlineTarget.getUniqueId();
            targetUsername = Bukkit.getOfflinePlayer(username).getName();
        } else {
            targetUUID = target.getUniqueId();
            targetUsername = target.getName();
        }
        if (databaseManager.adminExists(targetUUID)) {
            player.sendMessage(miniMessage.deserialize("<red>Player is already an admin!</red>"));
            return;
        }
        databaseManager.makeAdmin(targetUUID, targetUsername);
        if (target != null) {
            givePermission(target, "ticket.admin");
        }
        player.sendMessage(miniMessage.deserialize("<gold>Admin access has been given to " + targetUsername + " successfully!</gold>"));
    }

    public void givePermission(Player player, String permission) {
        PermissionAttachment attachment = player.addAttachment(plugin);
        attachment.setPermission(permission, true);
        permissionAttachments.put(player.getUniqueId(), attachment);
        LOGGER.info(player.getName() + " just received admin permissions for the ticketing system.");
    }

    public void removePermission(Player player, String permission) {
        PermissionAttachment attachment = permissionAttachments.get(player.getUniqueId());
        if (attachment != null) {
            attachment.unsetPermission(permission);
        }
        LOGGER.info(player.getName() + " just got admin permissions removed for the ticketing system.");
    }

    private void handleTicketReopen(Player player, String[] args) {
        if (!player.hasPermission("ticket.admin")) {
            player.sendMessage(miniMessage.deserialize("<red>You do not have permission for that command!</red>"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(miniMessage.deserialize("<red>Usage: /ticket reopen <id></red>"));
            return;
        }
        int id = Integer.parseInt(args[1]);
        if (!databaseManager.ticketExists(id)) {
            player.sendMessage(miniMessage.deserialize("<red>Invalid ticket ID. Please provide a valid ticket ID. '/ticket list' to get valid IDs</red>")); // Notify player of invalid ticket ID
            return;
        }
        HashMap<String, String> ticketInfo = databaseManager.getTicketInfo(id);
        if (ticketInfo.get("Status").contains("open")) {
            player.sendMessage(miniMessage.deserialize("<red>Ticket is already open!</red>"));
            return;
        }
        databaseManager.updateTicket(id, ticketInfo.get("Description"), "open");
        player.sendMessage(miniMessage.deserialize("<gold>Ticket reopened successfully!</gold>"));
    }

    private void handleTicketClose(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(miniMessage.deserialize("<red>Usage: /ticket close <id></red>"));
            return;
        }
        int id = Integer.parseInt(args[1]);
        if (!databaseManager.ticketExists(id)) {
            player.sendMessage(miniMessage.deserialize("<red>Invalid ticket ID. Please provide a valid ticket ID. '/ticket list' to get valid IDs</red>")); // Notify player of invalid ticket ID
            return;
        }
        HashMap<String, String> ticketInfo = databaseManager.getTicketInfo(id);
        if (!checkPlayerOwnsTicket(player, ticketInfo)) {
            return;
        }
        if (ticketInfo.get("Status").contains("closed")) {
            player.sendMessage(miniMessage.deserialize("<red>Ticket is already closed!</red>"));
            return;
        }
        String newStatus = "closed by admin";
        if (ticketInfo.get("player_uuid").equalsIgnoreCase(player.getUniqueId().toString())) {
            newStatus = "closed by creator";
        }
        databaseManager.updateTicket(id, ticketInfo.get("Description"), newStatus);
        player.sendMessage(miniMessage.deserialize("<gold>Ticket closed successfully!</gold>"));
    }

    private void handleTeleport(Player player, String[] args) {
        if (!player.hasPermission("ticket.admin")) {
            player.sendMessage(miniMessage.deserialize("<red>You do not have permission for that command!</red>"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(miniMessage.deserialize("<red>Usage: /ticket teleport <id></red>"));
            return;
        }
        int id = Integer.parseInt(args[1]);
        if (!databaseManager.ticketExists(id)) {
            player.sendMessage(miniMessage.deserialize("<red>Invalid ticket ID. Please provide a valid ticket ID. '/ticket list' to get valid IDs</red>"));
            return;
        }
        HashMap<String, String> ticketInfo = databaseManager.getTicketInfo(id);
        double x = Double.parseDouble(ticketInfo.get("x_coord"));
        double y = Double.parseDouble(ticketInfo.get("y_coord"));
        double z = Double.parseDouble(ticketInfo.get("z_coord"));
        World world = getWorldByWorldName(ticketInfo.get("world"));
        if (world == null) {
            LOGGER.log(Level.SEVERE, "Invalid world name '" + ticketInfo.get("world") + "' found in the database!");
            return;
        }
        Location location = new Location(world, x, y, z, Float.parseFloat(ticketInfo.get("yaw")), Float.parseFloat(ticketInfo.get("pitch")));
        player.teleport(location);
        String locationString = "World: " + ticketInfo.get("world") + ", X: " + (int) x + ", Y: " + (int) y + ", Z: " + (int) z;
        player.sendMessage(miniMessage.deserialize("<gold>Teleported to ticket location <aqua>(" + locationString + ")</aqua>!</gold>"));
    }

    private World getWorldByWorldName(String worldName) {
        for (World world : Bukkit.getWorlds()) {
            if (world.getName().equalsIgnoreCase(worldName)) {
                return world;
            }
        }
        return null;
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(miniMessage.deserialize("<green>This is a ticketing system plugin used for server moderation. The goal is to allow users to submit a ticket and have an admin view/solve their issue when available.</green>"));
        player.sendMessage(miniMessage.deserialize("<aqua>List of commands and usages: {hover for descriptions}</aqua>"));
        player.sendMessage(miniMessage.deserialize("<hover:show_text:'<green>Shows this help menu</green>'><gold> - /ticket help</gold></hover>"));
        player.sendMessage(miniMessage.deserialize("<hover:show_text:'<green>Creates a help ticket</green>'><gold> - /ticket create <description></gold></hover>"));
        player.sendMessage(miniMessage.deserialize("<hover:show_text:'<green>Updates an existing help ticket</green>'><gold> - /ticket update <id> <description> <new_value></gold></hover>"));
        player.sendMessage(miniMessage.deserialize("<hover:show_text:'<green>Closes an existing help ticket</green>'><gold> - /ticket close <id></gold></hover>"));
        if (player.hasPermission("ticket.admin")) {
            player.sendMessage(miniMessage.deserialize("<hover:show_text:'<green>Reopens a closed ticket</green>'><gold> - /ticket reopen <id> </gold></hover>"));
            player.sendMessage(miniMessage.deserialize("<hover:show_text:'<green>Teleports to the location of a ticket</green>'><gold> - /ticket teleport <id></gold></hover>"));
            player.sendMessage(miniMessage.deserialize("<hover:show_text:'<green>Lists all opened tickets</green>'><gold> - /ticket list</gold></hover>"));
            player.sendMessage(miniMessage.deserialize("<hover:show_text:'<green>Claims a ticket to work on</green>'><gold> - /ticket claim <id></gold></hover>"));
        } else {
            player.sendMessage(miniMessage.deserialize("<hover:show_text:'<green>Lists all owned and active tickets</green>'><gold> - /ticket list</gold></hover>"));

        }
        if (player.isOp()) {
            player.sendMessage(miniMessage.deserialize("<hover:show_text:'<green>Gives a user admin permissions for the ticketing system</green>'><gold> - /ticket crown <username></gold></hover>"));
            player.sendMessage(miniMessage.deserialize("<hover:show_text:'<green>Removes ticketing system admin permissions from a user system</green>'><gold> - /ticket uncrown <username></gold></hover>"));
        }
    }

    private void handleTicketChatUI(Player player) {
        Component message = miniMessage.deserialize("<aqua>List of ticket command options:</aqua>");
        Component help = miniMessage.deserialize("<gold> - Click <click:suggest_command:/ticket help><green><bold><hover:show_text:'/ticket help'>HERE</hover></bold></green></click> to open the commands list!</gold>");
        Component create = miniMessage.deserialize("<gold> - Click <click:suggest_command:/ticket create ><green><bold><hover:show_text:'/ticket create'>HERE</hover></bold></green></click> to create a new ticket!</gold>");
        Component update = miniMessage.deserialize("<gold> - Click <click:suggest_command:/ticket update ><green><bold><hover:show_text:'/ticket update'>HERE</hover></bold></green></click> to update an existing ticket!</gold>");
        Component close = miniMessage.deserialize("<gold> - Click <click:suggest_command:/ticket close ><green><bold><hover:show_text:'/ticket close'>HERE</hover></bold></green></click> to close a ticket!</gold>");
        Component reopen = miniMessage.deserialize("<gold> - Click <click:suggest_command:/ticket reopen ><green><bold><hover:show_text:'/ticket reopen'>HERE</hover></bold></green></click> to reopen a closed ticket!</gold>");
        Component list = miniMessage.deserialize("<gold> - Click <click:suggest_command:/ticket list ><green><bold><hover:show_text:'/ticket list'>HERE</hover></bold></green></click> to list your own tickets!</gold>");
        Component listAdmin = miniMessage.deserialize("<gold> - Click <click:suggest_command:/ticket list ><green><bold><hover:show_text:'/ticket list'>HERE</hover></bold></green></click> to list all existing tickets!</gold>");
        Component teleport = miniMessage.deserialize("<gold> - Click <click:suggest_command:/ticket teleport ><green><bold><hover:show_text:'/ticket teleport'>HERE</hover></bold></green></click> to teleport to a ticket location!</gold>");
        Component crown = miniMessage.deserialize("<gold> - Click <click:suggest_command:/ticket crown ><green><bold><hover:show_text:'/ticket crown'>HERE</hover></bold></green></click> to give admin access to a user!</gold>");
        Component uncrown = miniMessage.deserialize("<gold> - Click <click:suggest_command:/ticket uncrown ><green><bold><hover:show_text:'/ticket uncrown'>HERE</hover></bold></green></click> to remove admin access from a user!</gold>");
        Component claim = miniMessage.deserialize("<gold> - Click <click:suggest_command:/ticket claim ><green><bold><hover:show_text:'/ticket claim'>HERE</hover></bold></green></click> to claim a ticket to work on!</gold>");
        player.sendMessage(message);
        player.sendMessage(help);
        player.sendMessage(create);
        player.sendMessage(update);
        player.sendMessage(close);
        if (player.hasPermission("ticket.admin")) {
            player.sendMessage(reopen);
            player.sendMessage(listAdmin);
            player.sendMessage(teleport);
            player.sendMessage(claim);
        } else {
            player.sendMessage(list);
        }
        if (player.isOp()) {
            player.sendMessage(crown);
            player.sendMessage(uncrown);
        }
    }

    // Method to handle ticket creation
    private void handleTicketCreation(Player player, String[] args) {
        if (args.length < 2) { // If insufficient arguments provided
            player.sendMessage(miniMessage.deserialize("<red>Usage: /ticket create <newDescription></red>")); // Provide usage instructions
            return;
        }
        String description = String.join(" ", Arrays.copyOfRange(args, 1, args.length)); // Extract ticket description from arguments
        double x = player.getLocation().getX(); // Get player's X coordinate
        double y = player.getLocation().getY(); // Get player's Y coordinate
        double z = player.getLocation().getZ(); // Get player's Z coordinate
        double pitch = player.getLocation().getPitch();
        double yaw = player.getLocation().getYaw();
        long creationTime = System.currentTimeMillis(); // Get current system time
        databaseManager.insertTicket(player.getUniqueId(), player.getName(), description, "open", player.getWorld().getName(), x, y, z, pitch, yaw, creationTime); // Insert ticket into the database
        player.sendMessage(miniMessage.deserialize("<gold>Ticket submitted! View it's information using /ticket list</gold>")); // Notify player that ticket has been submitted
    }

    // Method to handle listing player's tickets
    private void handleTicketList(Player player) {
        // Check if player has permission to view all tickets
        List<HashMap<String, String>> tickets = null;
        String headMessage = "<aqua>List of all tickets {Hover for descriptions}</aqua>";
        String noTicketsMessage = "<gold>There are no tickets to view.</gold>";
        System.out.println(player.permissionValue("ticket.admin"));
        if (player.hasPermission("ticket.admin")) {
            tickets = databaseManager.getAllTickets(); // Retrieve all tickets from the database
        } else {
            UUID playerUUID = player.getUniqueId();
            tickets = databaseManager.getPlayerTickets(playerUUID); // Retrieve player's tickets from the database
            headMessage = "<aqua>List of your tickets {Hover for descriptions}</aqua>";
            noTicketsMessage = "<gold>You have no tickets to view. Use '/tickets create' if you need to create one.</gold>";
        }
        if (tickets.isEmpty()) { // If there are no tickets
            player.sendMessage(miniMessage.deserialize(noTicketsMessage)); // Notify player that there are no tickets
        } else {
            player.sendMessage(miniMessage.deserialize(headMessage)); // Notify player that a list of tickets will be displayed
            for (HashMap<String, String> ticket : tickets) { // Iterate through each ticket
                 if (!ticket.get("Status").contains("closed")) {
                    player.sendMessage(miniMessage.deserialize(ticket.get("message"))); // Send ticket information to player
                 }
            }
        }
    }

    private void handleTicketUpdate(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(miniMessage.deserialize("<red>Usage: /ticket edit <id> <newDescription></red>"));
            return;
        }
        String id = args[1]; // Extract ticket ID from arguments
        String attribute = args[2]; // Extract attribute to edit from arguments
        if (!databaseManager.ticketExists(Integer.parseInt(id))) {
            player.sendMessage(miniMessage.deserialize("<red>Invalid ticket ID. Please provide a valid ticket ID. '/ticket list' to get valid IDs</red>")); // Notify player of invalid ticket ID
            return;
        }
        HashMap<String, String> ticketInfo = databaseManager.getTicketInfo(Integer.parseInt(id));;
        if (!checkPlayerOwnsTicket(player, ticketInfo) || !player.hasPermission("ticket.admin")) {
            return;
        }
        System.out.println(2);
        if (attribute.equalsIgnoreCase("description")) { // If player wants to edit ticket description
            System.out.println(1);
            String newDescription = String.join(" ", Arrays.copyOfRange(args, 3, args.length)); // Extract new ticket description from arguments
            databaseManager.updateTicket(Integer.parseInt(id), newDescription, ticketInfo.get("Status")); // Update ticket description in the database
            player.sendMessage(miniMessage.deserialize("<gold>Ticket description updated successfully!</gold>")); // Notify player of successful update
        } else {
            System.out.println(3);
            player.sendMessage(miniMessage.deserialize("<red>Invalid attribute. Please provide a valid attribute to edit (description).</red>")); // Notify player of invalid attribute
        }
    }

    public boolean checkPlayerOwnsTicket(Player player, HashMap<String, String> ticketInfo) {
        if (!ticketInfo.get("player_uuid").equalsIgnoreCase(player.getUniqueId().toString())) {
            player.sendMessage(miniMessage.deserialize("<red>You are not the owner of that ticket!</red>"));
            return false;
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("ticket")) {
            if (args.length == 1) {
                List<String> completions = new ArrayList<>();
                completions.add("create");
                completions.add("list");
                completions.add("update");
                completions.add("close");
                completions.add("help");
                if (sender.hasPermission("ticket.admin")) {
                    completions.add("reopen");
                    completions.add("teleport");
                    completions.add("tp");
                    completions.add("claim");
                }
                if (sender.isOp()) {
                    completions.add("crown");
                    completions.add("uncrown");
                }
                return completions;
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("create")) {
                    List<String> completions = new ArrayList<>();
                    completions.add("<description>");
                    return completions;
                } else if (args[0].equalsIgnoreCase("update") || args[0].equalsIgnoreCase("close")) {
                    List<String> completions = new ArrayList<>();
                    completions.add("<id>");
                    return completions;
                } else if (sender.hasPermission("ticket.admin") && (args[0].equalsIgnoreCase("reopen") || args[0].equalsIgnoreCase("teleport")) || args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("claim")) {
                    List<String> completions = new ArrayList<>();
                    completions.add("<id>");
                    return completions;
                } else if (sender.isOp() && (args[0].equalsIgnoreCase("crown") || args[0].equalsIgnoreCase("uncrown"))) {
                    List<String> completions = new ArrayList<>();
                    completions.add("<username>");
                    return completions;
                }
            } else if (args.length == 3) {
                if (args[0].equalsIgnoreCase("update")) {
                    List<String> completions = new ArrayList<>();
                    completions.add("description");
                    return completions;
                }
            }
        }
        return null;
    }
}
