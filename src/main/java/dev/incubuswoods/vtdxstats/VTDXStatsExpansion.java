package dev.incubuswoods.vtdxstats;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

final class VTDXStatsExpansion extends PlaceholderExpansion {
    private final VTDXStats plugin;
    VTDXStatsExpansion(VTDXStats plugin) { this.plugin = plugin; }
    @Override public @NotNull String getIdentifier() { return "vtdxstats"; }
    @Override public @NotNull String getAuthor() { return "IncubusWoods"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        String p = params.toLowerCase(Locale.ROOT);
        java.util.UUID u = player.getUniqueId();
        return switch (p) {
            case "nivel", "level" -> String.valueOf(plugin.stats().resolveLevel(u));
            case "puntos", "points", "total" -> String.valueOf(plugin.stats().totalPoints(u));
            case "disponibles", "available" -> String.valueOf(plugin.stats().availablePoints(u));
            case "gastados", "spent" -> String.valueOf(plugin.stats().spentPoints(u));
            default -> {
                StatDef def = plugin.cfg().get(p);
                if (def != null) yield String.valueOf(plugin.stats().getPoints(u, def.key()));
                yield null;
            }
        };
    }
}
