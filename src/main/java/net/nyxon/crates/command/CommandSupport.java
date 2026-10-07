// made by nyxon
package net.nyxon.crates.command;

import net.nyxon.crates.NyxonCrates;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class CommandSupport {

    private CommandSupport() {}

    public static void fail(NyxonCrates plugin, CommandSender sender, String key, Map<String, String> values) {
        if (sender instanceof Player player) {
            plugin.messages().send(player, key, values);
        } else {
            sender.sendMessage(plain(plugin, key, values));
        }
    }

    /** ACF manda stringhe, non Component: qui togliamo i tag di MiniMessage. */
    public static String plain(NyxonCrates plugin, String key, Map<String, String> values) {
        return plugin.messages().raw(key, values).replaceAll("<[^>]+>", "");
    }
}
