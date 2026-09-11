// made by haze
package it.haze.hazecrates;

import it.haze.hazecrates.animation.AnimationSession;
import it.haze.hazecrates.animation.AnimationRegistry;
import it.haze.hazecrates.command.CrateCommand;
import it.haze.hazecrates.command.HcCommand;
import it.haze.hazecrates.config.MessageService;
import it.haze.hazecrates.crate.CrateDisplayService;
import it.haze.hazecrates.crate.CratePlacementService;
import it.haze.hazecrates.crate.CrateRegistry;
import it.haze.hazecrates.crate.CrateWriter;
import it.haze.hazecrates.database.DatabaseService;
import it.haze.hazecrates.gui.GuiListener;
import it.haze.hazecrates.gui.GuiManager;
import it.haze.hazecrates.item.ExternalItemReloadListener;
import it.haze.hazecrates.item.ExternalItemService;
import it.haze.hazecrates.item.NexoItemsLoadedListener;
import it.haze.hazecrates.key.KeyService;
import it.haze.hazecrates.listener.CrateListener;
import it.haze.hazecrates.placeholder.HazeCratesExpansion;
import it.haze.hazecrates.reward.RewardService;
import it.haze.hazecrates.stats.StatsService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class HazeCrates extends JavaPlugin {

    private MessageService        messages;
    private CrateRegistry         crates;
    private DatabaseService       database;
    private StatsService          stats;
    private KeyService            keys;
    private RewardService         rewards;
    private AnimationRegistry     animations;
    private GuiManager            guiManager;
    private CrateWriter           crateWriter;
    private ExternalItemService   externalItems;
    private CratePlacementService placements;
    private CrateDisplayService   display;
    private final java.util.Map<java.util.UUID, AnimationSession> animSessions =
            new java.util.concurrent.ConcurrentHashMap<>();

    private volatile boolean externalPluginsReady = false;
    private volatile boolean itemsAdderPending = false;
    private volatile boolean nexoPending = false;
    private BukkitTask externalRefreshTask;

    @Override
    public void onEnable() {
        ensureDefaultFiles();

        externalItems = new ExternalItemService(this);
        messages      = new MessageService(this);
        crates        = new CrateRegistry(this);
        animations    = new AnimationRegistry(this);
        database      = new DatabaseService(this);
        database.initialize().whenComplete((unused, error) -> {
            if (error != null) getLogger().severe("Database startup failed: " + error.getMessage());
        });

        stats       = new StatsService(database, this);
        keys        = new KeyService(this, database);
        rewards     = new RewardService(this);
        guiManager  = new GuiManager(this);
        crateWriter = new CrateWriter(this);
        placements  = new CratePlacementService(this);
        display     = new CrateDisplayService(this);

        CrateCommand crateCmd = new CrateCommand(this);
        PluginCommand crate = getCommand("crate");
        if (crate != null) { crate.setExecutor(crateCmd); crate.setTabCompleter(crateCmd); }
        PluginCommand openCmd = getCommand("open");
        if (openCmd != null) { openCmd.setExecutor(crateCmd); openCmd.setTabCompleter(crateCmd); }

        HcCommand hcCmd = new HcCommand(this);
        PluginCommand hc = getCommand("hc");
        if (hc != null) { hc.setExecutor(hcCmd); hc.setTabCompleter(hcCmd); }

        it.haze.hazecrates.command.ChiaveCommand chiaveCmd = new it.haze.hazecrates.command.ChiaveCommand(this);
        PluginCommand meCmd = getCommand("chiave");
        if (meCmd != null) { meCmd.setExecutor(chiaveCmd); meCmd.setTabCompleter(chiaveCmd); }

        getServer().getPluginManager().registerEvents(new CrateListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        itemsAdderPending = getServer().getPluginManager().isPluginEnabled("ItemsAdder");
        nexoPending = getServer().getPluginManager().isPluginEnabled("Nexo");

        if (itemsAdderPending) {
            getServer().getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onIaLoad(dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent e) {
                    getServer().getScheduler().runTask(HazeCrates.this, () -> {
                        itemsAdderPending = false;
                        onExternalPackLoaded("ItemsAdder");
                    });
                }
            }, this);
        }
        if (nexoPending) {
            getServer().getPluginManager().registerEvents(new NexoItemsLoadedListener(this), this);
        }

        reloadConfig();
        messages.reload();
        animations.reload();
        crates.reload();

        if (!itemsAdderPending && !nexoPending) {
            externalPluginsReady = true;
            getServer().getScheduler().runTask(this, () -> {
                placements.scanAndRepair();
                display.startAll();
            });
        }

        if (getServer().getPluginManager().isPluginEnabled("MMOItems")) {
            getServer().getPluginManager().registerEvents(new ExternalItemReloadListener(this), this);
        }

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI"))
            new HazeCratesExpansion(this).register();
    }

    @Override
    public void onDisable() {
        it.haze.hazecrates.animation.opening.world.support.TempOpenChest.restoreAll();
        if (display  != null) display.stopAll();
        if (externalRefreshTask != null) {
            externalRefreshTask.cancel();
            externalRefreshTask = null;
        }
        if (database != null) database.close();
    }

    public void reloadPlugin() {
        ensureDefaultFiles();
        reloadConfig();
        messages.reload();
        animations.reload();
        crates.reload();
        startDisplay();
    }

    public void scheduleExternalItemRefresh(String reason, long delayTicks) {
        if (crates == null) return;
        if (externalRefreshTask != null) {
            externalRefreshTask.cancel();
        }
        long delay = Math.max(1L, delayTicks);
        externalRefreshTask = getServer().getScheduler().runTaskLater(this, () -> {
            externalRefreshTask = null;
            externalPluginsReady = true;
            getLogger().info("[HazeCrates] " + reason + " – refreshing crate items.");
            crates.reload();
            startDisplay();
        }, delay);
    }

    public void onNexoItemsLoaded() {
        getServer().getScheduler().runTask(this, () -> {
            nexoPending = false;
            onExternalPackLoaded("Nexo");
        });
    }

    private void onExternalPackLoaded(String source) {
        if (!Bukkit.isPrimaryThread()) {
            getServer().getScheduler().runTask(this, () -> onExternalPackLoaded(source));
            return;
        }
        getLogger().info("[HazeCrates] " + source + " data loaded.");
        crates.reload();
        if (itemsAdderPending || nexoPending) {
            getLogger().info("[HazeCrates] Waiting for remaining item plugins before starting displays.");
            return;
        }
        boolean first = !externalPluginsReady;
        externalPluginsReady = true;
        if (first) {
            placements.scanAndRepair();
            display.startAll();
        } else {
            startDisplay();
        }
    }

    public void startDisplay() {
        if (!externalPluginsReady || display == null) return;
        if (Bukkit.isPrimaryThread()) {
            display.startAll();
            return;
        }
        getServer().getScheduler().runTask(this, () -> display.startAll());
    }

    public boolean externalPluginsReady() { return externalPluginsReady; }

    public void ensureDefaultFiles() {
        saveDefaultConfig();
        saveResourceIfMissing("messages.yml");
        saveResourceIfMissing("animations.yml");
        saveResourceIfMissing("keys.yml");
        java.io.File cratesDir = new java.io.File(getDataFolder(), "crates");
        cratesDir.mkdirs();
        java.io.File exampleCrate = new java.io.File(cratesDir, "example.yml");
        if (!exampleCrate.exists()) saveResource("crates/example.yml", false);
        mergeConfigDefaults();
    }

    private void mergeConfigDefaults() {
        try (java.io.InputStream in = getResource("config.yml")) {
            if (in == null) return;
            org.bukkit.configuration.file.YamlConfiguration bundled =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            boolean changed = false;
            for (String key : bundled.getKeys(true)) {
                if (!getConfig().contains(key)) {
                    getConfig().set(key, bundled.get(key));
                    changed = true;
                }
            }
            if (changed) saveConfig();
        } catch (Exception ignored) {
        }
    }

    private void saveResourceIfMissing(String resourcePath) {
        java.io.File file = new java.io.File(getDataFolder(), resourcePath);
        if (!file.exists()) saveResource(resourcePath, false);
    }

    public MessageService        messages()      { return messages; }
    public CrateRegistry         crates()        { return crates; }
    public DatabaseService       database()      { return database; }
    public StatsService          stats()         { return stats; }
    public KeyService            keys()          { return keys; }
    public RewardService         rewards()       { return rewards; }
    public AnimationRegistry     animations()    { return animations; }
    public GuiManager            guiManager()    { return guiManager; }
    public CrateWriter           crateWriter()   { return crateWriter; }
    public ExternalItemService   externalItems() { return externalItems; }
    public CratePlacementService placements()    { return placements; }
    public CrateDisplayService   display()       { return display; }

    public void startAnimSession(java.util.UUID uuid, AnimationSession session) {
        animSessions.put(uuid, session);
    }
    public AnimationSession takeAnimSession(java.util.UUID uuid) {
        return animSessions.remove(uuid);
    }
    public boolean hasAnimSession(java.util.UUID uuid) {
        return animSessions.containsKey(uuid);
    }
}
