package com.example.simpleredeem;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AutoRedeemScheduler {
    private final SimpleRedeem plugin;
    private BukkitTask task;
    private final Set<String> triggered = new HashSet<>();

    public AutoRedeemScheduler(SimpleRedeem plugin) { this.plugin = plugin; }

    public void start() {
        stop();
        if (!plugin.getAutoConfig().getBoolean("enabled", false)) return;
        int interval = Math.max(10, plugin.getAutoConfig().getInt("check-interval-seconds", 30));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, interval * 20L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    public void restart() { triggered.clear(); start(); }

    private void tick() {
        if (!plugin.getAutoConfig().getBoolean("enabled", false)) return;
        String tzId = plugin.getAutoConfig().getString("timezone", "Asia/Jakarta");
        ZoneId zone;
        try { zone = ZoneId.of(tzId); } catch (Exception e) { zone = ZoneId.of("UTC"); }
        ZonedDateTime now = ZonedDateTime.now(zone);
        String currentDate = now.toLocalDate().toString();
        int nowHour = now.getHour();
        int nowMinute = now.getMinute();
        DayOfWeek dow = now.getDayOfWeek();

        List<Map<?, ?>> schedules = plugin.getAutoConfig().getMapList("schedules");
        for (int i = 0; i < schedules.size(); i++) {
            Map<String,Object> sch = (Map<String,Object>) (Map<?,?>) schedules.get(i);
            if (!asBool(sch.get("enabled"), false)) continue;
            Map<String,Object> schedNode = (Map<String,Object>) sch.get("schedule");
            if (schedNode == null) continue;
            Object _t = schedNode.get("type");
            String type = (_t == null ? "weekly" : String.valueOf(_t)).toLowerCase();
            Object _time = schedNode.get("time");
            String timeStr = (_time == null ? "00:00" : String.valueOf(_time));
            String[] tp = timeStr.split(":");
            if (tp.length < 2) continue;
            int hour, minute;
            try { hour = Integer.parseInt(tp[0]); minute = Integer.parseInt(tp[1]); } catch (Exception e) { continue; }
            if (hour != nowHour || minute != nowMinute) continue;

            if (type.equals("weekly")) {
                Object _day = schedNode.get("day-of-week");
                String day = (_day == null ? "" : String.valueOf(_day)).toUpperCase();
                if (day.isEmpty() || !day.equals(dow.name())) continue;
            } else if (type.equals("once")) {
                Object _date = schedNode.get("date");
                String dateStr = (_date == null ? "" : String.valueOf(_date));
                try {
                    DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                    LocalDate d = LocalDate.parse(dateStr, f);
                    if (!d.toString().equals(currentDate)) continue;
                } catch (Exception e) { continue; }
            } else if (type.equals("daily")) {
                // ok
            } else {
                continue;
            }

            String key = i + "|" + currentDate + "|" + timeStr;
            if (triggered.contains(key)) continue;
            triggered.add(key);
            triggerSchedule(i, sch, now);
        }
        // cleanup old triggered keys (keep recent)
        if (triggered.size() > 1000) triggered.clear();
    }

    private void triggerSchedule(int index, Map<?, ?> sch, ZonedDateTime now) {
        Object _code = sch.get("code");
        String codeRaw = (_code == null ? "" : String.valueOf(_code));
        CodeManager cm = plugin.getCodeManager();
        String code = codeRaw;
        // if integer, generate random code
        try {
            int len = Integer.parseInt(codeRaw);
            if (len > 0) code = cm.randomCode(Math.min(len, plugin.getConfig().getInt("max-code-length", 32)));
        } catch (NumberFormatException ignored) {}

        int uses = -1;
        Object uo = sch.get("uses");
        if (uo != null) {
            try { uses = Integer.parseInt(String.valueOf(uo)); } catch (Exception ignored) { uses = -1; }
        }
        if (asBool(sch.get("unlimited-uses"), false)) uses = -1;

        boolean permbased = asBool(sch.get("permbased"), false);
        boolean enabled = true;

        Object _exp = sch.get("expiry");
        String expiry = (_exp == null ? "none" : String.valueOf(_exp));
        Object eh = sch.get("expiry-after-hours");
        if (eh != null) {
            try {
                int hours = Integer.parseInt(String.valueOf(eh));
                if (hours > 0) {
                    ZonedDateTime exp = now.plusHours(hours);
                    expiry = exp.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                }
            } catch (Exception ignored) {}
        }

        List<String> rewards = new ArrayList<>();
        Object ro = sch.get("rewards");
        if (ro instanceof List) {
            for (Object o : (List<?>) ro) rewards.add(String.valueOf(o));
        }

        cm.renew(code, uses, enabled, permbased, expiry, rewards);

        // webhook
        boolean whEnabled = false;
        String url = "";
        String title = "Redeem Code Released!";
        String desc = "New code: **{code}**\nUses: {uses}\nExpiry: {expiry}";
        String username = plugin.getAutoConfig().getString("webhook.username", "SimpleRedeem");

        Map<String,Object> schWh = (Map<String,Object>) sch.get("webhook");
        if (schWh != null) {
            whEnabled = asBool(schWh.get("enabled"), false);
            if (schWh.get("url") != null) url = String.valueOf(schWh.get("url"));
            if (schWh.get("title") != null) title = String.valueOf(schWh.get("title"));
            if (schWh.get("description") != null) desc = String.valueOf(schWh.get("description"));
        }
        // global defaults
        if (url.isEmpty()) url = plugin.getAutoConfig().getString("webhook.url", "");
        boolean globalWh = plugin.getAutoConfig().getBoolean("webhook.enabled", false);
        if (!whEnabled && globalWh) whEnabled = true;

        if (whEnabled && !url.trim().isEmpty()) {
            String finalCode = code;
            String finalUses = (uses < 0) ? plugin.msgRaw("uses_unlimited") : String.valueOf(uses);
            String finalExpiry = (expiry == null || expiry.equalsIgnoreCase("none")) ? plugin.msgRaw("uses_unlimited") : expiry;
            String finalTitle = title.replace("{code}", finalCode).replace("{uses}", finalUses).replace("{expiry}", finalExpiry);
            String finalDesc = desc.replace("{code}", finalCode).replace("{uses}", finalUses).replace("{expiry}", finalExpiry);
            sendWebhook(url, username, finalTitle, finalDesc);
        }

        plugin.getLogger().info("Auto-redeem created code: " + code);
    }

    private void sendWebhook(String url, String username, String title, String desc) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json");
                con.setDoOutput(true);
                String json = "{\"username\":\"" + esc(username) + "\",\"embeds\":[{\"title\":\"" + esc(title) + "\",\"description\":\"" + esc(desc) + "\"}]}";
                try (OutputStream os = con.getOutputStream()) {
                    os.write(json.getBytes("UTF-8"));
                }
                con.getResponseCode();
                con.disconnect();
            } catch (Exception e) {
                plugin.getLogger().warning("Webhook failed: " + e.getMessage());
            }
        });
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private boolean asBool(Object o, boolean def) {
        if (o == null) return def;
        String s = String.valueOf(o).trim().toLowerCase();
        if (s.equals("true") || s.equals("yes") || s.equals("1")) return true;
        if (s.equals("false") || s.equals("no") || s.equals("0")) return false;
        return def;
    }
}
