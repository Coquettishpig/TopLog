package src.toplog;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import src.toplog.database.DatabaseManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TopLog extends JavaPlugin implements CommandExecutor {

    private DatabaseManager databaseManager;
    private SimpleDateFormat dateFormat;

    @Override
    public void onEnable() {
        // 保存默认配置文件
        saveDefaultConfig();

        // 初始化数据库管理器
        databaseManager = new DatabaseManager(this);

        // 初始化日期格式
        dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

        // 注册指令
        this.getCommand("toplog").setExecutor(this);
        this.getCommand("toplogquery").setExecutor(this);
        this.getCommand("toplog").setTabCompleter(new TopLogTabCompleter(this));
        this.getCommand("toplogquery").setTabCompleter(new TopLogTabCompleter(this));

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
            // 检查命令发送者是否为控制台
            if (!(sender instanceof Player)) {
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
            } else {
                sender.sendMessage("该命令只能由控制台使用。");
                return true;
            }
        } else if (command.getName().equalsIgnoreCase("toplogquery")) {
            // 检查发送者是否有权限
            if (sender.hasPermission("toplog.use")) {
                if (args.length != 4) {
                    sender.sendMessage("用法: /toplogquery <玩家名字> <类型> <开始时间> <结束时间>");
                    sender.sendMessage("示例：/toplogquery 114514 大额转账 2024-09-18-13-13-27-00 2024-09-18-13-15-00-00");
                    return true;
                }

                String playerName = args[0];
                String type = args[1];
                String startTimeStr = args[2];
                String endTimeStr = args[3];

                Date startTime;
                Date endTime;

                try {
                    startTime = dateFormat.parse(startTimeStr);
                    endTime = dateFormat.parse(endTimeStr);
                } catch (ParseException e) {
                    sender.sendMessage("时间格式错误，请使用 yyyy-MM-dd-HH-mm-ss 格式。");
                    return true;
                }

                // 查询数据库
                List<String> results = databaseManager.queryLogs(playerName, type, startTime, endTime);

                if (results.isEmpty()) {
                    sender.sendMessage("未找到符合条件的日志。");
                } else {
                    sender.sendMessage("查询结果:");
                    for (String result : results) {
                        sender.sendMessage(result);
                    }
                }
                return true;
            } else {
                sender.sendMessage("你没有权限使用该命令。");
                return true;
            }
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
            if (command.getName().equalsIgnoreCase("toplog") || command.getName().equalsIgnoreCase("toplogquery")) {
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