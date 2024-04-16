package org.incendo.cloudpaper;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger;

public final class Plugin extends JavaPlugin {

    public static Logger LOGGER;
    private DatabaseManager databaseManager;
    private TicketManager ticketManager;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        LOGGER = this.getLogger();
        databaseManager = new DatabaseManager(this.getConfig());
        ticketManager = new TicketManager(this, databaseManager, this.getConfig());
        setupDatabase();
        ticketManager.loadPermissions();
        LOGGER.info("Enabled!"); // Log plugin enable status
    }

    @Override
    public void onDisable() {
        ticketManager.unloadPermissions();
        databaseManager.disconnectFromDatabase(); // Disconnect from the database
        getLogger().info("Disabled!"); // Log plugin disable status
    }

    private void setupDatabase() {
        databaseManager.connectToDatabase();
        databaseManager.createTicketsTable();
        databaseManager.createAdminsTable();
    }
}