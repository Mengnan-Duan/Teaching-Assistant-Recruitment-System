package com.bupt.smartta.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

public class JsonFileStore {
    private final String dataDir;

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

    /** Save a list of beans (uses reflection to serialize public fields) */
    public <T> void save(String name, List<T> data) throws IOException {
        Path path = Paths.get(getFilePath(name));
        Files.writeString(path, toJson(data));
    }

    /** Load a list of beans (uses reflection to deserialize public fields) */
    public <T> List<T> load(String name, Class<T> elementType) {
        try {
            File f = new File(getFilePath(name));
            if (!f.exists()) return new ArrayList<>();
            String json = Files.readString(f.toPath());
            return parseListOfObjects(json, elementType);
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    /** Save a top-level object / map (e.g. HashMap) */
    public <T> void saveObject(String name, Object obj) throws IOException {
        Path path = Paths.get(getFilePath(name));
        Files.writeString(path, toJson(obj));
    }

    /** Load a top-level object / map */
    @SuppressWarnings("unchecked")
    public <T> T loadObject(String name, Class<T> clazz) {
        try {
            File f = new File(getFilePath(name));
            if (!f.exists()) return null;
            String json = Files.readString(f.toPath());
            if (clazz == HashMap.class || clazz == LinkedHashMap.class) {
                return (T) parseMap(json);
            }
            return parseObject(json, clazz);
        } catch (IOException e) {
            return null;
        }
    }

    public boolean fileExists(String name) {
        return new File(getFilePath(name)).exists();
    }

    public String getDataDir() { return dataDir; }

    // ==================== JSON Writer ====================

    private String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            List<?> list = (List<?>) obj;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj instanceof java.util.Set) {
            StringBuilder sb = new StringBuilder("[");
            int i = 0;
            for (Object item : (java.util.Set<?>) obj) {
                if (i > 0) sb.append(",");
                sb.append(toJson(item));
                i++;
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj instanceof HashMap || obj instanceof LinkedHashMap) {
            StringBuilder sb = new StringBuilder("{");
            int i = 0;
            for (Object e : ((HashMap<?, ?>) obj).entrySet()) {
                if (i > 0) sb.append(",");
                java.util.Map.Entry<?, ?> entry = (java.util.Map.Entry<?, ?>) e;
                sb.append("\"").append(esc(entry.getKey().toString())).append("\":");
                sb.append(toJson(entry.getValue()));
                i++;
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof String) return "\"" + esc((String) obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        // Fallback: treat as bean (public fields)
        return beanToJson(obj);
    }

    private String beanToJson(Object obj) {
        StringBuilder sb = new StringBuilder("{");
        java.lang.reflect.Field[] fields = obj.getClass().getDeclaredFields();
        int i = 0;
        for (java.lang.reflect.Field f : fields) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            if (i > 0) sb.append(",");
            sb.append("\"").append(esc(f.getName())).append("\":");
            try {
                Object val = f.get(obj);
                sb.append(toJson(val));
            } catch (IllegalAccessException e) {
                sb.append("null");
            }
            i++;
        }
        sb.append("}");
        return sb.toString();
    }

    // ==================== JSON Parser ====================

    @SuppressWarnings("unchecked")
    private <T> List<T> parseListOfObjects(String json, Class<T> elementType) {
        List<Object> rawList = parseArray(json);
        List<T> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof HashMap) {
                T obj = mapToBean((HashMap<String, Object>) item, elementType);
                result.add(obj);
            }
        }
        return result;
    }

