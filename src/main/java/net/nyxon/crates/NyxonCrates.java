// made by nyxon
package net.nyxon.crates;

import net.nyxon.crates.animation.AnimationSession;
import net.nyxon.crates.animation.AnimationRegistry;
import net.nyxon.crates.command.CommandRegistrar;
import net.nyxon.crates.config.MessageService;
import net.nyxon.crates.crate.CrateDisplayService;
import net.nyxon.crates.crate.CratePlacementService;
import net.nyxon.crates.crate.CrateRegistry;
import net.nyxon.crates.crate.CrateWriter;
import net.nyxon.crates.database.DatabaseService;
import net.nyxon.crates.database.PlayerDataStore;
import net.nyxon.crates.gui.GuiListener;
import net.nyxon.crates.gui.GuiManager;
import net.nyxon.crates.item.ExternalItemReloadListener;
import net.nyxon.crates.item.ExternalItemService;
import net.nyxon.crates.item.NexoItemsLoadedListener;
import net.nyxon.crates.key.KeyService;
import net.nyxon.crates.listener.CrateListener;
import net.nyxon.crates.listener.PlayerDataListener;
import net.nyxon.crates.placeholder.NyxonCratesExpansion;
import net.nyxon.crates.reward.RewardService;
import net.nyxon.crates.stats.StatsService;
import net.nyxon.crates.util.AsyncWorkService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class NyxonCrates extends JavaPlugin {

    // Migrazione una tantum: riusa la cartella dati del vecchio plugin "HazeCrates".
    @Override
    public void onLoad() {
        java.io.File target = getDataFolder();
        java.io.File legacy = new java.io.File(target.getParentFile(), "HazeCrates");
        if (!target.exists() && legacy.isDirectory()) {
            if (legacy.renameTo(target)) {
                getLogger().info("Migrata cartella dati " + legacy.getName() + " -> " + target.getName());
            } else {
                getLogger().warning("Impossibile rinominare " + legacy.getPath() + ": spostala a mano in " + target.getPath());
            }
        }
    }

    private MessageService        messages;
    private CrateRegistry         crates;
    private DatabaseService       database;
    private AsyncWorkService      asyncWork;
    private PlayerDataStore       playerData;
    private StatsService          stats;
    private KeyService            keys;
    private RewardService         rewards;
    private AnimationRegistry     animations;
    private GuiManager            guiManager;
    private CrateWriter           crateWriter;
    private ExternalItemService   externalItems;
    private CratePlacementService placements;
    private CrateDisplayService   display;
    private CommandRegistrar      commands;
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
        asyncWork     = new AsyncWorkService(
                getConfig().getInt("database-async-workers", 2),
                getConfig().getInt("async-max-pending-actions", 256), getLogger());
        database      = new DatabaseService(this, asyncWork);
        playerData    = new PlayerDataStore(this, database, asyncWork);
        database.initialize().whenComplete((unused, error) -> {
            if (error != null) getLogger().severe("Database startup failed: " + error.getMessage());
            if (isEnabled()) getServer().getScheduler().runTask(this, playerData::start);
        });

        stats       = new StatsService(database, this);
        keys        = new KeyService(this, database);
        rewards     = new RewardService(this);
        guiManager  = new GuiManager(this);
        crateWriter = new CrateWriter(this);
        placements  = new CratePlacementService(this, asyncWork);
        display     = new CrateDisplayService(this);

        commands = new CommandRegistrar(this);
        commands.register();

        getServer().getPluginManager().registerEvents(new CrateListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDataListener(this), this);

        itemsAdderPending = getServer().getPluginManager().isPluginEnabled("ItemsAdder");
        nexoPending = getServer().getPluginManager().isPluginEnabled("Nexo");

        if (itemsAdderPending) {
            getServer().getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onIaLoad(dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent e) {
                    getServer().getScheduler().runTask(NyxonCrates.this, () -> {
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
        keys.reload();

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
            new NyxonCratesExpansion(this).register();
    }

    @Override
    public void onDisable() {
        if (commands != null) commands.unregister();
        net.nyxon.crates.animation.opening.world.support.OpeningProps.clearAll();
        net.nyxon.crates.animation.opening.world.support.TempOpenChest.restoreAll();
        if (rewards != null) rewards.shutdown();
        if (display  != null) display.stopAll();
        if (externalRefreshTask != null) {
            externalRefreshTask.cancel();
            externalRefreshTask = null;
        }
        if (playerData != null) playerData.shutdown();
        if (placements != null) placements.shutdown();
        if (asyncWork != null) asyncWork.close();
        if (database != null) database.close();
    }

    public void reloadPlugin() {
        ensureDefaultFiles();
        reloadConfig();
        messages.reload();
        animations.reload();
        crates.reload();
        keys.reload();
        if (rewards != null) rewards.reloadBroadcastQueue();
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
            getLogger().info("[NyxonCrates] " + reason + " – refreshing crate items.");
            crates.reload();
            keys.reload();
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
        getLogger().info("[NyxonCrates] " + source + " data loaded.");
        crates.reload();
        keys.reload();
        if (itemsAdderPending || nexoPending) {
            getLogger().info("[NyxonCrates] Waiting for remaining item plugins before starting displays.");
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
    public PlayerDataStore       playerData()    { return playerData; }
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
    public boolean tryStartAnimSession(java.util.UUID uuid, AnimationSession session) {
        return animSessions.putIfAbsent(uuid, session) == null;
    }
    public AnimationSession takeAnimSession(java.util.UUID uuid) {
        return animSessions.remove(uuid);
    }
    public AnimationSession animSession(java.util.UUID uuid) {
        return animSessions.get(uuid);
    }
    public boolean hasAnimSession(java.util.UUID uuid) {
        return animSessions.containsKey(uuid);
    }
}
