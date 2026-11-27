package dev.incubuswoods.vtdxstats;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

final class VTDXStatsCommand implements TabExecutor {
    private final VTDXStats plugin;
    VTDXStatsCommand(VTDXStats plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Usa /vtdxstats reset <player>|reload|admin"); return true; }
            if (!p.hasPermission("vtdxstats.use")) { p.sendMessage(GuiUtil.mm(plugin.msg("messages.no-permission"))); return true; }
            if (!plugin.stats().hasUsableProfile(p.getUniqueId())) { p.sendMessage(GuiUtil.mm(plugin.msg("messages.no-profile"))); return true; }
            plugin.guis().openOverview(p, p.getUniqueId());
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "repartir", "asignar" -> {
                if (!(sender instanceof Player p)) return true;
                if (!p.hasPermission("vtdxstats.use")) { p.sendMessage(GuiUtil.mm(plugin.msg("messages.no-permission"))); return true; }
                if (!plugin.stats().hasUsableProfile(p.getUniqueId())) { p.sendMessage(GuiUtil.mm(plugin.msg("messages.no-profile"))); return true; }
                plugin.guis().openRepartir(p, p.getUniqueId());
            }
            case "stats", "ver" -> {
                if (!(sender instanceof Player p)) return true;
                if (!p.hasPermission("vtdxstats.use")) { p.sendMessage(GuiUtil.mm(plugin.msg("messages.no-permission"))); return true; }
                plugin.guis().openOverview(p, p.getUniqueId());
            }
            case "admin" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("Solo jugadores."); return true; }
                if (!p.hasPermission("vtdxstats.admin")) { p.sendMessage(GuiUtil.mm(plugin.msg("messages.no-permission"))); return true; }
                plugin.guis().openAdminPlayers(p, 0);
            }
            case "reload" -> {
                if (!sender.hasPermission("vtdxstats.admin")) { sender.sendMessage(GuiUtil.mm(plugin.msg("messages.no-permission"))); return true; }
                plugin.reloadAll();
                sender.sendMessage(GuiUtil.mm(plugin.msg("messages.reloaded")));
            }
            case "reset" -> {
                if (args.length < 2) { sender.sendMessage(GuiUtil.mm("<red>Uso: /vtdxstats reset <jugador></red>")); return true; }
                boolean allowed = sender instanceof ConsoleCommandSender || sender.hasPermission("vtdxstats.reset.others") || sender.hasPermission("vtdxstats.admin");
                if (!allowed) { sender.sendMessage(GuiUtil.mm(plugin.msg("messages.no-permission"))); return true; }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                UUID uuid = target.getUniqueId();
                plugin.stats().reset(uuid);
                String name = target.getName() == null ? args[1] : target.getName();
                sender.sendMessage(GuiUtil.mm(plugin.msg("messages.reset-other").replace("{player}", name)));
                if (target.isOnline() && target.getPlayer() != null) target.getPlayer().sendMessage(GuiUtil.mm(plugin.msg("messages.reset-self")));
            }
            default -> sender.sendMessage(GuiUtil.mm("<yellow>/vtdxstats [repartir|admin|reset <player>|reload]</yellow>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(List.of("repartir","stats"));
            if (sender.hasPermission("vtdxstats.admin")) { base.add("admin"); base.add("reload"); }
            if (sender.hasPermission("vtdxstats.reset.others") || sender.hasPermission("vtdxstats.admin")) base.add("reset");
            return filter(base, args[0]);
        }
        if (args.length == 2 && "reset".equalsIgnoreCase(args[0])) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> in, String token) {
        String t = token == null ? "" : token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : in) if (s.toLowerCase(Locale.ROOT).startsWith(t)) out.add(s);
        return out;
    }
}
