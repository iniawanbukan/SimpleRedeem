package com.example.simpleredeem;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class CodeManager {
    private final SimpleRedeem plugin;
    private final File codesFile;
    private FileConfiguration codesConfig;
    private final Map<String, RedeemCode> codes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public CodeManager(SimpleRedeem plugin) {
        this.plugin = plugin;
        this.codesFile = new File(plugin.getDataFolder(), "codes.yml");
    }

    public void load() {
        codes.clear();
        if (!codesFile.exists()) {
            try {
                codesFile.getParentFile().mkdirs();
                codesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create codes.yml: " + e.getMessage());
            }
        }
        codesConfig = YamlConfiguration.loadConfiguration(codesFile);
        ConfigurationSection sec = codesConfig.getConfigurationSection("codes");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection c = sec.getConfigurationSection(key);
                if (c == null) continue;
                RedeemCode rc = new RedeemCode();
                rc.code = key;
                rc.limit = c.getInt("limit", -1);
                rc.used = c.getInt("used", 0);
                rc.enabled = c.getBoolean("enabled", true);
                rc.permbased = c.getBoolean("permbased", false);
                rc.expiry = c.getString("expiry", null);
                rc.rewards = new ArrayList<>(c.getStringList("rewards"));
                rc.redeemedPlayers = new HashSet<>(c.getStringList("redeemed"));
                codes.put(key, rc);
            }
        }
    }

    public void save() {
        codesConfig = new YamlConfiguration();
        ConfigurationSection sec = codesConfig.createSection("codes");
        for (RedeemCode rc : codes.values()) {
            ConfigurationSection c = sec.createSection(rc.code);
            c.set("limit", rc.limit);
            c.set("used", rc.used);
            c.set("enabled", rc.enabled);
            c.set("permbased", rc.permbased);
            c.set("expiry", rc.expiry);
            c.set("rewards", rc.rewards);
            c.set("redeemed", new ArrayList<>(rc.redeemedPlayers));
        }
        try {
            codesConfig.save(codesFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save codes.yml: " + e.getMessage());
        }
    }

    private String norm(String code) {
        if (code == null) return null;
        return plugin.getConfig().getBoolean("case-sensitive", false) ? code : code.toUpperCase();
    }

    public RedeemCode get(String code) { return codes.get(norm(code)); }
    public boolean exists(String code) { return codes.containsKey(norm(code)); }

    public boolean create(String code, int limit) {
        String n = norm(code);
        if (n == null || n.isEmpty()) return false;
        if (n.length() > plugin.getConfig().getInt("max-code-length", 32)) return false;
        if (codes.containsKey(n)) return false;
        RedeemCode rc = new RedeemCode();
        rc.code = n;
        rc.limit = limit;
        rc.used = 0;
        rc.enabled = true;
        rc.permbased = false;
        rc.expiry = null;
        rc.rewards = new ArrayList<>();
        rc.redeemedPlayers = new HashSet<>();
        codes.put(n, rc);
        save();
        return true;
    }

    public boolean delete(String code) {
        String n = norm(code);
        if (!codes.containsKey(n)) return false;
        codes.remove(n);
        save();
        return true;
    }

    public boolean addReward(String code, String reward) {
        RedeemCode rc = get(code);
        if (rc == null) return false;
        rc.rewards.add(reward);
        save();
        return true;
    }

    public boolean removeReward(String code, int index) {
        RedeemCode rc = get(code);
        if (rc == null) return false;
        if (index < 0 || index >= rc.rewards.size()) return false;
        rc.rewards.remove(index);
        save();
        return true;
    }

    public boolean setUses(String code, int limit) {
        RedeemCode rc = get(code);
        if (rc == null) return false;
        rc.limit = limit;
        save();
        return true;
    }

    public boolean setEnabled(String code, boolean v) {
        RedeemCode rc = get(code); if (rc == null) return false;
        rc.enabled = v; save(); return true;
    }
    public boolean setPermbased(String code, boolean v) {
        RedeemCode rc = get(code); if (rc == null) return false;
        rc.permbased = v; save(); return true;
    }
    public void renew(String code, int limit, boolean enabled, boolean permbased, String expiry, List<String> rewards) {
        String n = norm(code);
        RedeemCode rc = codes.get(n);
        if (rc == null) {
            create(code, limit);
            rc = codes.get(n);
        }
        if (rc == null) return;
        rc.limit = limit;
        rc.used = 0;
        rc.enabled = enabled;
        rc.permbased = permbased;
        rc.expiry = (expiry == null || expiry.isEmpty() || expiry.equalsIgnoreCase("none")) ? null : expiry;
        rc.redeemedPlayers.clear();
        rc.rewards = new ArrayList<>(rewards == null ? new ArrayList<>() : rewards);
        save();
    }

    public boolean setExpiry(String code, String value) {
        RedeemCode rc = get(code); if (rc == null) return false;
        rc.expiry = (value == null || value.isEmpty() || value.equalsIgnoreCase("none") || value.equalsIgnoreCase("null")) ? null : value;
        save(); return true;
    }

    public boolean isExpired(RedeemCode rc) {
        if (rc.expiry == null) return false;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
            sdf.setLenient(false);
            Date expiry = sdf.parse(rc.expiry);
            // expiry at end of that day
            Calendar cal = Calendar.getInstance();
            cal.setTime(expiry);
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            return System.currentTimeMillis() > cal.getTimeInMillis();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasRedeemed(String code, UUID uuid) {
        RedeemCode rc = get(code);
        if (rc == null) return false;
        return rc.redeemedPlayers.contains(uuid.toString());
    }

    public RedeemResult redeem(org.bukkit.entity.Player player, String codeInput, boolean debug) {
        String n = norm(codeInput);
        RedeemCode rc = codes.get(n);
        if (rc == null) return RedeemResult.INVALID;
        if (debug) {
            // skip checks and depletion
            boolean ok = RewardExecutor.deliver(plugin, player, rc, true);
            return ok ? RedeemResult.SUCCESS : RedeemResult.FAIL;
        }
        if (!rc.enabled) return RedeemResult.DISABLED;
        if (isExpired(rc)) return RedeemResult.EXPIRED;
        if (rc.permbased && !player.hasPermission("sr.user.redeem." + rc.code.toLowerCase())) {
            return RedeemResult.NO_PERM;
        }
        if (plugin.getConfig().getBoolean("one-redeem-per-player", true) && rc.redeemedPlayers.contains(player.getUniqueId().toString())) {
            return RedeemResult.ALREADY_PLAYER;
        }
        if (rc.limit >= 0 && rc.used >= rc.limit) {
            return RedeemResult.MAX_USES;
        }
        boolean ok = RewardExecutor.deliver(plugin, player, rc, false);
        if (!ok) return RedeemResult.FAIL;
        rc.used++;
        rc.redeemedPlayers.add(player.getUniqueId().toString());
        save();
        return RedeemResult.SUCCESS;
    }

    public Collection<RedeemCode> all() { return codes.values(); }

    public String formatLimit(RedeemCode rc) {
        if (rc.limit < 0) return plugin.msgRaw("uses_unlimited");
        return rc.used + "/" + rc.limit;
    }

    public String randomCode(int len) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random();
        for (int i = 0; i < len; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    public enum RedeemResult { SUCCESS, INVALID, ALREADY_PLAYER, MAX_USES, DISABLED, EXPIRED, NO_PERM, FAIL }

    public static class RedeemCode {
        public String code;
        public int limit = -1;
        public int used = 0;
        public boolean enabled = true;
        public boolean permbased = false;
        public String expiry = null;
        public List<String> rewards = new ArrayList<>();
        public Set<String> redeemedPlayers = new HashSet<>();
    }
}
