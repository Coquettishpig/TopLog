package src.toplog;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import src.toplog.database.DatabaseManager;

import java.util.ArrayList;
import java.util.List;

public class TopLog extends JavaPlugin implements CommandExecutor {

    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        // 保存默认配置文件
        saveDefaultConfig();

        // 初始化数据库管理器
        databaseManager = new DatabaseManager(this);

        // 注册指令
        this.getCommand("toplog").setExecutor(this);
        this.getCommand("toplog").setTabCompleter(new TopLogTabCompleter(this));

        // 输出插件启用的日志信息
        getLogger().info("TopLog 插件已启用！");
    }

    @Override
    public void onDisable() {
        // 关闭数据库连接
        if (databaseManager != null) {
            databaseManager.close();
        }

        // 输出插件禁用的日志信息
        getLogger().info("TopLog 插件已禁用！");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("toplog")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                sender.sendMessage("配置文件已重载。");
                return true;
            }

            if (args.length != 3) {
                sender.sendMessage("用法: /toplog <玩家名字> <类型> <自定义数据>");
                return true;
            }

            String playerName = args[0];
            String type = args[1];
            String customData = args[2];

            // 获取当前服务器名称
            String serverName = getConfig().getString("server-name", "default-server");

            // 记录日志到数据库
            databaseManager.logToDatabase(playerName, type, customData, serverName);

            sender.sendMessage("日志已记录: " + playerName + ", " + type + ", " + customData);
            return true;
        }
        return false;
    }

    public static class TopLogTabCompleter implements TabCompleter {

        private final TopLog plugin;

        public TopLogTabCompleter(TopLog plugin) {
            this.plugin = plugin;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (command.getName().equalsIgnoreCase("toplog")) {
                List<String> completions = new ArrayList<>();

                if (args.length == 1) {
                    // 补全玩家名字
                    for (Player player : sender.getServer().getOnlinePlayers()) {
                        completions.add(player.getName());
                    }
                } else if (args.length == 2) {
                    // 补全类型
                    completions.addAll(plugin.getConfig().getStringList("type"));
                }

                return completions;
            }
            return null;
        }
    }
}