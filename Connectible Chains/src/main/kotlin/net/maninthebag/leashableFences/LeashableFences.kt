package net.maninthebag.leashableFences

import org.bukkit.plugin.java.JavaPlugin

class LeashableFences : JavaPlugin() {

    override fun onEnable() {
        server.pluginManager.registerEvents(ItemClickListener(this), this)
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
