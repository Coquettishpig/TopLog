package src.toplog.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
                        "custom_data TEXT," +
                        "log_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",
                PreparedStatement::executeUpdate);

        // 检查表结构是否正确创建
        checkTableStructure();
    }

    private void checkTableStructure() {
        execute("SELECT log_time FROM toplog_data LIMIT 1", statement -> {
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    plugin.getLogger().info("Table structure check passed.");
                }
            }
        });
    }

    public void logToDatabase(String playerName, String type, String customData, String serverName) {
        executeAsync("INSERT INTO toplog_data (player_name, server_name, type, custom_data, log_time) VALUES (?, ?, ?, ?, ?)", statement -> {
            statement.setString(1, playerName);
            statement.setString(2, serverName);
            statement.setString(3, type);
            statement.setString(4, customData);
            statement.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            statement.executeUpdate();
        });
    }

    public List<String> queryLogs(String playerName, String type, Date startTime, Date endTime) {
        List<String> results = new ArrayList<>();
        execute("SELECT * FROM toplog_data WHERE player_name = ? AND type = ? AND log_time BETWEEN ? AND ?", statement -> {
            statement.setString(1, playerName);
            statement.setString(2, type);
            statement.setTimestamp(3, new Timestamp(startTime.getTime()));
            statement.setTimestamp(4, new Timestamp(endTime.getTime()));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String result = String.format("ID: %d, Player: %s, Type: %s, Custom Data: %s, Time: %s",
                            resultSet.getInt("id"),
                            resultSet.getString("player_name"),
                            resultSet.getString("type"),
                            resultSet.getString("custom_data"),
                            resultSet.getTimestamp("log_time"));
                    results.add(result);
                }
            }
        });
        return results;
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