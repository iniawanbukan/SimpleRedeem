package com.example.simpleredeem;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RedeemCommand implements CommandExecutor {
    private final SimpleRedeem plugin;

    public RedeemCommand(SimpleRedeem plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.msg("player_only"));
            return true;
        }
        Player p = (Player) sender;
        if (args.length < 1) {
            p.sendMessage(plugin.msg("usage_redeem"));
            return true;
        }
        String code = args[0];
        CodeManager.RedeemResult res = plugin.getCodeManager().redeem(p, code, plugin.isDebugEnabled());
        switch (res) {
            case SUCCESS:
                p.sendMessage(plugin.msg("code_redeemed").replace("%code%", code));
                if (plugin.getConfig().getBoolean("broadcast-on-redeem", false)) {
                    org.bukkit.Bukkit.broadcastMessage(plugin.msg("code_broadcast")
                            .replace("%player%", p.getName()).replace("%code%", code));
                }
                break;
            case INVALID:
                p.sendMessage(plugin.msg("code_invalid"));
                break;
            case ALREADY_PLAYER:
                p.sendMessage(plugin.msg("code_already_used"));
                break;
            case MAX_USES:
                p.sendMessage(plugin.msg("code_max_uses"));
                break;
            case DISABLED:
                p.sendMessage(plugin.msg("code_disabled"));
                break;
            case EXPIRED:
                p.sendMessage(plugin.msg("code_expired"));
                break;
            case NO_PERM:
                p.sendMessage(plugin.msg("code_no_perm"));
                break;
            default:
                p.sendMessage(plugin.msg("code_invalid"));
                break;
        }
        return true;
    }
}