    private HashMap<String, Object> parseMap(String json) {
        Object result = parseValue(json, 0).first;
        if (result instanceof HashMap) return (HashMap<String, Object>) result;
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private <T> T parseObject(String json, Class<T> clazz) {
        Object result = parseValue(json, 0).first;
        if (result instanceof HashMap) {
            return mapToBean((HashMap<String, Object>) result, clazz);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> T mapToBean(HashMap<String, Object> map, Class<T> clazz) {
        try {
            T obj = clazz.getDeclaredConstructor().newInstance();
            for (Field f : clazz.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                String name = f.getName();
                if (!map.containsKey(name)) continue;
                Object val = map.get(name);
                Class<?> ft = f.getType();
                Type gen = f.getGenericType();
                if (val == null) {
                    if (ft == String.class) {
                        try { f.set(obj, ""); } catch (IllegalAccessException ignored) { }
                    }
                    continue;
                }
                if (ft == String.class) {
                    f.set(obj, val.toString());
                } else if (ft == int.class || ft == Integer.class) {
                    f.setInt(obj, toInt(val));
                } else if (ft == double.class || ft == Double.class) {
                    f.setDouble(obj, toDouble(val));
                } else if (ft == long.class || ft == Long.class) {
                    f.setLong(obj, toLong(val));
                } else if (ft == boolean.class || ft == Boolean.class) {
                    f.setBoolean(obj, toBool(val));
                } else if (ft == HashSet.class || ft == java.util.Set.class) {
                    if (val instanceof List) {
                        java.util.Set<String> set = new HashSet<>();
                        for (Object o : (List<?>) val) set.add(o == null ? null : o.toString());
                        f.set(obj, set);
                    }
                } else if (ft == ArrayList.class || ft == List.class) {
                    if (val instanceof List) {
                        List<?> rawList = (List<?>) val;
                        if (gen instanceof ParameterizedType) {
                            Type arg0 = ((ParameterizedType) gen).getActualTypeArguments()[0];
                            if (arg0 instanceof Class) {
                                Class<?> elemClass = (Class<?>) arg0;
                                List<Object> out = new ArrayList<>();
                                for (Object item : rawList) {
                                    if (elemClass == String.class) {
                                        out.add(item == null ? "" : item.toString());
                                    } else if (item instanceof HashMap) {
                                        out.add(mapToBean((HashMap<String, Object>) item, elemClass));
                                    } else {
                                        out.add(item);
                                    }
                                }
                                f.set(obj, out);
                            } else {
                                f.set(obj, new ArrayList<>(rawList));
                            }
                        } else {
                            f.set(obj, new ArrayList<>(rawList));
                        }
                    }
                } else if (val instanceof HashMap && !java.util.Map.class.isAssignableFrom(ft)) {
                    if (!ft.isPrimitive() && ft != String.class && !Number.class.isAssignableFrom(ft)
                            && ft != Boolean.class && ft != Character.class) {
                        f.set(obj, mapToBean((HashMap<String, Object>) val, ft));
                    }
                }
            }
            return obj;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private int toInt(Object v) {
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString().trim()); } catch (Exception e) { return 0; }
    }

    private double toDouble(Object v) {
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString().trim()); } catch (Exception e) { return 0.0; }
    }

    private long toLong(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString().trim()); } catch (Exception e) { return 0L; }
    }

    private boolean toBool(Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        String s = v.toString().trim();
        return "true".equalsIgnoreCase(s);
    }

    private List<Object> parseArray(String json) {
        Pair<Object, Integer> r = parseValue(json.trim(), 0);
        if (r.first instanceof List) return (List<Object>) r.first;
        return new ArrayList<>();
    }

    private Pair<Object, Integer> parseValue(String json, int i) {
        i = skipWhitespace(json, i);
        if (i >= json.length()) return new Pair<>(null, i);
        char c = json.charAt(i);
        if (c == '"') { Pair<String, Integer> p = parseString(json, i + 1); return new Pair<>(p.first, p.second); }
        if (c == '{') { Pair<HashMap<String, Object>, Integer> p = parseObject(json, i); return new Pair<>((HashMap<String, Object>) p.first, p.second); }
        if (c == '[') { Pair<List<Object>, Integer> p = parseArray2(json, i); return new Pair<>((List<Object>) p.first, p.second); }
        if (c == 'n') return new Pair<>(null, i + 4);
        if (c == 't') return new Pair<>(Boolean.TRUE, i + 4);
        if (c == 'f') return new Pair<>(Boolean.FALSE, i + 5);
        return parseNumber(json, i);
    }

    private Pair<Object, Integer> parseNumber(String json, int i) {
        int j = i;
        boolean isDouble = false;
        while (j < json.length()) {
            char c = json.charAt(j);
            if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') { isDouble = true; j++; continue; }
            if (c >= '0' && c <= '9') { j++; continue; }
            break;
        }
        String num = json.substring(i, j).trim();
        if (num.isEmpty()) return new Pair<>(0, j);
        try {
            if (isDouble || num.contains(".") || num.contains("e") || num.contains("E")) {
                return new Pair<>(Double.parseDouble(num), j);
            } else {
                return new Pair<>(Integer.parseInt(num), j);
            }
        } catch (NumberFormatException e) {
            return new Pair<>(0, j);
        }
    }

    private Pair<String, Integer> parseString(String json, int i) {
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(i + 1);
                if (n == 'n') { sb.append('\n'); i += 2; }
                else if (n == 't') { sb.append('\t'); i += 2; }
                else if (n == '"') { sb.append('"'); i += 2; }
                else if (n == '\\') { sb.append('\\'); i += 2; }
                else if (n == 'r') { sb.append('\r'); i += 2; }
                else if (n == 'u' && i + 5 < json.length()) {
                    String hex = json.substring(i + 2, i + 6);
                    sb.append((char) Integer.parseInt(hex, 16));
                    i += 6;
                } else { sb.append(n); i += 2; }
            } else if (c == '"') {
                return new Pair<>(sb.toString(), i + 1);
            } else {
                sb.append(c);
                i++;
            }
        }
        return new Pair<>(sb.toString(), i);
    }

    private Pair<HashMap<String, Object>, Integer> parseObject(String json, int i) {
        HashMap<String, Object> map = new LinkedHashMap<>();
        i++; // skip '{'
        while (i < json.length()) {
            i = skipWhitespace(json, i);
            if (i < json.length() && json.charAt(i) == '}') return new Pair<>(map, i + 1);
            if (i < json.length() && json.charAt(i) == ',') { i++; continue; }
            Pair<String, Integer> keyPair = parseString(json, i);
            String key = keyPair.first;
            i = skipWhitespace(json, keyPair.second);
            if (i < json.length() && json.charAt(i) == ':') i++;
            i = skipWhitespace(json, i);
            Pair<Object, Integer> valPair = parseValue(json, i);
            map.put(key, valPair.first);
            i = skipWhitespace(json, valPair.second);
            if (i < json.length() && json.charAt(i) == ',') i++;
        }
        return new Pair<>(map, i);
    }

    private Pair<List<Object>, Integer> parseArray2(String json, int i) {
        List<Object> list = new ArrayList<>();
        i++; // skip '['
        while (i < json.length()) {
            i = skipWhitespace(json, i);
            if (i < json.length() && json.charAt(i) == ']') return new Pair<>(list, i + 1);
            if (i < json.length() && json.charAt(i) == ',') { i++; continue; }
            Pair<Object, Integer> valPair = parseValue(json, i);
            list.add(valPair.first);
            i = skipWhitespace(json, valPair.second);
            if (i < json.length() && json.charAt(i) == ',') i++;
        }
        return new Pair<>(list, i);
    }

    private int skipWhitespace(String json, int i) {
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        return i;
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static class Pair<T, U> {
        T first;
        U second;
        Pair(T first, U second) { this.first = first; this.second = second; }
    }
}
