package com.bupt.smartta.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class JsonFileStore {
    private static final ObjectMapper MAPPER;
    private final String dataDir;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public JsonFileStore(String dataDir) {
        this.dataDir = dataDir;
        ensureDir();
    }

    private void ensureDir() {
        try {
            Files.createDirectories(Paths.get(dataDir));
        } catch (IOException e) {
            System.err.println("[JsonFileStore] Failed to create data directory: " + e.getMessage());
        }
    }

    private String getFilePath(String name) {
        return dataDir + File.separator + name + ".json";
    }

    private String getLockFilePath(String name) {
        return dataDir + File.separator + name + ".lock";
    }

    /**
     * 线程安全的文件保存方法。
     * 使用<strong>独立 .lock 文件</strong>加锁（不要锁目标 .json）：在 Windows 上若对 users.json
     * 打开 RandomAccessFile 并加锁，则无法再用 Files.move 覆盖同一文件，会导致 “Failed to save users.json”。
     * 保存策略：锁 .lock → 备份 → 写临时文件 → 覆盖目标（优先 ATOMIC_MOVE，失败则 copy）。
     */
    public <T> void save(String name, List<T> data) throws IOException {
        Path path = Paths.get(getFilePath(name));
        Path tmpPath = Paths.get(getFilePath(name + ".tmp"));
        Path bakPath = Paths.get(getFilePath(name + ".bak"));
        Path lockPath = Paths.get(getLockFilePath(name));
        String json = MAPPER.writeValueAsString(data);

        int retries = 3;
        for (int attempt = 0; attempt < retries; attempt++) {
            try (RandomAccessFile raf = new RandomAccessFile(lockPath.toFile(), "rw");
                 FileChannel channel = raf.getChannel()) {
                FileLock lock = channel.lock();
                try {
                    if (Files.exists(path)) {
                        Files.copy(path, bakPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    Files.writeString(tmpPath, json,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.SYNC);
                    moveOnto(path, tmpPath);
                    return;
                } finally {
                    lock.release();
                    Files.deleteIfExists(tmpPath);
                }
            } catch (IOException e) {
                Files.deleteIfExists(tmpPath);
                if (attempt == retries - 1) {
                    System.err.println("[JsonFileStore] Failed to save " + name + ".json after " + retries + " attempts: " + e.getMessage());
                    throw e;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for file lock", ie);
                }
            }
        }
    }

    /** 将临时文件覆盖到目标；Windows 上 ATOMIC_MOVE 失败时退回 copy。 */
    private void moveOnto(Path target, Path tmp) throws IOException {
        try {
            Files.move(tmp, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.copy(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * 保存单个对象（Map/List 等），同样使用文件锁和原子写入。
     */
    public <T> void saveObject(String name, T obj) throws IOException {
        Path path = Paths.get(getFilePath(name));
        Path tmpPath = Paths.get(getFilePath(name + ".tmp"));
        Path bakPath = Paths.get(getFilePath(name + ".bak"));
        Path lockPath = Paths.get(getLockFilePath(name));
        String json = MAPPER.writeValueAsString(obj);

        int retries = 3;
        for (int attempt = 0; attempt < retries; attempt++) {
            try (RandomAccessFile raf = new RandomAccessFile(lockPath.toFile(), "rw");
                 FileChannel channel = raf.getChannel()) {
                FileLock lock = channel.lock();
                try {
                    if (Files.exists(path)) {
                        Files.copy(path, bakPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    Files.writeString(tmpPath, json,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.SYNC);
                    moveOnto(path, tmpPath);
                    return;
                } finally {
                    lock.release();
                    Files.deleteIfExists(tmpPath);
                }
            } catch (IOException e) {
                Files.deleteIfExists(tmpPath);
                if (attempt == retries - 1) {
                    System.err.println("[JsonFileStore] Failed to save " + name + ".json (object) after " + retries + " attempts: " + e.getMessage());
                    throw e;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for file lock", ie);
                }
            }
        }
    }

    /**
     * 从备份文件恢复数据（当主文件损坏时）。
     * @param name 数据文件名（不含 .json 后缀）
     * @return true 如果恢复成功
     */
    public boolean restoreFromBackup(String name) {
        Path path = Paths.get(getFilePath(name));
        Path bakPath = Paths.get(getFilePath(name + ".bak"));
        if (!Files.exists(bakPath)) return false;
        try {
            Files.copy(bakPath, path, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[JsonFileStore] Restored " + name + ".json from backup");
            return true;
        } catch (IOException e) {
            System.err.println("[JsonFileStore] Failed to restore " + name + ".json from backup: " + e.getMessage());
            return false;
        }
    }

    /**
     * 读取 JSON 列表数据。文件不存在时返回空列表。
     * 如果主文件损坏，尝试从备份恢复。
     */
    public <T> List<T> load(String name, TypeReference<List<T>> typeRef) {
        try {
            File f = new File(getFilePath(name));
            if (!f.exists()) return new ArrayList<>();
            String json = Files.readString(f.toPath());
            return MAPPER.readValue(json, typeRef);
        } catch (IOException e) {
            System.err.println("[JsonFileStore] Failed to load " + name + ".json: " + e.getMessage());
            // 尝试从备份恢复
            if (restoreFromBackup(name)) {
                try {
                    String json = Files.readString(Paths.get(getFilePath(name)));
                    return MAPPER.readValue(json, typeRef);
                } catch (IOException ex) {
                    System.err.println("[JsonFileStore] Also failed to read backup: " + ex.getMessage());
                }
            }
            return new ArrayList<>();
        }
    }

    /**
     * 读取 JSON 对象数据。文件不存在时返回 null。
     */
    public <T> T loadObject(String name, Class<T> clazz) {
        try {
            File f = new File(getFilePath(name));
            if (!f.exists()) return null;
            String json = Files.readString(f.toPath());
            return MAPPER.readValue(json, clazz);
        } catch (IOException e) {
            System.err.println("[JsonFileStore] Failed to load " + name + ".json (object): " + e.getMessage());
            if (restoreFromBackup(name)) {
                try {
                    String json = Files.readString(Paths.get(getFilePath(name)));
                    return MAPPER.readValue(json, clazz);
                } catch (IOException ex) {
                    System.err.println("[JsonFileStore] Also failed to read backup: " + ex.getMessage());
                }
            }
            return null;
        }
    }

    public boolean fileExists(String name) {
        return new File(getFilePath(name)).exists();
    }

    public String getDataDir() { return dataDir; }
}
