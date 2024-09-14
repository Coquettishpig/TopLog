package src.toplog.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        initializeDatabase();
    }

    private void initializeDatabase() {
        HikariConfig hikariConfig = new HikariConfig();

        if (plugin.getConfig().getBoolean("mysql.enabled")) {
            hikariConfig.setJdbcUrl(String.format("jdbc:mysql://%s:%s/%s",
                    plugin.getConfig().getString("mysql.host"),
                    plugin.getConfig().getString("mysql.port"),
                    plugin.getConfig().getString("mysql.database")));
            hikariConfig.setUsername(plugin.getConfig().getString("mysql.username"));
            hikariConfig.setPassword(plugin.getConfig().getString("mysql.password"));
            hikariConfig.addDataSourceProperty("useSSL", plugin.getConfig().getBoolean("mysql.use-ssl"));
        } else {
            hikariConfig.setJdbcUrl("jdbc:sqlite:database.db");
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
        }

        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setConnectionTestQuery("SELECT 1");
        hikariConfig.setPoolName("TopLog-Pool");
        hikariConfig.addDataSourceProperty("characterEncoding", "utf8");
        hikariConfig.addDataSourceProperty("useUnicode", true);
        hikariConfig.addDataSourceProperty("cachePrepStmts", true);
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", 250);
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", 2048);

        this.dataSource = new HikariDataSource(hikariConfig);

        createTable();
    }

    private void createTable() {
        execute("CREATE TABLE IF NOT EXISTS toplog (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY," +
                        "player_name VARCHAR(255)," +
                        "type VARCHAR(255)," +
                        "custom_data TEXT," +
                        "server_name VARCHAR(255)," +
                        "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",
                PreparedStatement::executeUpdate);
    }

    public void execute(String sql, ThrowingConsumer<PreparedStatement> consumer) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            consumer.accept(statement);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to execute SQL statement", e);
        }
    }

    public void executeAsync(String sql, ThrowingConsumer<PreparedStatement> consumer) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> execute(sql, consumer));
    }

    public void logToDatabase(String playerName, String type, String customData, String serverName) {
        executeAsync("INSERT INTO toplog (player_name, type, custom_data, server_name) VALUES (?, ?, ?, ?)", statement -> {
            statement.setString(1, playerName);
            statement.setString(2, type);
            statement.setString(3, customData);
            statement.setString(4, serverName);
            statement.executeUpdate();
        });
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        void accept(T t) throws SQLException;
    }
}