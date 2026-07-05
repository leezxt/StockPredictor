package org.gtalent;

import org.springframework.core.env.Environment;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public final class AppRuntime {
    public static final String DATA_DIR_PROPERTY = "stockpredictor.data-dir";
    public static final String JDBC_URL_PROPERTY = "stockpredictor.jdbc-url";
    private static final String DATA_DIR_ENV = "STOCKPREDICTOR_DATA_DIR";
    private static final String JDBC_URL_ENV = "STOCKPREDICTOR_JDBC_URL";
    private static final String APP_NAME = "StockPredictor";

    private AppRuntime() {
    }

    public static void initializeSystemProperties() {
        Path dataDir = resolveDataDirectory();
        System.setProperty(DATA_DIR_PROPERTY, dataDir.toString());
        System.setProperty(JDBC_URL_PROPERTY, resolveJdbcUrl());
        configureServerPort();
        System.out.println("資料目錄: " + dataDir);
    }

    public static String resolveJdbcUrl() {
        String configured = firstNonBlank(
                System.getProperty(JDBC_URL_PROPERTY),
                System.getenv(JDBC_URL_ENV)
        );
        if (isBlank(configured)) {
            return resolveJdbcUrl(resolveDataDirectory());
        }
        return configured;
    }

    public static Path resolveDataDirectory() {
        String configured = firstNonBlank(
                System.getProperty(DATA_DIR_PROPERTY),
                System.getenv(DATA_DIR_ENV)
        );
        Path dataDir;
        if (!isBlank(configured)) {
            dataDir = Paths.get(configured);
        } else {
            dataDir = defaultDataDirectory();
        }

        dataDir = dataDir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(dataDir);
        } catch (Exception e) {
            throw new IllegalStateException("無法建立資料目錄: " + dataDir, e);
        }
        return dataDir;
    }

    public static void openBrowserIfEnabled(Environment environment) {
        boolean autoOpen = environment.getProperty("app.browser.auto-open", Boolean.class, false);
        if (!autoOpen) {
            return;
        }
        if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()) {
            System.out.println("目前環境不支援自動開啟瀏覽器，請手動前往首頁。");
            return;
        }

        String host = environment.getProperty("app.browser.host", "127.0.0.1");
        String port = firstNonBlank(
                environment.getProperty("local.server.port"),
                environment.getProperty("server.port"),
                "8080"
        );
        long delayMs = environment.getProperty("app.browser.startup-delay-ms", Long.class, 1200L);
        String url = "http://" + host + ":" + port + "/";

        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(Math.max(delayMs, 0L));
                Desktop.getDesktop().browse(new URI(url));
                System.out.println("已自動開啟瀏覽器: " + url);
            } catch (Exception e) {
                System.out.println("自動開啟瀏覽器失敗，請手動前往: " + url + "，原因: " + e.getMessage());
            }
        }, "stockpredictor-browser-launcher");
        thread.setDaemon(true);
        thread.start();
    }

    private static final int DEFAULT_PORT = 19090;

    private static void configureServerPort() {
        String configuredPort = firstNonBlank(
                System.getProperty("server.port"),
                System.getenv("SERVER_PORT")
        );
        if (!isBlank(configuredPort)) {
            System.setProperty("server.port", configuredPort);
            System.out.println("啟動埠: " + configuredPort);
            return;
        }

        if (isPortAvailable(DEFAULT_PORT)) {
            System.setProperty("server.port", String.valueOf(DEFAULT_PORT));
            System.out.println("啟動埠: " + DEFAULT_PORT);
        } else {
            try (ServerSocket socket = new ServerSocket(0)) {
                socket.setReuseAddress(true);
                int freePort = socket.getLocalPort();
                System.setProperty("server.port", String.valueOf(freePort));
                System.out.println("預設埠 " + DEFAULT_PORT + " 已被佔用，已改用: " + freePort);
            } catch (Exception e) {
                throw new IllegalStateException("無法取得可用的啟動埠", e);
            }
        }
    }

    private static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String resolveJdbcUrl(Path dataDir) {
        String dbBasePath = dataDir.resolve("stockdb").toAbsolutePath().normalize().toString().replace('\\', '/');
        if (isRunningInDocker()) {
            return "jdbc:h2:file:" + dbBasePath + ";DB_CLOSE_ON_EXIT=FALSE;AUTO_RECONNECT=TRUE";
        }
        return "jdbc:h2:file:" + dbBasePath + ";DB_CLOSE_ON_EXIT=FALSE;AUTO_RECONNECT=TRUE";
    }

    private static boolean isRunningInDocker() {
        if ("true".equalsIgnoreCase(System.getenv("RUNNING_IN_DOCKER"))) {
            return true;
        }
        return Files.exists(Paths.get("/.dockerenv")) || Files.exists(Paths.get("/.containerenv"));
    }

    private static Path defaultDataDirectory() {
        String userHome = System.getProperty("user.home", ".");
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        if (osName.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (!isBlank(localAppData)) {
                return Paths.get(localAppData, APP_NAME);
            }
            return Paths.get(userHome, "AppData", "Local", APP_NAME);
        }

        if (osName.contains("mac")) {
            return Paths.get(userHome, "Library", "Application Support", APP_NAME);
        }

        return Paths.get(userHome, ".stockpredictor");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
