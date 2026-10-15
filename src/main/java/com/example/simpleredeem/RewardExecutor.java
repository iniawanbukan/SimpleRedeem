package com.example.simpleredeem;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Level;

public class RewardExecutor {
    public static boolean deliver(SimpleRedeem plugin, Player player, CodeManager.RedeemCode rc, boolean debug) {
        if (rc.rewards == null || rc.rewards.isEmpty()) return true;
        for (String raw : rc.rewards) {
            String r = raw.trim();
            try {
                if (r.toLowerCase().startsWith("item:")) {
                    String[] p = r.split(":", 3);
                    Material mat = Material.matchMaterial(p[1]);
                    if (mat == null) {
                        plugin.getLogger().warning("Unknown material in reward: " + r);
                        continue;
                    }
                    int amount = (p.length > 2) ? parseInt(p[2], 1) : 1;
                    ItemStack item = new ItemStack(mat, Math.max(1, amount));
                    java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                    for (ItemStack ls : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), ls);
                    }
                } else if (r.toLowerCase().startsWith("money:")) {
                    String[] p = r.split(":", 2);
                    double amount = parseDouble(p[1], 0);
                    boolean done = giveMoney(plugin, player, amount);
                    if (!done) plugin.getLogger().warning("Money reward skipped (Vault?).");
                } else if (r.toLowerCase().startsWith("cmd:") || r.toLowerCase().startsWith("command:")) {
                    String cmd = r.substring(r.indexOf(':') + 1);
                    cmd = cmd.replace("{PLAYER}", player.getName())
                            .replace("%player%", player.getName())
                            .replace("%uuid%", player.getUniqueId().toString())
                            .replace("%code%", rc.code);
                    cmd = applyPlaceholders(player, cmd);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                } else {
                    // treat as console command by default
                    String cmd = r.replace("{PLAYER}", player.getName())
                            .replace("%player%", player.getName())
                            .replace("%uuid%", player.getUniqueId().toString())
                            .replace("%code%", rc.code);
                    cmd = applyPlaceholders(player, cmd);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to deliver reward: " + r, e);
            }
        }
        return true;
    }

    private static boolean giveMoney(SimpleRedeem plugin, Player player, double amount) {
        try {
            Class<?> econClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object rsp = Bukkit.getServer().getServicesManager().getRegistration(econClass);
            if (rsp == null) return false;
            Object provider = rsp.getClass().getMethod("getProvider").invoke(rsp);
            provider.getClass().getMethod("depositPlayer", String.class, double.class)
                    .invoke(provider, player.getName(), amount);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String applyPlaceholders(Player player, String text) {
        try {
            Class<?> cls = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Object res = cls.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class).invoke(null, player, text);
            return String.valueOf(res);
        } catch (Throwable t) {
            return text;
        }
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
    private static double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}