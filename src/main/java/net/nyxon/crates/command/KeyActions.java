// made by nyxon
package net.nyxon.crates.command;

import net.nyxon.crates.NyxonCrates;
import net.nyxon.crates.crate.CrateDefinition;
import net.nyxon.crates.crate.KeyType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/** Consegna, rimozione e saldo delle chiavi: i comandi qui arrivano con gli argomenti gia' risolti. */
public final class KeyActions {

    private final NyxonCrates plugin;

    public KeyActions(NyxonCrates plugin) {
        this.plugin = plugin;
    }

    public void give(CommandSender sender, Player target, CrateDefinition crate, int amount) {
        boolean virtual = crate.keyType() == KeyType.VIRTUAL;
        if (virtual) {
            plugin.keys().addVirtual(target.getUniqueId(), crate.id(), amount);
            plugin.keys().play(target, crate.id(), "given");
        } else {
            plugin.keys().givePhysical(target, crate, amount);
        }
        String type = virtual ? "virtuale" : "fisica";
        plugin.messages().send(target, virtual ? "key-given-virtual" : "key-given-physical",
                placeholders(target, crate, amount, type));
        if (sender instanceof Player admin && !admin.equals(target)) {
            plugin.messages().send(admin, "key-given-admin", placeholders(target, crate, amount, type));
        } else if (!(sender instanceof Player)) {
            sender.sendMessage("Date " + amount + " chiavi " + type + " " + crate.id() + " a " + target.getName());
        }
    }

    public void take(CommandSender sender, Player target, CrateDefinition crate, int amount) {
        int removedItems = plugin.keys().takePhysical(target, crate.id(), amount);
        plugin.keys().takeVirtual(target.getUniqueId(), crate.id(), Math.max(0, amount - removedItems));
        plugin.keys().play(target, crate.id(), "taken");
        notify(sender, target, crate, amount, "key-taken", "key-taken-admin", "miste");
    }

    public void set(CommandSender sender, Player target, CrateDefinition crate, int amount) {
        plugin.keys().setVirtual(target.getUniqueId(), crate.id(), amount);
        plugin.keys().play(target, crate.id(), "given");
        notify(sender, target, crate, amount, "key-set", "key-set-admin", "virtuale");
    }

    public void giveLootbox(CommandSender sender, Player target, CrateDefinition crate, int amount) {
        plugin.keys().givePhysical(target, crate, amount);
        plugin.messages().send(target, "item-given", Map.of("crate", crate.displayName()));
        if (sender instanceof Player admin && !admin.equals(target)) {
            plugin.messages().send(admin, "item-given", Map.of("crate", crate.displayName()));
        }
    }

    public void list(Player player) {
        plugin.keys().allVirtual(player.getUniqueId()).thenAccept(virtuals ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.messages().send(player, "keys-header");
                    boolean any = false;
                    for (CrateDefinition crate : plugin.crates().all()) {
                        int physical = plugin.keys().countPhysical(player, crate.id());
                        int virtual = virtuals.getOrDefault(crate.id(), 0);
                        if (physical <= 0 && virtual <= 0) continue;
                        any = true;
                        plugin.messages().send(player, "keys-line", Map.of(
                                "crate", crate.displayName(),
                                "id", crate.id(),
                                "physical", String.valueOf(physical),
                                "virtual", String.valueOf(virtual)
                        ));
                    }
                    if (!any) {
                        plugin.messages().send(player, "keys-empty", Map.of("player", player.getName()));
                    }
                    plugin.messages().send(player, "keys-hint");
                }));
    }

    public void show(Player player, CrateDefinition crate) {
        plugin.keys().virtual(player.getUniqueId(), crate.id()).thenAccept(virtual ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    int physical = plugin.keys().countPhysical(player, crate.id());
                    plugin.messages().send(player, "keys-line", Map.of(
                            "crate", crate.displayName(),
                            "id", crate.id(),
                            "physical", String.valueOf(physical),
                            "virtual", String.valueOf(virtual)
                    ));
                }));
    }

    private void notify(CommandSender sender, Player target, CrateDefinition crate, int amount,
                        String targetKey, String adminKey, String type) {
        Map<String, String> values = placeholders(target, crate, amount, type);
        plugin.messages().send(target, targetKey, values);
        if (sender instanceof Player admin && !admin.equals(target)) {
            plugin.messages().send(admin, adminKey, values);
        } else if (!(sender instanceof Player)) {
            sender.sendMessage(crate.id() + " x" + amount + " -> " + target.getName());
        }
    }

    private static Map<String, String> placeholders(Player target, CrateDefinition crate, int amount, String type) {
        return Map.of(
                "crate", crate.displayName(),
                "amount", String.valueOf(amount),
                "player", target.getName(),
                "type", type
        );
    }
}
