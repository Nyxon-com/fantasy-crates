// made by haze
package it.haze.hazecrates.config;

import it.haze.hazecrates.HazeCrates;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.Map;

public final class MessageService {

    private final HazeCrates plugin;
    private YamlConfiguration config;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public MessageService(HazeCrates plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        try (java.io.InputStream defStream = plugin.getResource("messages.yml")) {
            if (defStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(defStream, java.nio.charset.StandardCharsets.UTF_8));
                config.setDefaults(defaults);
                config.options().copyDefaults(true);
            }
        } catch (Exception ignored) {}
    }

    public Component component(String key, Map<String, String> values) {
        String raw = config.getString(key, "<red>Missing message: " + key + "</red>");
        return parse(raw, values);
    }

    public Component parse(String raw, Map<String, String> values) {
        if (raw == null || raw.isBlank()) return Component.empty();

        for (Map.Entry<String, String> e : values.entrySet()) {
            String val = e.getValue() != null ? e.getValue() : "";
            raw = raw.replace("%" + e.getKey() + "%", val);
        }

        String converted = legacyToMini(raw);
        try {
            return MM.deserialize(converted);
        } catch (Exception e) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
        }
    }

    public Component parse(String raw) {
        return parse(raw, Map.of());
    }

    public Component prefix() {
        return component("prefix", Map.of());
    }

    public String raw(String key, Map<String, String> values) {
        String raw = config.getString(key, "");
        for (Map.Entry<String, String> e : values.entrySet()) {
            raw = raw.replace("%" + e.getKey() + "%", e.getValue());
        }
        return raw;
    }

    public String raw(String key) { return raw(key, Map.of()); }

    public List<String> rawList(String key, Map<String, String> values) {
        List<String> list = config.getStringList(key);
        return list.stream()
                .map(line -> {
                    String l = line;
                    for (Map.Entry<String, String> e : values.entrySet()) {
                        l = l.replace("%" + e.getKey() + "%", e.getValue());
                    }
                    return l;
                })
                .toList();
    }

    public List<String> rawList(String key) { return rawList(key, Map.of()); }

    public void send(Player player, String key) {
        send(player, key, Map.of());
    }

    public void send(Player player, String key, Map<String, String> values) {
        Component prefix = prefix();
        Component body   = component(key, values);
        player.sendMessage(prefix.append(body));
    }

    public void sendRaw(Player player, String text, Map<String, String> values) {
        player.sendMessage(prefix().append(parse(text, values)));
    }

    @Deprecated
    public Component componentText(String text) {
        return parse(text, Map.of());
    }

    public static String serialize(Component component) {
        if (component == null) return "";
        return MM.serialize(component.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,
                net.kyori.adventure.text.format.TextDecoration.State.NOT_SET));
    }

    public static boolean isBrokenLegacyHex(String text) {
        if (text == null || text.isBlank()) return false;
        String s = text.replace('\u00A7', '&');
        if (!s.toLowerCase(java.util.Locale.ROOT).contains("&x")) return false;
        String withoutValid = HEX_NIBBLES.matcher(s).replaceAll("");
        return withoutValid.toLowerCase(java.util.Locale.ROOT).contains("&x");
    }

    private static final java.util.regex.Pattern HEX_SHORT =
            java.util.regex.Pattern.compile("(?i)&#([0-9a-f]{6})");
    private static final java.util.regex.Pattern HEX_NIBBLES =
            java.util.regex.Pattern.compile("(?i)&x(?:&([0-9a-f])){6}");

    public static String legacyToMini(String text) {
        if (text == null || text.isEmpty()) return "";
        String s = text.replace('\u00A7', '&');

        s = HEX_SHORT.matcher(s).replaceAll("<#$1>");

        java.util.regex.Matcher nibble = HEX_NIBBLES.matcher(s);
        StringBuilder hexed = new StringBuilder();
        int last = 0;
        while (nibble.find()) {
            hexed.append(s, last, nibble.start());
            String hex = nibble.group().replaceAll("(?i)&x|&", "");
            hexed.append("<#").append(hex).append(">");
            last = nibble.end();
        }
        hexed.append(s.substring(last));
        s = hexed.toString();

        s = s.replaceAll("(?i)&x", "");

        s = replaceCode(s, '0', "<black>");
        s = replaceCode(s, '1', "<dark_blue>");
        s = replaceCode(s, '2', "<dark_green>");
        s = replaceCode(s, '3', "<dark_aqua>");
        s = replaceCode(s, '4', "<dark_red>");
        s = replaceCode(s, '5', "<dark_purple>");
        s = replaceCode(s, '6', "<gold>");
        s = replaceCode(s, '7', "<gray>");
        s = replaceCode(s, '8', "<dark_gray>");
        s = replaceCode(s, '9', "<blue>");
        s = replaceCode(s, 'a', "<green>");
        s = replaceCode(s, 'b', "<aqua>");
        s = replaceCode(s, 'c', "<red>");
        s = replaceCode(s, 'd', "<light_purple>");
        s = replaceCode(s, 'e', "<yellow>");
        s = replaceCode(s, 'f', "<white>");
        s = replaceCode(s, 'k', "<obfuscated>");
        s = replaceCode(s, 'l', "<bold>");
        s = replaceCode(s, 'm', "<strikethrough>");
        s = replaceCode(s, 'n', "<underlined>");
        s = replaceCode(s, 'o', "<italic>");
        s = replaceCode(s, 'r', "<reset>");
        return s;
    }

    private static String replaceCode(String text, char code, String mini) {
        return text.replace("&" + code, mini).replace("&" + Character.toUpperCase(code), mini);
    }
}
