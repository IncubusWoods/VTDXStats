package dev.incubuswoods.vtdxstats;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

final class ChatListener implements Listener {
    private final VTDXStats plugin;
    ChatListener(VTDXStats plugin) { this.plugin = plugin; }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        VTDXStats.PendingEdit pe = plugin.pendingEdits.get(p.getUniqueId());
        if (pe == null) return;
        e.setCancelled(true);
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> handle(p, pe, msg));
    }

    private void handle(Player p, VTDXStats.PendingEdit pe, String msg) {
        if (msg.equalsIgnoreCase("cancel")) {
            plugin.pendingEdits.remove(p.getUniqueId());
            p.sendMessage(GuiUtil.mm("<yellow>Edici?n cancelada.</yellow>"));
            return;
        }
        try {
            if ("SLOT".equals(pe.type())) {
                int slot = Integer.parseInt(msg);
                if (slot < 0 || slot > 53) { p.sendMessage(GuiUtil.mm("<red>Slot inv?lido. Usa 0-53 o 'cancel'.</red>")); return; }
                // occupancy check for stat slots in repartir
                if ("stat".equals(pe.subType())) {
                    for (String k : plugin.cfg().statOrder) {
                        if (k.equalsIgnoreCase(pe.stat())) continue;
                        int os = plugin.getConfig().getInt("gui.repartir.stat-slots."+k.toLowerCase(), -1);
                        if (os == slot) { p.sendMessage(GuiUtil.mm("<red>Ese slot ya est? ocupado por "+k+".</red>")); return; }
                    }
                    plugin.cfg().setStatSlot(pe.stat(), slot);
                } else if ("attr".equals(pe.subType())) {
                    plugin.cfg().setAttributeSlot(pe.stat(), pe.subId(), slot);
                } else if ("bonus".equals(pe.subType())) {
                    plugin.cfg().setBonusSlot(pe.stat(), pe.subId(), slot);
                }
                plugin.pendingEdits.remove(p.getUniqueId());
                p.sendMessage(GuiUtil.mm("<green>Slot actualizado a <white>"+slot+"</white>. Config recargada.</green>"));
                plugin.guis().openAdminConfig(p, plugin.guis().lastAdminTarget(p.getUniqueId()));
            } else if ("MODEL".equals(pe.type())) {
                String model = msg;
                if (model.equalsIgnoreCase("none") || model.equalsIgnoreCase("clear")) model = "";
                if ("stat".equals(pe.subType())) plugin.cfg().setStatItemModel(pe.stat(), model);
                else if ("attr".equals(pe.subType())) plugin.cfg().setAttributeItemModel(pe.stat(), pe.subId(), model);
                else if ("bonus".equals(pe.subType())) plugin.cfg().setBonusItemModel(pe.stat(), pe.subId(), model);
                plugin.pendingEdits.remove(p.getUniqueId());
                p.sendMessage(GuiUtil.mm("<green>Item model actualizado. Config recargada.</green>"));
                plugin.guis().openAdminConfig(p, plugin.guis().lastAdminTarget(p.getUniqueId()));
            }
        } catch (NumberFormatException ex) {
            p.sendMessage(GuiUtil.mm("<red>N?mero inv?lido. Escribe un slot 0-53 o 'cancel'.</red>"));
        } catch (Exception ex) {
            p.sendMessage(GuiUtil.mm("<red>Error: "+ex.getMessage()+"</red>"));
        }
    }
}
