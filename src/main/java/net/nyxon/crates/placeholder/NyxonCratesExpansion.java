// made by nyxon
package net.nyxon.crates.placeholder;

import net.nyxon.crates.NyxonCrates;
import net.nyxon.crates.stats.LeaderboardEntry;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.List;

public final class NyxonCratesExpansion extends PlaceholderExpansion {

    private final NyxonCrates plugin;

    public NyxonCratesExpansion(NyxonCrates plugin) { this.plugin = plugin; }

    @Override public String getIdentifier() { return "nyxoncrates"; }
    @Override public String getAuthor()     { return "nyxon"; }
    @Override public String getVersion()    { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist()      { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "0";
        String[] parts = params.split("_");

        if (parts.length == 2 && parts[0].equalsIgnoreCase("opened"))
            return String.valueOf(plugin.stats().opened(player.getUniqueId(), parts[1]).getNow(0));

        if (parts.length == 2 && parts[0].equalsIgnoreCase("keys"))
            return String.valueOf(plugin.keys().virtual(player.getUniqueId(), parts[1]).getNow(0));

        if (parts.length == 3 && parts[0].equalsIgnoreCase("milestone")) {
            var crate = plugin.crates().get(parts[1]);
            if (crate == null) return "";
            int opened = plugin.stats().opened(player.getUniqueId(), crate.id()).getNow(0);
            for (var milestone : crate.milestones()) {
                if (!milestone.id().equalsIgnoreCase(parts[2])) continue;
                if (opened >= milestone.openingsRequired() && !milestone.resetAfterClaim()) {
                    return "Completato";
                }
                return opened + "/" + milestone.openingsRequired();
            }
            return "";
        }

        if ((parts.length == 4 || parts.length == 5) && parts[0].equalsIgnoreCase("leaderboard")) {
            try {
                String crateId = parts[1];
                int page  = Integer.parseInt(parts[2]);
                int row   = Integer.parseInt(parts[3]);
                String field = parts.length == 5 ? parts[4].toLowerCase() : "both";
                int idx = (page - 1) * 10 + (row - 1);
                List<LeaderboardEntry> board = plugin.stats().leaderboard(crateId).getNow(List.of());
                if (idx < 0 || idx >= board.size()) return "";
                LeaderboardEntry e = board.get(idx);
                if (field.equals("name")) return e.name();
                if (field.equals("amount")) return String.valueOf(e.amount());
                return e.name() + " - " + e.amount();
            } catch (NumberFormatException ignored) { return ""; }
        }
        return null;
    }
}
