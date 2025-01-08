package me.errcruze.proxyguard;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.bstats.bungeecord.Metrics;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Proxyguard extends Plugin implements Listener {
    private Set<String> premiumPlayers;
    private Configuration config;
    private File logsFolder;
    private File successLog;
    private File failLog;
    private SimpleDateFormat dateFormat;
    private Map<String, Integer> failedAttempts;
    private Set<String> blockedIPs;

    @Override
    public void onEnable() {

        new Metrics(this, 24163);

        failedAttempts = new HashMap<>();
        blockedIPs = new HashSet<>();

        loadConfig();
        setupLogs();
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        // Register listeners and commands
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new ProxyguardCommand(this));

        // Schedule log rotation task (every 24 hours)
        getProxy().getScheduler().schedule(this, this::rotateLogFiles, 24, 24, TimeUnit.HOURS);

        // Startup message
        getLogger().info(ChatColor.DARK_GRAY + "----------------------------------------");
        getLogger().info(ChatColor.AQUA + "ProxyGuard" + ChatColor.GRAY + " has been enabled!");
        getLogger().info(ChatColor.GRAY + "Made by " + ChatColor.AQUA + "ERR CRUZE" +
                ChatColor.GRAY + " for " + ChatColor.YELLOW + "cStudios");
        getLogger().info(ChatColor.GRAY + "Protecting " + ChatColor.YELLOW +
                premiumPlayers.size() + ChatColor.GRAY + " premium accounts");
        getLogger().info(ChatColor.DARK_GRAY + "----------------------------------------");
    }

    @Override
    public void onDisable() {
        getLogger().info(ChatColor.DARK_GRAY + "----------------------------------------");
        getLogger().info(ChatColor.AQUA + "ProxyGuard" + ChatColor.GRAY + " has been disabled!");
        getLogger().info(ChatColor.GRAY + "Made by " + ChatColor.AQUA + "ERR CRUZE" +
                ChatColor.GRAY + " for " + ChatColor.YELLOW + "cStudios");
        getLogger().info(ChatColor.DARK_GRAY + "----------------------------------------");
    }

    private void setupLogs() {
        logsFolder = new File(getDataFolder(), "logs");
        if (!logsFolder.exists()) {
            logsFolder.mkdirs();
        }

        successLog = new File(logsFolder, "successful.log");
        failLog = new File(logsFolder, "unsuccessful.log");

        try {
            if (!successLog.exists()) successLog.createNewFile();
            if (!failLog.exists()) failLog.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void rotateLogFiles() {
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

        if (successLog.length() > 5 * 1024 * 1024) { // 5MB
            File backup = new File(logsFolder, "successful-" + date + ".log");
            successLog.renameTo(backup);
            setupLogs();
        }

        if (failLog.length() > 5 * 1024 * 1024) {
            File backup = new File(logsFolder, "unsuccessful-" + date + ".log");
            failLog.renameTo(backup);
            setupLogs();
        }
    }

    private void logConnection(String username, String ip, boolean success) {
        File logFile = success ? successLog : failLog;
        String timestamp = dateFormat.format(new Date());
        String logEntry = String.format("[%s] %s - IP: %s - %s%n",
                timestamp,
                username,
                ip,
                success ? "Successful Premium Login" : "Attempted Offline Login with Premium Username"
        );

        try (FileWriter fw = new FileWriter(logFile, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(logEntry);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPreLogin(PreLoginEvent event) {
        String username = event.getConnection().getName().toLowerCase();
        String ip = event.getConnection().getAddress().getAddress().getHostAddress();

        // Check if IP is blocked
        if (blockedIPs.contains(ip)) {
            event.setCancelled(true);
            event.setCancelReason(new TextComponent(ChatColor.RED + "Too many failed login attempts. Please try again later."));
            return;
        }

        if (premiumPlayers.contains(username)) {
            event.getConnection().setOnlineMode(true);
            getLogger().info(username + " connecting as premium user");
        } else {
            event.getConnection().setOnlineMode(false);
            getLogger().info(username + " connecting as cracked user");
        }
    }

    @EventHandler
    public void onLogin(LoginEvent event) {
        PendingConnection connection = event.getConnection();
        String username = connection.getName().toLowerCase();
        String ip = connection.getAddress().getAddress().getHostAddress();

        if (premiumPlayers.contains(username)) {
            if (connection.isOnlineMode()) {
                // Reset failed attempts on successful login
                failedAttempts.remove(ip);
                blockedIPs.remove(ip);
                logConnection(username, ip, true);
            } else {
                // Track failed attempts
                int attempts = failedAttempts.getOrDefault(ip, 0) + 1;
                failedAttempts.put(ip, attempts);

                // Block IP after 5 failed attempts
                if (attempts >= 5) {
                    blockedIPs.add(ip);
                    // Schedule unblock after 1 hour
                    getProxy().getScheduler().schedule(this, () -> blockedIPs.remove(ip), 1, TimeUnit.HOURS);
                }

                logConnection(username, ip, false);
                notifyAdmins(ChatColor.RED + "Warning: Failed login attempt for premium username '" +
                        username + "' from IP: " + ip);

                event.setCancelled(true);
                event.setCancelReason(new TextComponent(ChatColor.RED + "This username belongs to a premium account!"));
            }
        }
    }

    private void notifyAdmins(String message) {
        for (ProxiedPlayer player : getProxy().getPlayers()) {
            if (player.hasPermission("proxyguard.admin")) {
                player.sendMessage(new TextComponent(ChatColor.RED + "[ProxyGuard] " + message));
            }
        }
    }

    private void loadConfig() {
        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdir();
            }

            File configFile = new File(getDataFolder(), "config.yml");

            if (!configFile.exists()) {
                configFile.createNewFile();
                Configuration defaultConfig = new Configuration();
                defaultConfig.set("premium-players", new ArrayList<String>());
                ConfigurationProvider.getProvider(YamlConfiguration.class).save(defaultConfig, configFile);
            }

            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
            premiumPlayers = new HashSet<>(config.getStringList("premium-players"));

        } catch (IOException e) {
            e.printStackTrace();
            getLogger().severe("Failed to load config!");
        }
    }

    public void addPremiumPlayer(String username) {
        username = username.toLowerCase();
        premiumPlayers.add(username);
        saveConfig();
    }

    public void removePremiumPlayer(String username) {
        username = username.toLowerCase();
        premiumPlayers.remove(username);
        saveConfig();
    }

    public boolean isPremium(String username) {
        return premiumPlayers.contains(username.toLowerCase());
    }

    public Set<String> getPremiumPlayers() {
        return new HashSet<>(premiumPlayers);
    }

    private void saveConfig() {
        try {
            config.set("premium-players", new ArrayList<>(premiumPlayers));
            ConfigurationProvider.getProvider(YamlConfiguration.class)
                    .save(config, new File(getDataFolder(), "config.yml"));
        } catch (IOException e) {
            e.printStackTrace();
            getLogger().severe("Failed to save config!");
        }
    }
}