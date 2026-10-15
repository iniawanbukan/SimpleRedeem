package com.example.simpleredeem;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class SimpleRedeem extends JavaPlugin {
    private static SimpleRedeem instance;
    private FileConfiguration messages;
    private File messagesFile;
    private FileConfiguration autoConfig;
    private File autoFile;
    private CodeManager codeManager;
    private AutoRedeemScheduler autoScheduler;
    private boolean debugMode = false;

    public static SimpleRedeem getInstance() { return instance; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        migrateYaml(new File(getDataFolder(), "config.yml"), "config.yml");
        reloadConfig();
        saveDefaultMessages();
        migrateYaml(new File(getDataFolder(), "message.yml"), "message.yml");
        loadMessages();
        saveDefaultAuto();
        migrateYaml(new File(getDataFolder(), "auto.yml"), "auto.yml");
        loadAutoConfig();
        codeManager = new CodeManager(this);
        codeManager.load();
        debugMode = getConfig().getBoolean("debug-mode", false);

        getCommand("redeem").setExecutor(new RedeemCommand(this));
        getCommand("sr").setExecutor(new SrAdminCommand(this));
        getCommand("sr").setTabCompleter(new SrAdminTabCompleter(this));

        autoScheduler = new AutoRedeemScheduler(this);
        autoScheduler.start();

        getLogger().info("SimpleRedeem enabled.");
    }

    @Override
    public void onDisable() {
        if (autoScheduler != null) autoScheduler.stop();
        if (codeManager != null) codeManager.save();
        getLogger().info("SimpleRedeem disabled.");
    }

    public void reloadPlugin() {
        migrateYaml(new File(getDataFolder(), "config.yml"), "config.yml");
        reloadConfig();
        migrateYaml(new File(getDataFolder(), "message.yml"), "message.yml");
        loadMessages();
        migrateYaml(new File(getDataFolder(), "auto.yml"), "auto.yml");
        loadAutoConfig();
        codeManager.load();
        debugMode = getConfig().getBoolean("debug-mode", false);
        if (autoScheduler != null) autoScheduler.restart();
        getLogger().info(msg("plugin_reloaded"));
    }

    public CodeManager getCodeManager() { return codeManager; }
    public boolean isDebugEnabled() { return debugMode; }
    public void setDebugEnabled(boolean v) { debugMode = v; }
    public AutoRedeemScheduler getAutoScheduler() { return autoScheduler; }

    private void migrateYaml(File file, String resourcePath) {
        if (!file.exists()) return;
        try {
            FileConfiguration user = YamlConfiguration.loadConfiguration(file);
            InputStream defStream = getResource(resourcePath);
            if (defStream == null) return;
            FileConfiguration def = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            int userVer = user.getInt("config-version", 0);
            int defVer = def.getInt("config-version", 0);
            if (userVer >= defVer && userVer > 0) return;
            // merge missing keys recursively
            mergeMissing(user, def);
            if (defVer > 0) user.set("config-version", defVer);
            user.save(file);
            getLogger().info("Updated " + file.getName() + " to config-version " + defVer);
        } catch (Exception e) {
            getLogger().warning("Failed to migrate " + file.getName() + ": " + e.getMessage());
        }
    }

    private void mergeMissing(ConfigurationSection target, ConfigurationSection source) {
        if (target == null || source == null) return;
        for (String key : source.getKeys(false)) {
            if (!target.contains(key)) {
                target.set(key, source.get(key));
            } else if (source.isConfigurationSection(key) && target.isConfigurationSection(key)) {
                mergeMissing(target.getConfigurationSection(key), source.getConfigurationSection(key));
            }
        }
    }

    private void saveDefaultMessages() {
        messagesFile = new File(getDataFolder(), "message.yml");
        if (!messagesFile.exists()) saveResource("message.yml", false);
    }

    public void loadMessages() {
        messagesFile = new File(getDataFolder(), "message.yml");
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        InputStream def = getResource("message.yml");
        if (def != null) {
            YamlConfiguration defCfg = YamlConfiguration.loadConfiguration(new InputStreamReader(def, StandardCharsets.UTF_8));
            messages.setDefaults(defCfg);
        }
    }

    public FileConfiguration getAutoConfig() { return autoConfig; }

    private void saveDefaultAuto() {
        autoFile = new File(getDataFolder(), "auto.yml");
        if (!autoFile.exists()) saveResource("auto.yml", false);
    }

    public void loadAutoConfig() {
        autoFile = new File(getDataFolder(), "auto.yml");
        if (!autoFile.exists()) saveResource("auto.yml", false);
        autoConfig = YamlConfiguration.loadConfiguration(autoFile);
        InputStream def = getResource("auto.yml");
        if (def != null) {
            YamlConfiguration defCfg = YamlConfiguration.loadConfiguration(new InputStreamReader(def, StandardCharsets.UTF_8));
            autoConfig.setDefaults(defCfg);
        }
    }

    public boolean saveAutoConfig() {
        try { if (autoConfig != null && autoFile != null) autoConfig.save(autoFile); return true; }
        catch (IOException e) { getLogger().warning("Could not save auto.yml: " + e.getMessage()); return false; }
    }

    public FileConfiguration getMessagesConfig() { return messages; }

    public String msg(String key) {
        String prefix = messages.getString("prefix", "");
        String m = messages.getString("messages." + key, key);
        return color(prefix + m);
    }

    public String msgRaw(String key) {
        return messages.getString("messages." + key, key);
    }

    public static String color(String s) {
        if (s == null) return "";
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
    }

    public boolean saveMessages() {
        try { messages.save(messagesFile); return true; }
        catch (IOException e) { getLogger().warning("Could not save message.yml: " + e.getMessage()); return false; }
    }
}