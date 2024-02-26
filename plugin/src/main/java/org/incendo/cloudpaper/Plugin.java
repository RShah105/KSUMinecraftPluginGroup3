package org.incendo.cloudpaper;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class Plugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Enabled!");

    }

    @Override
    public void onDisable() {
        getLogger().info("Disabled!");
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if (label.equalsIgnoreCase("test")){
            sender.sendMessage("test");
            return true;
        }
        return false;
    }
}
