package com.bupt.smartta.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            e.printStackTrace();
        }
    }

    private String getFilePath(String name) {
        return dataDir + File.separator + name + ".json";
    }

    public <T> void save(String name, List<T> data) throws IOException {
        Path path = Paths.get(getFilePath(name));
        String json = MAPPER.writeValueAsString(data);
        Files.writeString(path, json);
    }

    public <T> List<T> load(String name, TypeReference<List<T>> typeRef) {
        try {
            File f = new File(getFilePath(name));
            if (!f.exists()) return new ArrayList<>();
            String json = Files.readString(f.toPath());
            return MAPPER.readValue(json, typeRef);
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public <T> void saveObject(String name, T obj) throws IOException {
        Path path = Paths.get(getFilePath(name));
        String json = MAPPER.writeValueAsString(obj);
        Files.writeString(path, json);
    }

    public <T> T loadObject(String name, Class<T> clazz) {
        try {
            File f = new File(getFilePath(name));
            if (!f.exists()) return null;
            String json = Files.readString(f.toPath());
            return MAPPER.readValue(json, clazz);
        } catch (IOException e) {
            return null;
        }
    }

    public boolean fileExists(String name) {
        return new File(getFilePath(name)).exists();
    }

    public String getDataDir() { return dataDir; }
}
