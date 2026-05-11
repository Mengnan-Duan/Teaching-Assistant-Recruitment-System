package com.bupt.smartta.util;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connects to the Bailian (Qwen) API to generate match analysis for TA applicants
 * and AI-powered workload rebalancing advice.
 *
 * <p>This service reads the API key from the application's {@code .env} file or
 * the {@code DASHSCOPE_API_KEY} environment variable, caches results in memory
 * to avoid repeated API calls for identical queries, and falls back gracefully
 * to template-based explanations when the API is unavailable.</p>
 *
 * <p>Two main features:</p>
 * <ul>
 *   <li><strong>Match Analysis</strong> — generates a human-readable paragraph
 *       explaining why a given applicant matches (or does not match) a position</li>
 *   <li><strong>Workload Rebalancing</strong> — analyses current TA workloads,
 *       identifies overloaded TAs, and suggests concrete adjustments</li>
 * </ul>
 *
 * <p>This class is a singleton: obtain the instance via {@link #getInstance()}.</p>
 *
 * @see #generateMatchAnalysis(String, String, double, int, String, String, int, int, int, int, int)
 * @see #generateWorkloadRebalanceAdvice(List, int)
 */
public class LLMService {

    private static final String BAILIAN_BASE_URL =
        "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String BAILIAN_MODEL = "qwen-plus";

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
     * Loads the Bailian (Qwen) API key from the application's .env file.
     * Looks for .env in the same directory as SmartTA.war is expanded,
     * then falls back to the catalina.base system property, then CLASSPATH.
     */
    private String loadApiKey() {
        if (cachedApiKey != null) return cachedApiKey;

        // Try environment variable first (works on most platforms / IDE runners)
        String envKey = System.getenv("DASHSCOPE_API_KEY");
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
                        if ("DASHSCOPE_API_KEY".equals(key) && isNonEmpty(val)) {
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
     * Generates a human-readable match analysis using Bailian (Qwen) LLM.
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
            String analysis = callBailian(apiKey, applicantName, applicantSkills,
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

    private String callBailian(String apiKey,
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
            """, BAILIAN_MODEL, escapeJson(prompt));

        HttpURLConnection conn = (HttpURLConnection) new URL(BAILIAN_BASE_URL).openConnection();
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
                throw new IOException("Bailian API key is invalid or unauthorized (401)");
            }
            if (httpCode != 200) {
                throw new IOException("Bailian API returned HTTP " + httpCode);
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
     * Cache key prefix for workload rebalancing suggestions.
     * Identical workload snapshots produce identical advice, avoiding redundant API calls.
     */
    private static final String WORKLOAD_CACHE_KEY = "workload_rebalance_v1";

    /**
     * Internal record representing a single TA's workload entry.
     */
    public static class WorkloadEntry {
        public final String taApplicantId;
        public final String taDisplayName;
        public final String taUsername;
        public final int currentHours;
        /** List of the TA's accepted positions (code -> hours per week). */
        public final Map<String, Integer> positions; // positionCode -> hoursPerWeek

        public WorkloadEntry(String taApplicantId, String taDisplayName,
                             String taUsername, int currentHours,
                             Map<String, Integer> positions) {
            this.taApplicantId = taApplicantId;
            this.taDisplayName = taDisplayName;
            this.taUsername = taUsername;
            this.currentHours = currentHours;
            this.positions = positions != null ? new HashMap<>(positions) : new HashMap<>();
        }
    }

    /**
     * AI-powered workload rebalancing advice returned by the Bailian LLM.
     *
     * @param action              "reduce" or "no_action"
     * @param targetApplicantId   ID of the overloaded TA (may be null)
     * @param targetDisplayName   Display name of the target TA (may be null)
     * @param targetPositionCode  Code of the position the AI recommends removing (may be null)
     * @param targetPositionName  Name of the position the AI recommends removing (may be null)
     * @param targetHoursDelta    How many hours the removal saves (may be null)
     * @param reasoning           English reasoning explaining the recommendation
     * @param summary             Concise summary for UI display
     */
    public static class RebalanceAdvice {
        public final String action;
        public final String targetApplicantId;
        public final String targetDisplayName;
        public final String targetPositionCode;
        public final String targetPositionName;
        public final Integer targetHoursDelta;
        public final String reasoning;
        public final String summary;

        public RebalanceAdvice(String action, String targetApplicantId,
                               String targetDisplayName, String targetPositionCode,
                               String targetPositionName, Integer targetHoursDelta,
                               String reasoning, String summary) {
            this.action = action;
            this.targetApplicantId = targetApplicantId;
            this.targetDisplayName = targetDisplayName;
            this.targetPositionCode = targetPositionCode;
            this.targetPositionName = targetPositionName;
            this.targetHoursDelta = targetHoursDelta;
            this.reasoning = reasoning;
            this.summary = summary;
        }
    }

    /**
     * Requests AI-powered workload rebalancing advice from the Bailian LLM.
     *
     * <p>Only calls the API when overloaded TAs are detected; otherwise returns
     * {@code no_action} immediately. On API failure, returns {@code null} and the
     * caller should fall back to fixed business rules.</p>
     *
     * @param entries  current workload data for all TAs
     * @param capacity safe weekly hours upper limit (e.g., 20 h/week)
     * @return Bailian advice, or {@code null} if the API is unavailable
     */
    public RebalanceAdvice generateWorkloadRebalanceAdvice(
            List<WorkloadEntry> entries, int capacity) {

        if (entries == null || entries.isEmpty()) {
            return new RebalanceAdvice("no_action", null, null, null, null, null,
                    "No TA data available", "No data — no adjustment needed.");
        }

        // Check for overloaded TAs
        List<WorkloadEntry> overloaded = entries.stream()
                .filter(e -> e.currentHours > capacity)
                .toList();

        if (overloaded.isEmpty()) {
            return new RebalanceAdvice("no_action", null, null, null, null, null,
                    "All TAs within safe range", "All TAs are within the " + capacity + "h/week safe range — no adjustment needed.");
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
            return null; // Caller falls back to fixed rules
        }

        try {
            RebalanceAdvice advice = callBailianWorkload(apiKey, entries, capacity, overloaded);
            if (advice != null) {
                cache.put(cacheKey, "AI|" + serializeAdvice(advice));
            }
            return advice;
        } catch (Exception e) {
            System.err.println("[LLMService] Workload rebalance API failed: " + e.getMessage());
            return null; // Caller falls back to fixed rules
        }
    }

    private RebalanceAdvice callBailianWorkload(String apiKey,
            List<WorkloadEntry> entries, int capacity,
            List<WorkloadEntry> overloaded) throws IOException {

        // Build TA list text
        StringBuilder taList = new StringBuilder();
        for (WorkloadEntry e : entries) {
            String status = e.currentHours > capacity
                    ? "[OVERLOAD] " + e.currentHours + "h"
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

            【Overloaded TAs (current > %d hours)】
            %s

            Please output ONLY in the following JSON format (output JSON only, no other text):
            {
              "action": "reduce" | "no_action",
              "targetApplicantId": "The applicantId of the overloaded TA (e.g. A001), null if action is no_action",
              "targetDisplayName": "Full name of the overloaded TA, null if no_action",
              "targetPositionCode": "The position code this TA should give up (e.g. CST302), null if no_action",
              "targetPositionName": "Full name of the position to give up, null if no_action",
              "targetHoursDelta": An integer (hours saved by removing that position), null if no_action",
              "reasoning": "Your reasoning in English (within 120 words), explain why this TA should remove this specific position",
              "summary": "A concise summary for the administrator in English (within 50 words)"
            }

            Notes:
            - Only output "reduce" when there are overloaded TAs
            - Choose ONE position to remove from ONE overloaded TA that best reduces their workload
            - The targetPositionCode MUST be a position the target TA currently holds (from their accepted positions list above)
            - Only output one recommendation (the TA most in need of adjustment, and ONE position)
            """, capacity, taList.toString().trim(), capacity, overloadList.toString().trim());

        String jsonBody = String.format("""
            {
              "model": "%s",
              "messages": [
                {"role": "user", "content": %s}
              ],
              "max_tokens": 450,
              "temperature": 0.2
            }
            """, BAILIAN_MODEL, escapeJson(prompt));

        HttpURLConnection conn = (HttpURLConnection) new URL(BAILIAN_BASE_URL).openConnection();
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
                throw new IOException("Bailian API key invalid (401)");
            }
            if (httpCode != 200) {
                throw new IOException("Bailian API HTTP " + httpCode);
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
     * Parses a JSON advice object returned by the LLM.
     * Has moderate tolerance for format deviations.
     */
    private RebalanceAdvice parseAdviceFromLLM(String content) {
        try {
            content = content.trim();
            if (content.startsWith("```")) {
                int firstNewline = content.indexOf('\n');
                if (firstNewline >= 0) content = content.substring(firstNewline + 1).trim();
                int lastBacktick = content.lastIndexOf("```");
                if (lastBacktick >= 0) content = content.substring(0, lastBacktick).trim();
            }

            String action = extractJsonField(content, "action");
            String targetId = extractJsonField(content, "targetApplicantId");
            if (targetId != null && (targetId.equals("null") || targetId.isEmpty())) targetId = null;
            String targetName = extractJsonField(content, "targetDisplayName");
            if (targetName != null && (targetName.equals("null") || targetName.isEmpty())) targetName = null;
            String targetPosCode = extractJsonField(content, "targetPositionCode");
            if (targetPosCode != null && (targetPosCode.equals("null") || targetPosCode.isEmpty())) targetPosCode = null;
            String targetPosName = extractJsonField(content, "targetPositionName");
            if (targetPosName != null && (targetPosName.equals("null") || targetPosName.isEmpty())) targetPosName = null;
            String hoursDeltaStr = extractJsonField(content, "targetHoursDelta");
            Integer hoursDelta = null;
            if (hoursDeltaStr != null && !hoursDeltaStr.equals("null") && !hoursDeltaStr.isEmpty()) {
                try { hoursDelta = Integer.parseInt(hoursDeltaStr.replaceAll("[^0-9-]", "")); } catch (Exception ignored) {}
            }
            String reasoning = extractJsonField(content, "reasoning");
            String summary = extractJsonField(content, "summary");

            return new RebalanceAdvice(
                    action != null ? action : "reduce",
                    targetId, targetName, targetPosCode, targetPosName, hoursDelta,
                    reasoning != null ? reasoning : "",
                    summary != null ? summary : "Please review the details.");
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
            // String value
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
            // Numeric value
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-' || json.charAt(end) == '.')) end++;
            return json.substring(start, end);
        }
    }

    private String serializeAdvice(RebalanceAdvice a) {
        return a.action + "|" + (a.targetApplicantId != null ? a.targetApplicantId : "")
                + "|" + (a.targetDisplayName != null ? a.targetDisplayName : "")
                + "|" + (a.targetPositionCode != null ? a.targetPositionCode : "")
                + "|" + (a.targetPositionName != null ? a.targetPositionName : "")
                + "|" + (a.targetHoursDelta != null ? a.targetHoursDelta : "")
                + "|" + (a.reasoning != null ? a.reasoning : "")
                + "|" + (a.summary != null ? a.summary : "");
    }

    private RebalanceAdvice parseCachedAdvice(String cached) {
        String[] parts = cached.substring(3).split("\\|", 8);
        return new RebalanceAdvice(
                parts.length > 0 ? parts[0] : "no_action",
                parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null,
                parts.length > 2 && !parts[2].isEmpty() ? parts[2] : null,
                parts.length > 3 && !parts[3].isEmpty() ? parts[3] : null,
                parts.length > 4 && !parts[4].isEmpty() ? parts[4] : null,
                parts.length > 5 && !parts[5].isEmpty() ? Integer.parseInt(parts[5]) : null,
                parts.length > 6 ? parts[6] : "",
                parts.length > 7 ? parts[7] : "");
    }
}
