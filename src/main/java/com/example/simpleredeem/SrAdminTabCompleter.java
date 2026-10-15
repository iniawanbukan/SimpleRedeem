package com.example.simpleredeem;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SrAdminTabCompleter implements TabCompleter {
    private final SimpleRedeem plugin;

    public SrAdminTabCompleter(SimpleRedeem plugin) { this.plugin = plugin; }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("sr.admin")) {
            // allow specific? keep simple
            boolean any = false;
            for (String s : new String[]{"sr.admin.create","sr.admin.delete","sr.admin.info","sr.admin.addcmd","sr.admin.delcmd","sr.admin.uses","sr.admin.bulk","sr.admin.permbased","sr.admin.enabled","sr.admin.expiry","sr.admin.debug","sr.admin.help","sr.admin.reload","sr.admin.version","sr.admin.debuginfo"}) {
                if (sender.hasPermission(s)) { any = true; break; }
            }
            if (!any) return null;
        }
        if (args.length == 1) {
            return filter(Arrays.asList("new","delete","info","addcmd","delcmd","uses","bulk","permbased","enabled","expiry","debug","help","version","reload","debuginfo"), args[0]);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("new") && !args[0].equalsIgnoreCase("debug") && !args[0].equalsIgnoreCase("help") && !args[0].equalsIgnoreCase("version") && !args[0].equalsIgnoreCase("reload") && !args[0].equalsIgnoreCase("debuginfo")) {
            return filter(new ArrayList<>(plugin.getCodeManager().all().stream().map(c -> c.code).collect(Collectors.toList())), args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("permbased") || args[0].equalsIgnoreCase("enabled"))) {
            return filter(Arrays.asList("true","false"), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("uses")) {
            return filter(Arrays.asList("infinite"), args[2]);
        }
        return null;
    }

    private List<String> filter(List<String> list, String prefix) {
        String p = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : list) if (s.toLowerCase().startsWith(p)) out.add(s);
        return out;
    }
}
