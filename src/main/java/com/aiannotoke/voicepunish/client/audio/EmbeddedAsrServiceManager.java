package com.aiannotoke.voicepunish.client.audio;

import com.aiannotoke.voicepunish.VoicePunishMod;
import com.aiannotoke.voicepunish.config.VoicePunishClientConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class EmbeddedAsrServiceManager implements AutoCloseable {

    static final String BUNDLE_RESOURCE = "/voicepunish/asr/windows-x64/asr-service.zip";
    static final long LAUNCH_BACKOFF_MS = 15_000L;
    static final long HEALTH_WAIT_TIMEOUT_MS = 45_000L;
    static final long HEALTH_POLL_INTERVAL_MS = 1_000L;
    private static final Duration REQUEST_TIMEOUT = Duration.ofMillis(2_000L);

    private final VoicePunishClientConfig config;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final Path installRoot;
    private final Path appDir;
    private final boolean portableInstall;
    private final boolean supportedPlatform;

    private volatile Process serviceProcess;
    private volatile CompletableFuture<Boolean> launchFuture = CompletableFuture.completedFuture(false);
    private volatile long nextLaunchAttemptAt;

    public EmbeddedAsrServiceManager(VoicePunishClientConfig config) {
        this.config = config.copy();
        this.config.fillDefaults();
        this.executor = Executors.newSingleThreadExecutor(new EmbeddedAsrThreadFactory());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .executor(executor)
                .build();
        this.supportedPlatform = isSupportedPlatform(System.getProperty("os.name"), System.getProperty("os.arch"));

        String version = FabricLoader.getInstance()
                .getModContainer(VoicePunishMod.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
        String safeVersion = sanitizePathComponent(version);
        Path packagedInstallRoot = resolvePackagedInstallRoot(safeVersion);
        this.portableInstall = packagedInstallRoot != null;
        this.installRoot = packagedInstallRoot != null
                ? packagedInstallRoot
                : resolveLocalAppData().resolve("VoicePunishASR").resolve("embedded").resolve(safeVersion);
        this.appDir = installRoot.resolve("app");
    }

    public void initializeAsync() {
        if (shouldManage()) {
            ClientAsrRuntimeState.setStarting("starting local service");
            ensureRunningAsync();
        } else {
            ClientAsrRuntimeState.setDisabled("provider disabled");
        }
    }

    public boolean shouldManage() {
        return supportedPlatform && "local_funasr".equals(config.transcriptionProvider);
    }

    public CompletableFuture<Boolean> ensureRunningAsync() {
        if (!shouldManage()) {
            return CompletableFuture.completedFuture(false);
        }
        if (isHealthy()) {
            return CompletableFuture.completedFuture(true);
        }

        long now = System.currentTimeMillis();
        if (now < nextLaunchAttemptAt) {
            return CompletableFuture.completedFuture(false);
        }

        synchronized (this) {
            if (isHealthy()) {
                return CompletableFuture.completedFuture(true);
            }
            if (launchFuture != null && !launchFuture.isDone()) {
                return launchFuture;
            }
            nextLaunchAttemptAt = now + LAUNCH_BACKOFF_MS;
            ClientAsrRuntimeState.setStarting("checking offline ASR");
            launchFuture = CompletableFuture.supplyAsync(this::bootstrapAndLaunch, executor)
                    .exceptionally(throwable -> {
                        ClientAsrRuntimeState.setError("bootstrap failed");
                        VoicePunishMod.LOGGER.warn("Embedded ASR bootstrap failed", throwable);
                        return false;
                    });
            return launchFuture;
        }
    }

    public boolean isHealthy() {
        HttpRequest request = HttpRequest.newBuilder(baseUri("/healthz"))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public void close() {
        Process process = serviceProcess;
        serviceProcess = null;
        if (process != null) {
            destroyProcessTree(process.toHandle());
        }
        executor.shutdownNow();
    }

    private boolean bootstrapAndLaunch() {
        try {
            if (isHealthy()) {
                ClientAsrRuntimeState.setReady("ready");
                return true;
            }

            extractBundleIfNeeded();
            ensureInstalled();
            if (isHealthy()) {
                ClientAsrRuntimeState.setReady("ready");
                return true;
            }

            startServiceIfNeeded();
            boolean ready = waitForHealthy();
            if (ready) {
                ClientAsrRuntimeState.setReady("ready");
            } else {
                ClientAsrRuntimeState.setStarting("loading model");
            }
            return ready;
        } catch (Exception exception) {
            ClientAsrRuntimeState.setError("offline ASR failed");
            VoicePunishMod.LOGGER.warn("Failed to bootstrap embedded ASR service", exception);
            return false;
        }
    }

    private void extractBundleIfNeeded() throws IOException {
        if (Files.exists(appDir.resolve("voice-punish-asr-service.json"))) {
            return;
        }

        ClientAsrRuntimeState.setPreparing("extracting bundled files");
        Files.createDirectories(appDir);
        try (InputStream stream = EmbeddedAsrServiceManager.class.getResourceAsStream(BUNDLE_RESOURCE)) {
            if (stream != null) {
                unzip(stream, appDir);
                return;
            }
        }

        Path devServiceDir = Path.of("asr-service");
        if (Files.exists(devServiceDir.resolve("app.py"))) {
            copyDirectory(devServiceDir, appDir);
            return;
        }

        throw new IOException("Embedded ASR bundle resource missing: " + BUNDLE_RESOURCE);
    }

    private void ensureInstalled() throws IOException, InterruptedException {
        if (Files.exists(installPythonPath())) {
            ClientAsrRuntimeState.setPreparing("offline runtime ready");
            return;
        }

        Path installScript = appDir.resolve("install.ps1");
        if (!Files.exists(installScript)) {
            throw new IOException("Embedded ASR install script missing: " + installScript);
        }

        VoicePunishMod.LOGGER.info("Installing embedded ASR runtime into {}", installRoot);
        ClientAsrRuntimeState.setPreparing("installing offline runtime");
        Path logsDir = installRoot.resolve("logs");
        Files.createDirectories(logsDir);
        Path installLog = logsDir.resolve("embedded-install.log");

        ProcessBuilder builder = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                installScript.toString()
        );
        builder.directory(appDir.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(installLog.toFile());

        Map<String, String> environment = builder.environment();
        environment.put("VOICE_PUNISH_ASR_INSTALL_ROOT", installRoot.toString());
        environment.put("VOICE_PUNISH_ASR_REGISTER_STARTUP", "0");
        environment.put("VOICE_PUNISH_ASR_AUTO_START", "0");

        Process process = builder.start();
        boolean finished = process.waitFor(20L, TimeUnit.MINUTES);
        if (!finished) {
            destroyProcessTree(process.toHandle());
            throw new IOException("Embedded ASR install timed out");
        }
        if (process.exitValue() != 0) {
            throw new IOException("Embedded ASR install failed with exit code " + process.exitValue());
        }
    }

    private void startServiceIfNeeded() throws IOException {
        if (isHealthy()) {
            return;
        }
        if (serviceProcess != null && serviceProcess.isAlive()) {
            return;
        }

        Path startScript = appDir.resolve("start-service.bat");
        if (!Files.exists(startScript)) {
            throw new IOException("Embedded ASR start script missing: " + startScript);
        }

        Path logsDir = installRoot.resolve("logs");
        Files.createDirectories(logsDir);
        Path launchLog = logsDir.resolve("embedded-launch.log");

        ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", "start-service.bat");
        builder.directory(appDir.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(launchLog.toFile());
        builder.environment().put("VOICE_PUNISH_ASR_INSTALL_ROOT", installRoot.toString());
        builder.environment().put("VOICE_PUNISH_ASR_PORTABLE", portableInstall ? "1" : "0");

        ClientAsrRuntimeState.setStarting("launching local ASR");
        serviceProcess = builder.start();
        VoicePunishMod.LOGGER.info("Launching embedded ASR service from {}", appDir);
    }

    private boolean waitForHealthy() throws InterruptedException {
        long deadline = System.currentTimeMillis() + HEALTH_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (isHealthy()) {
                return true;
            }
            Process process = serviceProcess;
            if (process != null && !process.isAlive()) {
                return false;
            }
            Thread.sleep(HEALTH_POLL_INTERVAL_MS);
        }
        return isHealthy();
    }

    private URI baseUri(String path) {
        String base = config.localAsrBaseUrl;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + path);
    }

    private Path installPythonPath() {
        Path bundledPortable = installRoot.resolve("runtime").resolve("python310").resolve("python.exe");
        if (Files.exists(bundledPortable)) {
            return bundledPortable;
        }
        return installRoot.resolve("venv").resolve("Scripts").resolve("python.exe");
    }

    private static void unzip(InputStream stream, Path targetDir) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(stream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path destination = targetDir.resolve(entry.getName()).normalize();
                if (!destination.startsWith(targetDir)) {
                    throw new IOException("Refusing to unzip outside target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(Objects.requireNonNullElse(destination.getParent(), targetDir));
                    Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            paths.forEach(path -> {
                try {
                    Path relative = source.relativize(path);
                    Path destination = target.resolve(relative);
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(Objects.requireNonNullElse(destination.getParent(), target));
                        Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        } catch (IllegalStateException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

    private static void destroyProcessTree(ProcessHandle handle) {
        handle.descendants().forEach(child -> {
            if (child.isAlive()) {
                child.destroyForcibly();
            }
        });
        if (handle.isAlive()) {
            handle.destroyForcibly();
        }
    }

    static boolean isSupportedPlatform(String osName, String osArch) {
        String normalizedOs = osName == null ? "" : osName.toLowerCase();
        String normalizedArch = osArch == null ? "" : osArch.toLowerCase();
        return normalizedOs.contains("win") && (normalizedArch.contains("amd64") || normalizedArch.contains("x86_64"));
    }

    private static Path resolveLocalAppData() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData);
        }
        return Path.of(System.getProperty("user.home"), "AppData", "Local");
    }

    private static Path resolvePackagedInstallRoot(String safeVersion) {
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            Path packaged = gameDir.resolve("voicepunish-offline").resolve("embedded").resolve(safeVersion);
            if (Files.exists(packaged.resolve("app").resolve("voice-punish-asr-service.json"))
                    && Files.exists(packaged.resolve("runtime").resolve("python310").resolve("python.exe"))
                    && Files.exists(packaged.resolve("model-cache"))) {
                return packaged;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String sanitizePathComponent(String value) {
        return value == null || value.isBlank()
                ? "dev"
                : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static final class EmbeddedAsrThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "VoicePunish-EmbeddedAsr");
            thread.setDaemon(true);
            return thread;
        }
    }
}
