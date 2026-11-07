// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.pack;

import com.flechazo.hkt.Either;
import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.hkt.business.util.ThrowableSupport;
import com.flechazo.polaris_vehicle.sparkcore.SparkCore;
import com.jme3.system.JmeSystem;
import com.jme3.system.Platform.Os;

import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLPaths;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class NativeLoader {
    private static final Logger LOGGER = SparkCore.logger("Native加载器");

    private record DistroPackages(String deb, String rpm, String alpine, String desc) {}

    private enum DistroType {
        DEB,
        RPM,
        ALPINE,
        UNKNOWN
    }

    private static final Map<String, DistroPackages> KNOWN_DEPENDENCIES =
            Map.of(
                    "libgomp.so",
                    new DistroPackages(
                            "libgomp1",
                            "libgomp",
                            "libgomp",
                            "GNU OpenMP runtime (multi-threading support)"),
                    "libstdc++.so",
                    new DistroPackages(
                            "libstdc++6", "libstdc++", "libstdc++", "GNU C++ standard library"),
                    "libgcc_s.so",
                    new DistroPackages("libgcc-s1", "libgcc", "libgcc", "GCC runtime library"),
                    "libm.so",
                    new DistroPackages("libc6", "glibc", "musl", "Math library (glibc/musl)"),
                    "libc.so",
                    new DistroPackages("libc6", "glibc", "musl", "C standard library"),
                    "libpthread.so",
                    new DistroPackages("libc6", "glibc", "musl", "POSIX threads library"));

    private static final class NativeLoadFailedException extends Exception {
        private final String path;

        NativeLoadFailedException(String path, UnsatisfiedLinkError error) {
            super(error);
            this.path = path;
        }
    }

    private static ModLoadingException loadingError(String message) {
        return new ModLoadingException(ModLoadingIssue.error(message));
    }

    public static void load(String modId, String module) {
        load(modId, module, null);
    }

    public static void load(String modId, String module, String fallback) {
        Os platform = resolveOs();
        if (platform == Os.Android && fallback != null) {
            load(modId, fallback);
            return;
        }
        String file =
                switch (platform) {
                    case Windows -> "bulletjme.dll";
                    case Linux, Android -> "libbulletjme.so";
                    case MacOS -> "libbulletjme.dylib";
                    default -> throw loadingError("Bullet 物理库不支持该系统平台: " + platform);
                };
        String platformPath =
                switch (platform) {
                    case Windows -> "windows";
                    case Linux -> "linux";
                    case MacOS -> "macos";
                    case Android -> "android";
                    default -> throw loadingError("未知平台: " + platform);
                };
        String arch =
                resolveArchPath(platform, System.getProperty("os.arch").toLowerCase(Locale.ROOT));
        var primary =
                Pathway.either(tryLoadModule(modId, module, platformPath, arch, file))
                        .peek(path -> LOGGER.info("已加载物理库: {}", path))
                        .peekLeft(error -> reportFailure(module, file, error))
                        .run();
        if (primary.isRight()) return;
        var result =
                Pathway.either(primary)
                        .orElse(
                                () -> {
                                    if (fallback == null) return Pathway.either(primary);
                                    LOGGER.error("正在尝试回退到单线程物理库 ({})...", fallback);
                                    return Pathway.either(
                                                    tryLoadModule(
                                                            modId,
                                                            fallback,
                                                            platformPath,
                                                            arch,
                                                            file))
                                            .peek(path -> LOGGER.info("已成功加载回退的单线程物理库: {}", path))
                                            .peekLeft(
                                                    error -> reportFailure(fallback, file, error));
                                })
                        .run();
        result.fold(
                error -> {
                    var failure =
                            loadingError(
                                    fallback != null
                                            ? "物理库加载失败：主库多线程版本 ("
                                                    + module
                                                    + ") 和回退单线程版本 ("
                                                    + fallback
                                                    + ") 均无法加载。"
                                            : "物理库 (" + module + ") 加载失败：" + error.getMessage());
                    failure.addSuppressed(error);
                    if (fallback != null)
                        primary.fold(
                                first -> {
                                    if (first != error) failure.addSuppressed(first);
                                    return false;
                                },
                                value -> false);
                    throw failure;
                },
                path -> path);
    }

    private static Os resolveOs() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String runtime = System.getProperty("java.runtime.name", "").toLowerCase(Locale.ROOT);
        String vm = System.getProperty("java.vm.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("android") || runtime.contains("android") || vm.contains("dalvik")
                || (os.contains("linux") && System.getenv("ANDROID_ROOT") != null
                        && System.getenv("ANDROID_DATA") != null)) return Os.Android;
        return JmeSystem.getPlatform().getOs();
    }

    private static String resolveArchPath(Os platform, String arch) {
        if (platform == Os.Android) {
            if (arch.contains("aarch64") || arch.contains("arm64")) return "arm64-v8a";
            if (arch.equals("arm") || arch.equals("aarch32")
                    || (arch.contains("arm") && arch.contains("v7"))) return "armeabi-v7a";
            if (arch.equals("amd64") || arch.contains("x86_64")) return "x86_64";
        } else {
            if (arch.contains("aarch64") || arch.contains("arm64")) return "arm64";
            if (arch.contains("amd64") || arch.contains("x86_64")) return "x86_64";
        }
        return arch;
    }

    private static Either<Throwable, String> tryLoadModule(
            String modId, String module, String platform, String arch, String file) {
        return Pathway.tryOf(() -> extractAndPrepareLibrary(modId, module, platform, arch, file))
                .toEitherPath()
                .via(
                        path -> {
                            try {
                                System.load(path);
                                return Pathway.<Throwable, String>right(path);
                            } catch (UnsatisfiedLinkError error) {
                                return Pathway.<Throwable, String>left(
                                        new NativeLoadFailedException(path, error));
                            }
                        })
                .run();
    }

    private static String extractAndPrepareLibrary(
            String modId, String module, String platform, String arch, String file)
            throws Exception {
        String resource = "natives/" + module + "/" + platform + "/" + arch + "/" + file;
        var mod =
                Pathway.nullable(ModList.get().getModFileById(modId))
                        .toEither(() -> "未找到模组: " + modId)
                        .fold(
                                message -> {
                                    throw loadingError(message);
                                },
                                value -> value);
        var source = mod.getFile().findResource(resource);
        if (!Files.isRegularFile(source)) throw loadingError("未找到Native核心文件: " + resource);
        var dir =
                FMLPaths.GAMEDIR
                        .get()
                        .resolve(modId)
                        .resolve("natives")
                        .resolve(module)
                        .resolve(platform)
                        .resolve(arch);
        Files.createDirectories(dir);
        byte[] bytes = Files.readAllBytes(source);
        String hash =
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes), 0, 6);
        int dot = file.lastIndexOf('.');
        String base = file.substring(0, dot), extension = file.substring(dot + 1);
        var target = dir.resolve(base + "-" + hash + "." + extension);
        if (!Files.exists(target) || Files.size(target) != bytes.length) {
            var temp = Files.createTempFile(dir, "tmp_", null);
            Throwable failure = null;
            try {
                Pathway.tryRun(() -> Files.write(temp, bytes))
                        .peekChecked(
                                ignored -> {
                                    try {
                                        Files.move(
                                                temp,
                                                target,
                                                StandardCopyOption.ATOMIC_MOVE,
                                                StandardCopyOption.REPLACE_EXISTING);
                                    } catch (AtomicMoveNotSupportedException error) {
                                        Files.move(
                                                temp, target, StandardCopyOption.REPLACE_EXISTING);
                                    }
                                })
                        .peekFailure(
                                error ->
                                        LOGGER.error(
                                                "Native库复制失败: source={} target={} reason={}",
                                                source,
                                                target,
                                                error.getMessage(),
                                                error))
                        .run()
                        .getOrThrow();
            } catch (Throwable error) {
                failure = error;
                throw ThrowableSupport.propagate(error);
            } finally {
                try {
                    Files.deleteIfExists(temp);
                } catch (Throwable cleanup) {
                    if (failure != null) ThrowableSupport.suppress(failure, cleanup);
                    else throw ThrowableSupport.propagate(cleanup);
                }
            }
        }
        removeStaleCopies(dir, base, extension, target);
        return target.toAbsolutePath().toString();
    }

    private static void removeStaleCopies(Path dir, String base, String extension, Path current) {
        Pathway.tryRun(
                () -> {
                    try (var files = Files.list(dir)) {
                        files.filter(path -> !path.equals(current))
                                .filter(
                                        path ->
                                                path.getFileName().toString().startsWith(base + "-")
                                                        && path.getFileName()
                                                                .toString()
                                                                .endsWith("." + extension))
                                .forEach(path -> Pathway.tryRun(() -> Files.deleteIfExists(path)));
                    }
                });
    }

    private static Map<String, String> detectMissingDependencies(String path) {
        return Pathway.tryOf(
                        () -> {
                            var missing = new LinkedHashMap<String, String>();
                            var process =
                                    new ProcessBuilder("ldd", path)
                                            .redirectErrorStream(true)
                                            .start();
                            try {
                                if (!process.waitFor(5, TimeUnit.SECONDS)) return missing;
                                try (var input = process.inputReader()) {
                                    input.lines()
                                            .filter(line -> line.contains("not found"))
                                            .forEach(
                                                    line ->
                                                            missing.put(
                                                                    line.split("=>", 2)[0]
                                                                            .trim()
                                                                            .replaceAll(
                                                                                    "\\.so\\.[\\d.]+$",
                                                                                    ".so"),
                                                                    line.trim()));
                                }
                            } finally {
                                if (process.isAlive()) process.destroyForcibly();
                            }
                            return missing;
                        })
                .recover(IOException.class, error -> new LinkedHashMap<>())
                .run()
                .getOrThrow();
    }

    private static DistroType detectDistro() {
        if (Files.exists(Path.of("/etc/debian_version"))) return DistroType.DEB;
        if (Files.exists(Path.of("/etc/redhat-release"))) return DistroType.RPM;
        if (Files.exists(Path.of("/etc/alpine-release"))) return DistroType.ALPINE;
        return DistroType.UNKNOWN;
    }

    private static String installCommand(Set<String> missing, DistroType distro) {
        var packages =
                Pathway.listPath(new ArrayList<>(missing))
                        .filter(KNOWN_DEPENDENCIES::containsKey)
                        .map(KNOWN_DEPENDENCIES::get)
                        .run();
        if (packages.isEmpty()) return "";
        String deb =
                String.join(
                        " ", Pathway.listPath(packages).map(DistroPackages::deb).distinct().run());
        String rpm =
                String.join(
                        " ", Pathway.listPath(packages).map(DistroPackages::rpm).distinct().run());
        String alpine =
                String.join(
                        " ",
                        Pathway.listPath(packages).map(DistroPackages::alpine).distinct().run());
        return switch (distro) {
            case DEB -> "sudo apt-get update && sudo apt-get install -y " + deb;
            case RPM -> "sudo yum install -y " + rpm;
            case ALPINE -> "apk add --no-cache " + alpine;
            case UNKNOWN ->
                    "(Debian/Ubuntu) sudo apt-get install -y "
                            + deb
                            + "\n  (RHEL/CentOS) sudo yum install -y "
                            + rpm
                            + "\n  (Alpine/Docker) apk add --no-cache "
                            + alpine;
        };
    }

    private static void reportFailure(String module, String file, Throwable error) {
        LOGGER.error("====== 物理库 ({}) 加载失败 ======", module);
        LOGGER.error("原因: {}", error.getMessage());
        if (error instanceof NativeLoadFailedException nativeError) {
            LOGGER.error("文件: {}", nativeError.path);
            var missing = detectMissingDependencies(nativeError.path);
            if (missing.isEmpty()) {
                var match =
                        Pattern.compile(":\\s+(\\S+\\.so[\\d.]*)\\s*:")
                                .matcher(String.valueOf(nativeError.getCause().getMessage()));
                if (match.find())
                    missing.put(
                            match.group(1).replaceAll("\\.so\\.[\\d.]+$", ".so"), match.group(1));
            }
            if (!missing.isEmpty()) {
                missing.forEach((name, line) -> LOGGER.error("  {}", line));
                LOGGER.error("安装命令: {}", installCommand(missing.keySet(), detectDistro()));
            }
        }
        LOGGER.error("==============================================");
    }
}
