package com.example.simpleredeem;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.text.SimpleDateFormat;
import java.util.*;

public class SrAdminCommand implements CommandExecutor {
    private final SimpleRedeem plugin;
    private final CodeManager cm;

    public SrAdminCommand(SimpleRedeem plugin) {
        this.plugin = plugin;
        this.cm = plugin.getCodeManager();
    }

    private boolean hasPerm(CommandSender s, String sub) {
        if (s.hasPermission("sr.admin")) return true;
        String node = "sr.admin." + sub.toLowerCase();
        return s.hasPermission(node);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length < 1) {
            if (!hasPerm(sender, "help")) { sender.sendMessage(plugin.msg("no_permission")); return true; }
            showHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        if (!hasPerm(sender, sub)) {
            sender.sendMessage(plugin.msg("no_permission"));
            return true;
        }
        switch (sub) {
            case "new": return handleNew(sender, args);
            case "delete": return handleDelete(sender, args);
            case "info": return handleInfo(sender, args);
            case "addcmd": return handleAddCmd(sender, args);
            case "delcmd": return handleDelCmd(sender, args);
            case "addreward": return handleAddReward(sender, args);
            case "uses": return handleUses(sender, args);
            case "bulk": return handleBulk(sender, args);
            case "permbased": return handlePermBased(sender, args);
            case "enabled": return handleEnabled(sender, args);
            case "expiry": return handleExpiry(sender, args);
            case "debug": return handleDebug(sender, args);
            case "help": showHelp(sender); return true;
            case "version": sender.sendMessage(plugin.msg("version_info").replace("%version%", plugin.getDescription().getVersion())); return true;
            case "reload":
                plugin.reloadPlugin();
                sender.sendMessage(plugin.msg("plugin_reloaded"));
                return true;
            case "debuginfo": return handleDebugInfo(sender);
            default:
                showHelp(sender);
                return true;
        }
    }

    private void showHelp(CommandSender s) {
        s.sendMessage(plugin.msg("help_header"));
        String[] lines = new String[]{
                "new [integer/string]", "delete [id]", "info [id]", "addcmd [id] [command]",
                "delcmd [id] [command id]", "uses [id] [integer/string]", "bulk [id] [copies]",
                "permbased [id] [boolean]", "enabled [id] [boolean]", "expiry [id] [dd/MM/yyyy]",
                "debug", "help", "version", "reload", "debuginfo"
        };
        for (String l : lines) s.sendMessage(plugin.color("&7- &e" +
                "/sr " + l));
    }

    private boolean handleNew(CommandSender s, String[] args) {
        if (args.length < 2) { s.sendMessage(plugin.msg("usage_new")); return true; }
        String arg = args[1];
        int limit = -1;
        // parse optional uses? spec: new [integer|string] only code
        String code;
        try {
            int len = Integer.parseInt(arg);
            if (len <= 0) { s.sendMessage(plugin.msg("usage_new")); return true; }
            code = cm.randomCode(Math.min(len, plugin.getConfig().getInt("max-code-length", 32)));
        } catch (NumberFormatException e) {
            code = arg;
        }
        if (code.length() > plugin.getConfig().getInt("max-code-length", 32)) {
            s.sendMessage(plugin.msg("code_too_long").replace("%limit%", String.valueOf(plugin.getConfig().getInt("max-code-length", 32))));
            return true;
        }
        if (!cm.create(code, limit)) {
            if (cm.exists(code)) s.sendMessage(plugin.msg("code_exists").replace("%code%", code));
            else s.sendMessage(plugin.msg("code_invalid"));
            return true;
        }
        String limitStr = (limit < 0) ? plugin.msgRaw("uses_unlimited") : String.valueOf(limit);
        s.sendMessage(plugin.msg("code_created").replace("%code%", code).replace("%limit%", limitStr));
        return true;
    }

    private boolean handleDelete(CommandSender s, String[] args) {
        if (args.length < 2) { s.sendMessage(plugin.msg("usage_delete")); return true; }
        String code = args[1];
        if (!cm.delete(code)) { s.sendMessage(plugin.msg("code_not_found").replace("%code%", code)); return true; }
        s.sendMessage(plugin.msg("code_deleted").replace("%code%", code));
        return true;
    }

