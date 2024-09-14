package src.toplog.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DatabaseManager {

    private final HikariDataSource dataSource;
    private final JavaPlugin plugin;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        HikariConfig hikariConfig = new HikariConfig();

        hikariConfig.setJdbcUrl(String.format("jdbc:mysql://%s:%s/%s",
                plugin.getConfig().getString("mysql.host", "localhost"),
                plugin.getConfig().getString("mysql.port", "3306"),
                plugin.getConfig().getString("mysql.database", "woolwars")));
        hikariConfig.setUsername(plugin.getConfig().getString("mysql.username", "root"));
        hikariConfig.setPassword(plugin.getConfig().getString("mysql.password", ""));
        hikariConfig.addDataSourceProperty("useSSL", plugin.getConfig().getBoolean("mysql.use-ssl", false));

        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setConnectionTestQuery("SELECT 1");
        hikariConfig.setPoolName("TopLog-Pool");

        this.dataSource = new HikariDataSource(hikariConfig);

        createTable();
    }

    private void createTable() {
        execute("CREATE TABLE IF NOT EXISTS toplog_data (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY," +
                        "player_name VARCHAR(255)," +
                        "server_name VARCHAR(255)," +
                        "type VARCHAR(255)," +
                        "custom_data TEXT)",
                PreparedStatement::executeUpdate);
    }

    public void logToDatabase(String playerName, String type, String customData, String serverName) {
        executeAsync("INSERT INTO toplog_data (player_name, server_name, type, custom_data) VALUES (?, ?, ?, ?)", statement -> {
            statement.setString(1, playerName);
            statement.setString(2, serverName);
            statement.setString(3, type);
            statement.setString(4, customData);
            statement.executeUpdate();
        });
    }

    private void execute(String sql, ThrowingConsumer<PreparedStatement> consumer) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            consumer.accept(statement);
        } catch (SQLException e) {
            e.printStackTrace();
            plugin.getLogger().severe("Failed to execute SQL query. Disabling plugin...");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        }
    }

    private void executeAsync(String sql, ThrowingConsumer<PreparedStatement> consumer) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> execute(sql, consumer));
    }

    public void close() {
        dataSource.close();
    }

    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        void accept(T t) throws SQLException;
    }
}