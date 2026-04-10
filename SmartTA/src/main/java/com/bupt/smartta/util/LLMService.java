package com.bupt.smartta.util;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
            你是一位 TA（教学助理）招聘匹配分析助手。请根据以下信息，用中文生成一段专业、简洁的匹配分析（100-200字以内），说明该申请者与职位的匹配程度和亮点/不足。

            【申请者信息】
            姓名：%s
            已有技能：%s
            GPA：%.2f / 4.0
            可用时间：每周 %d 小时

            【职位信息】
            职位名称：%s
            必要技能：%s
            每周工作时间要求：%d 小时

            【匹配评分（0-100）】
            综合分：%d（技能 %d%% + GPA %d%% + 可用时间 %d%%）

            请用中文输出分析内容，无需任何额外标记，直接输出正文。
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
        sb.append(String.format("【智能匹配分析】（综合分 %d/100）\n\n", totalScore));
        sb.append(String.format("申请者 %s 与职位 %s 的匹配情况：\n\n", applicantName, positionName));
        sb.append(String.format("• 技能匹配度：%d%% — ", skillScore));
        if (skillScore >= 80) sb.append("技能高度匹配，能够胜任岗位核心需求。");
        else if (skillScore >= 50) sb.append("技能基本匹配，部分能力有待补充。");
        else sb.append("技能匹配度较低，建议进一步提升相关技能。");
        sb.append("\n");
        sb.append(String.format("• GPA 表现：%d%% — ", gpaScore));
        if (gpaScore >= 80) sb.append("学术成绩优秀，具备扎实的理论基础。");
        else if (gpaScore >= 60) sb.append("学术表现良好，能满足课程要求。");
        else sb.append("学术成绩有待提高，建议关注专业知识。");
        sb.append("\n");
        sb.append(String.format("• 时间可用性：%d%% — ", availScore));
        if (availScore >= 80) sb.append("时间充裕，可全力投入助教工作。");
        else if (availScore >= 50) sb.append("时间基本满足，但需合理安排。");
        else sb.append("时间有限，需评估是否能兼顾课程。");
        sb.append("\n\n综合评分：");
        if (totalScore >= 80) sb.append("强烈推荐，该申请者高度匹配此职位。");
        else if (totalScore >= 60) sb.append("建议考虑，综合素质良好。");
        else sb.append("建议谨慎，需综合其他因素决定。");
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
}