    private boolean handleInfo(CommandSender s, String[] args) {
        if (args.length < 2) { s.sendMessage(plugin.msg("usage_info")); return true; }
        String code = args[1];
        CodeManager.RedeemCode rc = cm.get(code);
        if (rc == null) { s.sendMessage(plugin.msg("code_not_found").replace("%code%", code)); return true; }
        String status = (rc.limit < 0) ? (rc.used + " (" + plugin.msgRaw("uses_remaining_unlimited") + ")") : (rc.used + "/" + rc.limit);
        String expiry = (rc.expiry == null) ? plugin.msgRaw("uses_unlimited") : rc.expiry;
        s.sendMessage(plugin.color(plugin.msgRaw("info_header").replace("%code%", rc.code)));
        s.sendMessage(plugin.color(plugin.msgRaw("info_enabled").replace("%value%", String.valueOf(rc.enabled))));
        s.sendMessage(plugin.color(plugin.msgRaw("info_permbased").replace("%value%", String.valueOf(rc.permbased))));
        s.sendMessage(plugin.color(plugin.msgRaw("info_expiry").replace("%value%", expiry)));
        s.sendMessage(plugin.color(plugin.msgRaw("info_uses").replace("%status%", status)));
        s.sendMessage(plugin.color(plugin.msgRaw("info_commands")));
        // list command rewards only
        List<String> cmds = new ArrayList<>();
        for (String r : rc.rewards) if (r.toLowerCase().startsWith("cmd:") || r.toLowerCase().startsWith("command:")) cmds.add(r.substring(r.indexOf(':') + 1));
        if (cmds.isEmpty()) s.sendMessage(plugin.color(plugin.msgRaw("info_none")));
        else {
            for (int i = 0; i < cmds.size(); i++) {
                String line = plugin.msgRaw("info_command_item").replace("%index%", String.valueOf(i + 1)).replace("%command%", cmds.get(i));
                s.sendMessage(plugin.color(line));
            }
        }
        return true;
    }

