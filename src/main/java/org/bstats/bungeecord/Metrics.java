package org.bstats.bungeecord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;

public class Metrics {

    private final Plugin plugin;
    private final MetricsBase metricsBase;

    public Metrics(Plugin plugin, int serviceId) {
        this.plugin = plugin;
        // Get the config file
        File bStatsFolder = new File(plugin.getDataFolder().getParentFile(), "bStats");
        File configFile = new File(bStatsFolder, "config.yml");
        Configuration configuration = null;
        // Check if the config file exists
        if (!configFile.exists()) {
            // Create the folders
            if (!bStatsFolder.exists()) {
                if (!bStatsFolder.mkdir()) {
                    plugin.getLogger().log(Level.WARNING, "Failed to create bStats metrics folder.");
                }
            }
            try {
                // Create the file
                if (!configFile.createNewFile()) {
                    throw new IOException("Failed to create config file");
                }
                try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(configFile))) {
                    bufferedWriter.write("# bStats (https://bStats.org) collects some basic information for plugin authors, like how\n"
                            + "# many people use their plugin and their total player count. It's recommended to keep bStats\n"
                            + "# enabled, but if you're not comfortable with this, you can turn this setting off. There is no\n"
                            + "# performance penalty associated with having metrics enabled, and data sent to bStats is fully\n"
                            + "# anonymous.\n"
                            + "enabled: true\n"
                            + "serverUuid: \"" + UUID.randomUUID().toString() + "\"\n"
                            + "logFailedRequests: false\n"
                            + "logSentData: false\n"
                            + "logResponseStatusText: false");
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to create bStats config", e);
            }
        }
        try {
            configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load bStats config", e);
        }
        // Load the data
        metricsBase = new MetricsBase(
                "bungeecord",
                configuration.getString("serverUuid"),
                serviceId,
                configuration.getBoolean("enabled", true),
                this::appendPlatformData,
                this::appendServiceData,
                null,
                () -> true,
                (message, error) -> this.plugin.getLogger().log(Level.WARNING, message, error),
                (message) -> this.plugin.getLogger().log(Level.INFO, message),
                configuration.getBoolean("logSentData", false),
                configuration.getBoolean("logResponseStatusText", false)
        );
    }

    private void appendPlatformData(JsonObject builder) {
        builder.addProperty("playerAmount", plugin.getProxy().getOnlineCount());
        builder.addProperty("managedServers", plugin.getProxy().getServers().size());
        builder.addProperty("onlineMode", plugin.getProxy().getConfig().isOnlineMode());
        builder.addProperty("bungeecordVersion", plugin.getProxy().getVersion());
        builder.addProperty("javaVersion", System.getProperty("java.version"));
        builder.addProperty("osName", System.getProperty("os.name"));
        builder.addProperty("osArch", System.getProperty("os.arch"));
        builder.addProperty("osVersion", System.getProperty("os.version"));
        builder.addProperty("coreCount", Runtime.getRuntime().availableProcessors());
    }

    private void appendServiceData(JsonObject builder) {
        builder.addProperty("pluginVersion", plugin.getDescription().getVersion());
    }

    public void addCustomChart(CustomChart chart) {
        metricsBase.addCustomChart(chart);
    }

    public static class MetricsBase {

        /** The version of the Metrics class. */
        public static final String METRICS_VERSION = "2.2.1";

        private static final String REPORT_URL = "https://bStats.org/api/v2/data/%s";

        private final String platform;
        private final String serverUuid;
        private final int serviceId;
        private final boolean enabled;
        private final Consumer<JsonObject> appendPlatformDataConsumer;
        private final Consumer<JsonObject> appendServiceDataConsumer;
        private final Consumer<Map<String, ?>> submitDataConsumer;
        private final Supplier<Boolean> checkServiceEnabledSupplier;
        private final BiConsumer<String, Throwable> errorLogger;
        private final Consumer<String> infoLogger;
        private final boolean logSentData;
        private final boolean logResponseStatusText;

        private final List<CustomChart> customCharts = new ArrayList<>();

        private MetricsBase(
                String platform,
                String serverUuid,
                int serviceId,
                boolean enabled,
                Consumer<JsonObject> appendPlatformDataConsumer,
                Consumer<JsonObject> appendServiceDataConsumer,
                Consumer<Map<String, ?>> submitDataConsumer,
                Supplier<Boolean> checkServiceEnabledSupplier,
                BiConsumer<String, Throwable> errorLogger,
                Consumer<String> infoLogger,
                boolean logSentData,
                boolean logResponseStatusText) {
            this.platform = platform;
            this.serverUuid = serverUuid;
            this.serviceId = serviceId;
            this.enabled = enabled;
            this.appendPlatformDataConsumer = appendPlatformDataConsumer;
            this.appendServiceDataConsumer = appendServiceDataConsumer;
            this.submitDataConsumer = submitDataConsumer;
            this.checkServiceEnabledSupplier = checkServiceEnabledSupplier;
            this.errorLogger = errorLogger;
            this.infoLogger = infoLogger;
            this.logSentData = logSentData;
            this.logResponseStatusText = logResponseStatusText;

            if (enabled) {
                startSubmitting();
            }
        }

        private void startSubmitting() {
            final Timer timer = new Timer("bStats-Metrics");
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    submitData();
                }
            }, 1000 * 60 * 5, 1000 * 60 * 30);
        }

        public void addCustomChart(CustomChart chart) {
            this.customCharts.add(chart);
        }

        private void submitData() {
            if (!enabled) {
                return;
            }
            if (!checkServiceEnabledSupplier.get()) {
                return;
            }
            JsonObject data = getServerData();
            JsonArray customChartData = new JsonArray();
            for (CustomChart customChart : customCharts) {
                JsonObject chart = customChart.getRequestJsonObject();
                if (chart == null) {
                    continue;
                }
                customChartData.add(chart);
            }
            data.add("customCharts", customChartData);

            new Thread(() -> {
                try {
                    sendData(data);
                } catch (Exception e) {
                    errorLogger.accept("Could not submit bStats metrics data", e);
                }
            }).start();
        }

        private JsonObject getServerData() {
            JsonObject data = new JsonObject();
            data.addProperty("serverUUID", serverUuid);
            data.addProperty("metricsVersion", METRICS_VERSION);
            appendPlatformDataConsumer.accept(data);
            appendServiceDataConsumer.accept(data);
            return data;
        }

        private void sendData(JsonObject data) throws Exception {
            if (logSentData) {
                infoLogger.accept("Sent bStats metrics data: " + data);
            }

            String url = String.format(REPORT_URL, platform);
            HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
            byte[] compressedData = compress(data.toString());

            connection.setRequestMethod("POST");
            connection.addRequestProperty("Accept", "application/json");
            connection.addRequestProperty("Connection", "close");
            connection.addRequestProperty("Content-Encoding", "gzip");
            connection.addRequestProperty("Content-Length", String.valueOf(compressedData.length));
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "Metrics-Service/1");

            connection.setDoOutput(true);
            try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
                outputStream.write(compressedData);
            }

            StringBuilder builder = new StringBuilder();
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    builder.append(line);
                }
            }

            if (logResponseStatusText) {
                infoLogger.accept("Sent data to bStats and received response: " + builder);
            }
        }

        private static byte[] compress(final String str) throws IOException {
            if (str == null) {
                return null;
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(outputStream)) {
                gzip.write(str.getBytes(StandardCharsets.UTF_8));
            }
            return outputStream.toByteArray();
        }
    }

    public static abstract class CustomChart {
        private final String chartId;

        protected CustomChart(String chartId) {
            if (chartId == null) {
                throw new IllegalArgumentException("chartId must not be null");
            }
            this.chartId = chartId;
        }

        public JsonObject getRequestJsonObject() {
            JsonObject chart = new JsonObject();
            chart.addProperty("chartId", chartId);
            try {
                JsonObject data = getChartData();
                if (data == null) {
                    return null;
                }
                chart.add("data", data);
            } catch (Throwable t) {
                return null;
            }
            return chart;
        }

        protected abstract JsonObject getChartData() throws Exception;
    }

    public static class SimplePie extends CustomChart {

        private final Callable<String> callable;

        /**
         * Class constructor.
         *
         * @param chartId The id of the chart.
         * @param callable The callable which is used to request the chart data.
         */
        public SimplePie(String chartId, Callable<String> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JsonObject getChartData() throws Exception {
            JsonObject data = new JsonObject();
            String value = callable.call();
            if (value == null || value.isEmpty()) {
                return null;
            }
            data.addProperty("value", value);
            return data;
        }
    }

    @FunctionalInterface
    public interface Consumer<T> {
        void accept(T t);
    }

    @FunctionalInterface
    public interface BiConsumer<T, U> {
        void accept(T t, U u);
    }

    @FunctionalInterface
    public interface Supplier<T> {
        T get();
    }
}