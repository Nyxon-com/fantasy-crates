// made by nyxon
package net.nyxon.crates.item;

public enum ItemProvider {
    VANILLA,
    MMOITEMS,
    ITEMSADDER,
    NEXO;

    public static ItemProvider of(String raw) {
        if (raw == null) return VANILLA;
        return switch (raw.toUpperCase(java.util.Locale.ROOT)) {
            case "MMOITEMS"   -> MMOITEMS;
            case "ITEMSADDER" -> ITEMSADDER;
            case "NEXO"       -> NEXO;
            default           -> VANILLA;
        };
    }
}
