// made by haze
package it.haze.hazecrates.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.util.AsyncWorkService;

import java.io.File;
import java.sql.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.logging.Level;

public final class DatabaseService {

    private final HazeCrates plugin;
    private final AsyncWorkService async;
    private CompletableFuture<Void> ready = CompletableFuture.completedFuture(null);

    private String sqliteUrl;
    private HikariDataSource hikari;
    private volatile boolean available = false;
    private volatile boolean sqlite    = false;

    public DatabaseService(HazeCrates plugin, AsyncWorkService async) {
        this.plugin   = plugin;
        this.async = async;
    }

    public CompletableFuture<Void> initialize() {
        String driver = plugin.getConfig().getString("database.driver", "sqlite").toLowerCase();
        boolean useRemote = plugin.getConfig().getBoolean("database.enabled", true)
                && (driver.equals("mysql") || driver.equals("mariadb"));
        ready = async.run(() -> start(driver, useRemote));
        return ready;
    }

    public CompletableFuture<Void> ready() { return ready; }

    private void start(String driver, boolean remote) {
        try {
            if (remote) {
                connectRemote(driver);
            } else {
                connectSqlite();
            }
            available = true;
            createSchema();
            plugin.getLogger().info("[HazeCrates] Database pronto (" + (sqlite ? "sqlite" : driver) + ").");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[HazeCrates] Database non avviato – conteggi e chiavi virtuali solo in RAM.", e);
        }
    }

    private void connectSqlite() throws Exception {
        sqlite = true;
        plugin.getDataFolder().mkdirs();
        File file = new File(plugin.getDataFolder(), "data.db");
        DriverManager.registerDriver(new org.sqlite.JDBC());
        sqliteUrl = "jdbc:sqlite:" + file.getAbsolutePath();
        try (Connection c = DriverManager.getConnection(sqliteUrl);
             Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
        }
        hikari = createSqlitePool(sqliteUrl);
        plugin.getLogger().info("[HazeCrates] SQLite: " + file.getAbsolutePath());
    }

    static HikariDataSource createSqlitePool(String url) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setDriverClassName("org.sqlite.JDBC");
        cfg.setMaximumPoolSize(2);
        cfg.setMinimumIdle(1);
        cfg.setConnectionInitSql("PRAGMA synchronous=NORMAL");
        cfg.setPoolName("HazeCrates-SQLite");
        return new HikariDataSource(cfg);
    }

    private void connectRemote(String driver) throws Exception {
        String host = plugin.getConfig().getString("database.host", "localhost");
        int port    = plugin.getConfig().getInt("database.port", 3306);
        String db   = plugin.getConfig().getString("database.database", "minecraft");
        boolean ssl = plugin.getConfig().getBoolean("database.useSSL", false);
        String user = plugin.getConfig().getString("database.user", "root");
        String pass = plugin.getConfig().getString("database.password", "");

        String url;
        if (driver.equals("mariadb")) {
            url = "jdbc:mariadb://" + host + ":" + port + "/" + db + "?useSSL=" + ssl;
        } else {
            url = "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=" + ssl + "&allowPublicKeyRetrieval=true";
        }
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(user);
        cfg.setPassword(pass);
        int pool = Math.max(2, plugin.getConfig().getInt("database.pool-size", 4));
        cfg.setMaximumPoolSize(pool);
        cfg.setMinimumIdle(Math.min(pool, 2));
        cfg.setConnectionTimeout(plugin.getConfig().getLong("database.connection-timeout-ms", 5000));
        cfg.setValidationTimeout(2000);
        cfg.setKeepaliveTime(60_000);
        cfg.setPoolName("HazeCrates");
        hikari = new HikariDataSource(cfg);
    }

    private void createSchema() throws SQLException {
        String[] ddl = {
            "CREATE TABLE IF NOT EXISTS fc_virtual_keys (uuid TEXT NOT NULL, crate_id TEXT NOT NULL, amount INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(uuid, crate_id))",
            "CREATE TABLE IF NOT EXISTS fc_player_stats (uuid TEXT NOT NULL, crate_id TEXT NOT NULL, total_opened INTEGER NOT NULL DEFAULT 0, last_opened_at TEXT, PRIMARY KEY(uuid, crate_id))",
            "CREATE TABLE IF NOT EXISTS fc_reward_progress (uuid TEXT NOT NULL, crate_id TEXT NOT NULL, milestone_id TEXT NOT NULL, current_count INTEGER NOT NULL DEFAULT 0, claimed INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(uuid, crate_id, milestone_id))"
        };
        try (Connection c = connection(); Statement s = c.createStatement()) {
            for (String sql : ddl) s.execute(sql);
        }
    }

    private Connection connection() throws SQLException {
        return hikari.getConnection();
    }

    public boolean isAvailable() { return available; }
    public boolean isSqlite()    { return sqlite; }

    public <T> CompletableFuture<T> query(Function<Connection, T> op, T fallback) {
        if (!available) return CompletableFuture.completedFuture(fallback);
        return async.supply(() -> {
            for (int i = 0; i < 3; i++) {
                try (Connection c = connection()) { return op.apply(c); }
                catch (Exception e) {
                    if (i == 2) throw new CompletionException(e);
                    try { Thread.sleep(100L * (i + 1)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new CompletionException(ie); }
                }
            }
            throw new CompletionException("unreachable", null);
        });
    }

    public <T> CompletableFuture<T> query(Function<Connection, T> op) { return query(op, null); }

    public <T> CompletableFuture<T> transaction(Function<Connection, T> op) {
        return query(conn -> {
            try {
                conn.setAutoCommit(false);
                T result = op.apply(conn);
                conn.commit();
                return result;
            } catch (Exception error) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
                throw new CompletionException(error);
            }
        });
    }

    public void close() {
        available = false;
        if (hikari != null) hikari.close();
    }
}
