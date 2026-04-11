package com.bupt.smartta.util;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connects to DeepSeek API to generate match analysis for TA applicants.
 * Reads API key from the application's .env file.
 */
public class LLMService {

    private static final String DEEPSEEK_BASE_URL =
        "https://api.deepseek.com/chat/completions";
    private static final String DEEPSEEK_MODEL = "deepseek-chat";

    private static volatile LLMService instance;
    private static volatile String cachedApiKey;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    private LLMService() {}

    public static LLMService getInstance() {
        if (instance == null) {
            synchronized (LLMService.class) {
                if (instance == null) instance = new LLMService();
            }
        }
        return instance;
    }

    /**
     * Loads the DeepSeek API key from the application's .env file.
     * Looks for .env in the same directory as SmartTA.war is expanded,
     * then falls back to the catalina.base system property, then CLASSPATH.
     */
    private String loadApiKey() {
        if (cachedApiKey != null) return cachedApiKey;

        // Try environment variable first (works on most platforms / IDE runners)
        String envKey = System.getenv("DEEPSEEK_API_KEY");
        if (isNonEmpty(envKey)) {
            cachedApiKey = envKey.trim();
            return cachedApiKey;
        }

        // Try catalina.base (Tomcat's base directory)
        String catalinaBase = System.getProperty("catalina.base");
        String[] searchPaths = {
            catalinaBase != null ? catalinaBase + "/webapps/SmartTA/.env" : null,
            System.getProperty("user.dir") + "/.env",
            "D:/Tomcat/apache-tomcat-10.1.48/webapps/SmartTA/.env",
        };

        for (String path : searchPaths) {
            if (path == null) continue;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        String key = line.substring(0, eq).trim();
                        String val = line.substring(eq + 1).trim();
                        if ("DEEPSEEK_API_KEY".equals(key) && isNonEmpty(val)) {
                            cachedApiKey = val;
                            return cachedApiKey;
                        }
                    }
                }
            } catch (IOException ignored) {}
        }

        return null;
    }

    /**
     * Generates a human-readable match analysis using DeepSeek LLM.
     * The result is cached by applicant+position so repeated calls are fast.
     *
     * @return a Chinese paragraph analysing why this applicant matches the position;
     *         falls back to a template string if the API call fails or the key is missing
     */
    public String generateMatchAnalysis(
            String applicantName, String applicantSkills,
            double gpa, int hoursAvailable,
            String positionName, String requiredSkills,
            int positionHours, int skillScore, int gpaScore, int availScore, int totalScore) {

        String cacheKey = String.format("%s|%s|%s|%d", applicantName, requiredSkills, positionName, totalScore);
        if (cache.containsKey(cacheKey)) return cache.get(cacheKey);

        String apiKey = loadApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            String fallback = buildFallbackExplanation(applicantName, positionName,
                    skillScore, gpaScore, availScore, totalScore);
            cache.put(cacheKey, fallback);
            return fallback;
        }

        try {
            String analysis = callDeepSeek(apiKey, applicantName, applicantSkills,
                    gpa, hoursAvailable, positionName, requiredSkills,
                    positionHours, skillScore, gpaScore, availScore, totalScore);
            cache.put(cacheKey, analysis);
            return analysis;
        } catch (Exception e) {
            String fallback = buildFallbackExplanation(applicantName, positionName,
                    skillScore, gpaScore, availScore, totalScore);
            cache.put(cacheKey, fallback);
            return fallback;
        }
    }

    private String callDeepSeek(String apiKey,
            String applicantName, String applicantSkills,
            double gpa, int hoursAvailable,
            String positionName, String requiredSkills,
            int positionHours, int skillScore, int gpaScore, int availScore, int totalScore)
            throws IOException {

        String prompt = buildPrompt(applicantName, applicantSkills, gpa, hoursAvailable,
                positionName, requiredSkills, positionHours,
                skillScore, gpaScore, availScore, totalScore);

        String jsonBody = String.format("""
            {
              "model": "%s",
              "messages": [
                {"role": "user", "content": %s}
              ],
              "max_tokens": 400,
              "temperature": 0.3
            }
            """, DEEPSEEK_MODEL, escapeJson(prompt));

        HttpURLConnection conn = (HttpURLConnection) new URL(DEEPSEEK_BASE_URL).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int httpCode = conn.getResponseCode();
            if (httpCode == 401) {
                throw new IOException("DeepSeek API key is invalid or unauthorized (401)");
            }
            if (httpCode != 200) {
                throw new IOException("DeepSeek API returned HTTP " + httpCode);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            String raw = response.toString();
            return extractContent(raw);

        } finally {
            conn.disconnect();
        }
    }

    private String buildPrompt(
            String applicantName, String applicantSkills,
            double gpa, int hoursAvailable,
            String positionName, String requiredSkills,
            int positionHours,
            int skillScore, int gpaScore, int availScore, int totalScore) {

        return String.format("""
            You are a TA recruitment matching analysis assistant. Based on the information below, generate a concise professional match analysis in English (within 100-200 words), explaining this applicant's strengths and gaps for the position.

            【Applicant Info】
            Name: %s
            Skills: %s
            GPA: %.2f / 4.0
            Available Hours: %d hours/week

            【Position Info】
            Position: %s
            Required Skills: %s
            Weekly Hours Required: %d hours

            【Match Scores (0-100)】
            Total Score: %d (Skills %d%% + GPA %d%% + Availability %d%%)

            Output only the analysis text, no markers or formatting.
            """,
            applicantName, applicantSkills,
            gpa, hoursAvailable,
            positionName, requiredSkills,
            positionHours,
            totalScore, skillScore, gpaScore, availScore);
    }

    private String buildFallbackExplanation(
            String applicantName, String positionName,
            int skillScore, int gpaScore, int availScore, int totalScore) {

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[AI Match Analysis] Total Score: %d/100\n\n", totalScore));
        sb.append(String.format("Applicant %s for %s:\n\n", applicantName, positionName));
        sb.append(String.format("Skill Match: %d%% - ", skillScore));
        if (skillScore >= 80) sb.append("Highly matching. Can handle core role requirements.");
        else if (skillScore >= 50) sb.append("Partially matching. Some skills need improvement.");
        else sb.append("Low match. Recommended to strengthen relevant skills.");
        sb.append("\n");
        sb.append(String.format("GPA Score: %d%% - ", gpaScore));
        if (gpaScore >= 80) sb.append("Excellent academic performance. Solid theoretical foundation.");
        else if (gpaScore >= 60) sb.append("Good academic standing. Meets course requirements.");
        else sb.append("Academic performance needs improvement.");
        sb.append("\n");
        sb.append(String.format("Availability: %d%% - ", availScore));
        if (availScore >= 80) sb.append("Sufficient time. Can fully commit to TA duties.");
        else if (availScore >= 50) sb.append("Adequate time, but schedule management required.");
        else sb.append("Limited time. Evaluate if coursework can be balanced.");
        sb.append("\n\nOverall: ");
        if (totalScore >= 80) sb.append("Highly recommended. Excellent match for this position.");
        else if (totalScore >= 60) sb.append("Consider favorably. Solid overall profile.");
        else sb.append("Proceed with caution. Evaluate other factors.");
        return sb.toString();
    }

    private String extractContent(String raw) {
        int idx = raw.indexOf("\"content\":\"");
        if (idx < 0) return null;
        int start = idx + 10;
        int end = raw.indexOf("\"", start);
        if (end < 0) return null;
        String content = raw.substring(start, end);
        return content
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static String escapeJson(String s) {
        if (s == null) return "\"\"";
        return "\"" + s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }

    private static boolean isNonEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /** Clears the in-memory cache. Useful after deployment. */
    public void clearCache() {
        cache.clear();
    }

    // ============================================================
    // AI Workload Rebalancing
    // ============================================================

    /**
     * 工作负载再平衡建议缓存 Key。
     * 相同工时快照产生相同建议，避免重复 API 调用。
     */
    private static final String WORKLOAD_CACHE_KEY = "workload_rebalance_v1";

    /**
     * 内部：一条 TA 工时记录。
     */
    public static class WorkloadEntry {
        public final String taApplicantId;
        public final String taDisplayName;
        public final String taUsername;
        public final int currentHours;

        public WorkloadEntry(String taApplicantId, String taDisplayName,
                             String taUsername, int currentHours) {
            this.taApplicantId = taApplicantId;
            this.taDisplayName = taDisplayName;
            this.taUsername = taUsername;
            this.currentHours = currentHours;
        }
    }

    /**
     * DeepSeek 返回的再平衡建议。
     * action: "reduce" | "no_action"
     * targetApplicantId: 要调整工时的 TA ID（可为 null）
     * targetHours: 建议目标工时（可为 null）
     * reasoning: 中文推理说明
     * summary: 摘要（供 UI 显示）
     */
    public static class RebalanceAdvice {
        public final String action;
        public final String targetApplicantId;
        public final String targetDisplayName;
        public final Integer targetHours;
        public final String reasoning;
        public final String summary;

        public RebalanceAdvice(String action, String targetApplicantId,
                               String targetDisplayName, Integer targetHours,
                               String reasoning, String summary) {
            this.action = action;
            this.targetApplicantId = targetApplicantId;
            this.targetDisplayName = targetDisplayName;
            this.targetHours = targetHours;
            this.reasoning = reasoning;
            this.summary = summary;
        }
    }

    /**
     * 调用 DeepSeek 生成工作负载再平衡建议。
     * 仅在有过载 TA 时调用；无过载时返回 no_action。
     * 失败时返回 null Caller 应回退到固定规则。
     *
     * @param entries    当前所有 TA 的工时数据
     * @param capacity    安全工时上限（如 20 h/week）
     * @return            DeepSeek 建议，或 null（API 不可用）
     */
    public RebalanceAdvice generateWorkloadRebalanceAdvice(
            List<WorkloadEntry> entries, int capacity) {

        if (entries == null || entries.isEmpty()) {
            return new RebalanceAdvice("no_action", null, null, null,
                    "无 TA 数据", "暂无数据，无需调整");
        }

        // 检查是否有过载 TA
        List<WorkloadEntry> overloaded = entries.stream()
                .filter(e -> e.currentHours > capacity)
                .toList();

        if (overloaded.isEmpty()) {
            return new RebalanceAdvice("no_action", null, null, null,
                    "所有 TA 工时均在安全范围内", "当前所有 TA 工时均在 " + capacity + "h/week 安全范围内，无需调整");
        }

        String cacheKey = WORKLOAD_CACHE_KEY + "_"
                + entries.hashCode() + "_" + capacity;
        if (cache.containsKey(cacheKey)) {
            String cached = cache.get(cacheKey);
            if (cached != null && cached.startsWith("AI|")) {
                return parseCachedAdvice(cached);
            }
        }

        String apiKey = loadApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            return null; // Caller 回退到固定规则
        }

        try {
            RebalanceAdvice advice = callDeepSeekWorkload(apiKey, entries, capacity, overloaded);
            if (advice != null) {
                cache.put(cacheKey, "AI|" + serializeAdvice(advice));
            }
            return advice;
        } catch (Exception e) {
            System.err.println("[LLMService] Workload rebalance API failed: " + e.getMessage());
            return null; // Caller 回退到固定规则
        }
    }

    private RebalanceAdvice callDeepSeekWorkload(String apiKey,
            List<WorkloadEntry> entries, int capacity,
            List<WorkloadEntry> overloaded) throws IOException {

        // 构建 TA 列表文本
        StringBuilder taList = new StringBuilder();
        for (WorkloadEntry e : entries) {
            String status = e.currentHours > capacity
                    ? "【过载】" + e.currentHours + "h"
                    : e.currentHours + "h";
            taList.append(String.format("  - %s(@%s): %s\n",
                    e.taDisplayName, e.taUsername, status));
        }

        StringBuilder overloadList = new StringBuilder();
        for (WorkloadEntry e : overloaded) {
            overloadList.append(String.format("  - %s(@%s): %dh (exceeds capacity by %dh)\n",
                    e.taDisplayName, e.taUsername,
                    e.currentHours, e.currentHours - capacity));
        }

        String prompt = String.format("""
            You are a TA workload intelligent scheduling assistant. Analyze the weekly workload data of the following TAs and provide professional rebalancing advice in English.

            【System Safe Capacity】
            Maximum safe weekly hours per TA: %d hours

            【All TA Workloads】
            %s

            【Overloaded TAs】
            %s

            Please output ONLY in the following JSON format (output JSON only, no other text):
            {
              "action": "reduce" | "no_action",
              "targetApplicantId": "The applicantId of the overloaded TA (e.g. A001), null if action is no_action",
              "targetDisplayName": "Full name of the overloaded TA, null if no_action",
              "targetHours": An integer target hours (>= 0), null if no_action,
              "reasoning": "Your reasoning in English (within 100 words), explain why this TA and the suggested value",
              "summary": "A concise summary for the administrator in English (within 50 words)"
            }

            Notes:
            - Only output "reduce" when there are overloaded TAs
            - targetHours should be >= 0 and <= capacity
            - Prioritize adjusting overloaded TAs to the safe capacity value
            - Only output one recommendation (the TA most in need of adjustment)
            """, capacity, taList.toString().trim(), overloadList.toString().trim());

        String jsonBody = String.format("""
            {
              "model": "%s",
              "messages": [
                {"role": "user", "content": %s}
              ],
              "max_tokens": 350,
              "temperature": 0.2
            }
            """, DEEPSEEK_MODEL, escapeJson(prompt));

        HttpURLConnection conn = (HttpURLConnection) new URL(DEEPSEEK_BASE_URL).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int httpCode = conn.getResponseCode();
            if (httpCode == 401) {
                throw new IOException("DeepSeek API key invalid (401)");
            }
            if (httpCode != 200) {
                throw new IOException("DeepSeek API HTTP " + httpCode);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            String raw = response.toString();
            String content = extractContent(raw);
            if (content == null || content.isEmpty()) return null;
            return parseAdviceFromLLM(content);

        } finally {
            conn.disconnect();
        }
    }

    /**
     * 解析 LLM 返回的 JSON 建议。
     * 对格式偏差有一定容错能力。
     */
    private RebalanceAdvice parseAdviceFromLLM(String content) {
        try {
            content = content.trim();
            // 去掉可能的 markdown 代码块标记
            if (content.startsWith("```")) {
                int firstNewline = content.indexOf('\n');
                if (firstNewline >= 0) content = content.substring(firstNewline + 1).trim();
                int lastBacktick = content.lastIndexOf("```");
                if (lastBacktick >= 0) content = content.substring(0, lastBacktick).trim();
            }

            // 提取各字段（正则容错）
            String action = extractJsonField(content, "action");
            String targetId = extractJsonField(content, "targetApplicantId");
            if (targetId != null && (targetId.equals("null") || targetId.isEmpty())) targetId = null;
            String targetName = extractJsonField(content, "targetDisplayName");
            if (targetName != null && (targetName.equals("null") || targetName.isEmpty())) targetName = null;
            String hoursStr = extractJsonField(content, "targetHours");
            Integer hours = null;
            if (hoursStr != null && !hoursStr.equals("null") && !hoursStr.isEmpty()) {
                try { hours = Integer.parseInt(hoursStr.replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
            }
            String reasoning = extractJsonField(content, "reasoning");
            String summary = extractJsonField(content, "summary");

            return new RebalanceAdvice(
                    action != null ? action : "reduce",
                    targetId, targetName, hours,
                    reasoning != null ? reasoning : "",
                    summary != null ? summary : "请查看详情");
        } catch (Exception e) {
            System.err.println("[LLMService] Failed to parse workload advice: " + e.getMessage());
            return null;
        }
    }

    private String extractJsonField(String json, String field) {
        String search = "\"" + field + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(":", idx);
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return null;
        char c = json.charAt(start);
        if (c == '"') {
            // 字符串
            int end = start + 1;
            while (end < json.length()) {
                if (json.charAt(end) == '\\') { end += 2; continue; }
                if (json.charAt(end) == '"') { end++; break; }
                end++;
            }
            return json.substring(start + 1, end - 1);
        } else if (c == 'n' && json.substring(start).startsWith("null")) {
            return "null";
        } else {
            // 数字
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-' || json.charAt(end) == '.')) end++;
            return json.substring(start, end);
        }
    }

    private String serializeAdvice(RebalanceAdvice a) {
        return a.action + "|" + (a.targetApplicantId != null ? a.targetApplicantId : "")
                + "|" + (a.targetDisplayName != null ? a.targetDisplayName : "")
                + "|" + (a.targetHours != null ? a.targetHours : "")
                + "|" + (a.reasoning != null ? a.reasoning : "")
                + "|" + (a.summary != null ? a.summary : "");
    }

    private RebalanceAdvice parseCachedAdvice(String cached) {
        String[] parts = cached.substring(3).split("\\|", 6);
        return new RebalanceAdvice(
                parts.length > 0 ? parts[0] : "no_action",
                parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null,
                parts.length > 2 && !parts[2].isEmpty() ? parts[2] : null,
                parts.length > 3 && !parts[3].isEmpty() ? Integer.parseInt(parts[3]) : null,
                parts.length > 4 ? parts[4] : "",
                parts.length > 5 ? parts[5] : "");
    }
}