    private boolean handleAddCmd(CommandSender s, String[] args) {
        if (args.length < 3) { s.sendMessage(plugin.msg("usage_addcmd")); return true; }
        String code = args[1];
        if (!cm.exists(code)) { s.sendMessage(plugin.msg("code_not_found").replace("%code%", code)); return true; }
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) sb.append(' ');
            sb.append(args[i]);
        }
        String cmd = sb.toString();
        CodeManager.RedeemCode rc = cm.get(code);
        int before = rc.rewards.size();
        cm.addReward(code, "cmd:" + cmd);
        int idx = before + 1;
        // index among commands? use reward index returned as size
        s.sendMessage(plugin.msg("cmd_added").replace("%code%", code).replace("%index%", String.valueOf(idx)));
        return true;
    }

    private boolean handleDelCmd(CommandSender s, String[] args) {
        if (args.length < 3) { s.sendMessage(plugin.msg("usage_delcmd")); return true; }
        String code = args[1];
        CodeManager.RedeemCode rc = cm.get(code);
        if (rc == null) { s.sendMessage(plugin.msg("code_not_found").replace("%code%", code)); return true; }
        int idx;
        try { idx = Integer.parseInt(args[2]); } catch (NumberFormatException e) { s.sendMessage(plugin.msg("cmd_invalid")); return true; }
        // map command index to reward list index
        List<Integer> cmdPositions = new ArrayList<>();
        for (int i = 0; i < rc.rewards.size(); i++) {
            String r = rc.rewards.get(i).toLowerCase();
            if (r.startsWith("cmd:") || r.startsWith("command:")) cmdPositions.add(i);
        }
        if (idx < 1 || idx > cmdPositions.size()) { s.sendMessage(plugin.msg("cmd_invalid")); return true; }
        int pos = cmdPositions.get(idx - 1);
        if (!cm.removeReward(code, pos)) { s.sendMessage(plugin.msg("cmd_invalid")); return true; }
        s.sendMessage(plugin.msg("cmd_removed").replace("%code%", code).replace("%index%", String.valueOf(idx)));
        return true;
    }

    private boolean handleAddReward(CommandSender s, String[] args) {
        // usage: addreward <code> item/cmd/money ...
        if (args.length < 4) { s.sendMessage(plugin.msg("usage_addreward") != null ? plugin.msg("usage_addreward") : plugin.msg("usage_admin")); return true; }
        // reuse previous style
        String code = args[1];
        if (!cm.exists(code)) { s.sendMessage(plugin.msg("code_not_found").replace("%code%", code)); return true; }
        String type = args[2].toLowerCase();
        String reward;
        if (type.equals("item")) {
            if (args.length < 5) return true;
            String mat = args[3].toUpperCase();
            int amt = 1;
            try { if (args.length >= 5) amt = Integer.parseInt(args[4]); } catch (NumberFormatException ignored) {}
            reward = "item:" + mat + ":" + amt;
        } else if (type.equals("money")) {
            reward = "money:" + args[3];
        } else if (type.equals("cmd") || type.equals("command")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 3; i < args.length; i++) {
                if (i > 3) sb.append(' ');
                sb.append(args[i]);
            }
            reward = "cmd:" + sb.toString();
        } else {
            s.sendMessage(plugin.msg("usage_admin"));
            return true;
        }
        cm.addReward(code, reward);
        s.sendMessage(plugin.msg("reward_added") != null ? plugin.msg("reward_added").replace("%code%", code).replace("%reward%", reward) : plugin.msg("cmd_added").replace("%code%", code).replace("%index%", reward));
        return true;
    }

    private boolean handleUses(CommandSender s, String[] args) {
        if (args.length < 3) { s.sendMessage(plugin.msg("usage_uses")); return true; }
        String code = args[1];
        if (!cm.exists(code)) { s.sendMessage(plugin.msg("code_not_found").replace("%code%", code)); return true; }
        String u = args[2].toLowerCase();
        int limit;
        if (u.equals("infinite") || u.equals("unlimited") || u.equals("inf") || u.equals("-1")) limit = -1;
        else {
            try { limit = Integer.parseInt(u); } catch (NumberFormatException e) { s.sendMessage(plugin.msg("usage_uses")); return true; }
            if (limit < -1) limit = -1;
        }
        cm.setUses(code, limit);
        String limitStr = (limit < 0) ? plugin.msgRaw("uses_unlimited") : String.valueOf(limit);
        s.sendMessage(plugin.msg("uses_set").replace("%code%", code).replace("%limit%", limitStr));
        return true;
    }

    private boolean handleBulk(CommandSender s, String[] args) {
        if (args.length < 3) { s.sendMessage(plugin.msg("usage_bulk")); return true; }
        String source = args[1];
        CodeManager.RedeemCode src = cm.get(source);
        if (src == null) { s.sendMessage(plugin.msg("code_not_found").replace("%code%", source)); return true; }
        int copies;
        try { copies = Integer.parseInt(args[2]); } catch (NumberFormatException e) { s.sendMessage(plugin.msg("usage_bulk")); return true; }
        if (copies <= 0) { s.sendMessage(plugin.msg("usage_bulk")); return true; }
        int count = 0;
        for (int i = 0; i < copies; i++) {
            // generate unique code same length
            String newCode = source;
            int tries = 0;
            while (cm.exists(newCode) && tries < 50) {
                newCode = cm.randomCode(Math.max(4, source.length()));
                tries++;
            }
            if (cm.exists(newCode)) break;
            if (!cm.create(newCode, src.limit)) break;
            CodeManager.RedeemCode nr = cm.get(newCode);
            nr.enabled = src.enabled;
            nr.permbased = src.permbased;
            nr.expiry = src.expiry;
            nr.rewards = new ArrayList<>(src.rewards);
            cm.save();
            count++;
        }
        s.sendMessage(plugin.msg("bulk_done").replace("%count%", String.valueOf(count)).replace("%code%", source));
        return true;
    }

    private boolean handlePermBased(CommandSender s, String[] args) {
        if (args.length < 3) { s.sendMessage(plugin.msg("usage_permbased")); return true; }
        String code = args[1];
        if (!cm.exists(code)) { s.sendMessage(plugin.msg("code_not_found").replace("%code%", code)); return true; }
        boolean v = parseBool(args[2]);
        cm.setPermbased(code, v);
        s.sendMessage(plugin.msg("permbased_set").replace("%code%", code).replace("%value%", String.valueOf(v)));
        return true;
    }

    private boolean handleEnabled(CommandSender s, String[] args) {
        if (args.length < 3) { s.sendMessage(plugin.msg("usage_enabled")); return true; }
        String code = args[1];
        if (!cm.exists(code)) { s.sendMessage(plugin.msg("code_not_found").replace("%code%", code)); return true; }
        boolean v = parseBool(args[2]);
        cm.setEnabled(code, v);
        s.sendMessage(plugin.msg("enabled_set").replace("%code%", code).replace("%value%", String.valueOf(v)));
        return true;
    }

    private boolean handleExpiry(CommandSender s, String[] args) {
        if (args.length < 2) { s.sendMessage(plugin.msg("usage_expiry")); return true; }
        String code = args[1];
        if (!cm.exists(code)) { s.sendMessage(plugin.msg("code_not_found").replace("%code%", code)); return true; }
        if (args.length < 3 || args[2].equalsIgnoreCase("none") || args[2].equalsIgnoreCase("clear")) {
            cm.setExpiry(code, null);
            s.sendMessage(plugin.msg("expiry_cleared").replace("%code%", code));
            return true;
        }
        String val = args[2];
        // validate
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
            sdf.setLenient(false);
            sdf.parse(val);
        } catch (Exception e) {
            s.sendMessage(plugin.msg("usage_expiry"));
            return true;
        }
        cm.setExpiry(code, val);
        s.sendMessage(plugin.msg("expiry_set").replace("%code%", code).replace("%value%", val));
        return true;
    }

    private boolean handleDebug(CommandSender s, String[] args) {
        boolean cur = plugin.isDebugEnabled();
        plugin.setDebugEnabled(!cur);
        plugin.getConfig().set("debug-mode", plugin.isDebugEnabled());
        plugin.saveConfig();
        s.sendMessage(plugin.msg("debug_toggled").replace("%value%", String.valueOf(plugin.isDebugEnabled())));
        return true;
    }

    private boolean handleDebugInfo(CommandSender s) {
        s.sendMessage(plugin.color("&eDebug mode: &f" + plugin.isDebugEnabled()));
        s.sendMessage(plugin.color("&eCodes loaded: &f" + cm.all().size()));
        return true;
    }

    private boolean parseBool(String s) {
        return s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes") || s.equals("1");
    }
}
