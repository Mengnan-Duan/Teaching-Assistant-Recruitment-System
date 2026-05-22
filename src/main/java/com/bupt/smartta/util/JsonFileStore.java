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
     * 线程安全的文件保存方法，使用文件锁防止并发写入导致的数据损坏。
     * 最多重试 3 次获取锁，每次等待 100ms。
     */
    public <T> void save(String name, List<T> data) throws IOException {
        Path path = Paths.get(getFilePath(name));
        Path lockPath = Paths.get(getLockFilePath(name));
        String json = MAPPER.writeValueAsString(data);

        int retries = 3;
        for (int attempt = 0; attempt < retries; attempt++) {
            try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw");
                 FileChannel channel = raf.getChannel()) {
                FileLock lock = channel.lock();
                try {
                    Files.writeString(path, json,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.SYNC);
                    return;
                } finally {
                    lock.release();
                }
            } catch (IOException e) {
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

    /**
     * 保存单个对象（Map/List 等），同样使用文件锁。
     */
    public <T> void saveObject(String name, T obj) throws IOException {
        Path path = Paths.get(getFilePath(name));
        Path lockPath = Paths.get(getLockFilePath(name));
        String json = MAPPER.writeValueAsString(obj);

        int retries = 3;
        for (int attempt = 0; attempt < retries; attempt++) {
            try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw");
                 FileChannel channel = raf.getChannel()) {
                FileLock lock = channel.lock();
                try {
                    Files.writeString(path, json,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.SYNC);
                    return;
                } finally {
                    lock.release();
                }
            } catch (IOException e) {
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
     * 读取 JSON 列表数据。文件不存在时返回空列表，不抛异常。
     */
    public <T> List<T> load(String name, TypeReference<List<T>> typeRef) {
        try {
            File f = new File(getFilePath(name));
            if (!f.exists()) return new ArrayList<>();
            String json = Files.readString(f.toPath());
            return MAPPER.readValue(json, typeRef);
        } catch (IOException e) {
            System.err.println("[JsonFileStore] Failed to load " + name + ".json: " + e.getMessage());
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
            return null;
        }
    }

    public boolean fileExists(String name) {
        return new File(getFilePath(name)).exists();
    }

    public String getDataDir() { return dataDir; }
}
