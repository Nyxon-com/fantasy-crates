package it.haze.hazecrates.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class MiniMessageService {

    private static final MiniMessageService SHARED = new MiniMessageService();
    private static final Component EMPTY = Component.empty().decoration(TextDecoration.ITALIC, false);

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Cache<String, Component> components = Caffeine.newBuilder()
            .maximumSize(4096)
            .build();

    private MiniMessageService() {}

    public static MiniMessageService shared() {
        return SHARED;
    }

    public Component parse(String raw) {
        if (raw == null || raw.isBlank()) return EMPTY;
        return components.get(raw, this::parseUncached);
    }

    private Component parseUncached(String raw) {
        try {
            return EMPTY
                    .append(miniMessage.deserialize(MessageService.legacyToMini(raw)));
        } catch (Exception e) {
            return EMPTY
                    .append(LegacyComponentSerializer.legacyAmpersand().deserialize(raw));
        }
    }

    public String serialize(Component component) {
        if (component == null) return "";
        return miniMessage.serialize(component.decoration(TextDecoration.ITALIC,
                TextDecoration.State.NOT_SET));
    }

    public void clear() {
        components.invalidateAll();
    }
}
