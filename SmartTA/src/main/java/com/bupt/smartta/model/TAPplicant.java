package com.bupt.smartta.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TAPplicant implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String email;
    private String yearOfStudy;
    private double gpa;
    private List<String> skills;
    private int hoursAvailable;
    private String cvFileName;
    private String createdAt;

    public TAPplicant() {
        this.skills = new ArrayList<>();
    }

    public TAPplicant(String id, String name, String email, String yearOfStudy,
                      double gpa, List<String> skills, int hoursAvailable) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.yearOfStudy = yearOfStudy;
        this.gpa = gpa;
        this.skills = skills != null ? skills : new ArrayList<>();
        this.hoursAvailable = hoursAvailable;
        this.createdAt = java.time.LocalDate.now().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email != null ? email : ""; }
    public void setEmail(String email) { this.email = email; }
    public String getYearOfStudy() { return yearOfStudy != null ? yearOfStudy : ""; }
    public void setYearOfStudy(String yearOfStudy) { this.yearOfStudy = yearOfStudy; }
    public double getGpa() { return gpa; }
    public void setGpa(double gpa) { this.gpa = gpa; }
    public List<String> getSkills() {
        if (skills == null) skills = new ArrayList<>();
        return skills;
    }
    public void setSkills(List<String> skills) {
        this.skills = skills != null ? skills : new ArrayList<>();
    }
    public int getHoursAvailable() { return hoursAvailable; }
    public void setHoursAvailable(int hoursAvailable) { this.hoursAvailable = hoursAvailable; }
    public String getCvFileName() { return cvFileName; }
    public void setCvFileName(String cvFileName) { this.cvFileName = cvFileName; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    /**
     * 计算与目标职位的技能匹配数量。
     * 安全的空值保护：requiredSkills 或 skills 为 null 时返回 0。
     */
    public int getMatchedSkillCount(List<String> requiredSkills) {
        if (requiredSkills == null || requiredSkills.isEmpty()) return 0;
        List<String> mySkills = getSkills();
        if (mySkills == null || mySkills.isEmpty()) return 0;

        int count = 0;
        for (String req : requiredSkills) {
            if (req == null || req.trim().isEmpty()) continue;
            for (String has : mySkills) {
                if (has != null && has.equalsIgnoreCase(req.trim())) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    /**
     * 计算 AI 综合匹配分数（0-100）。
     * 权重：技能匹配 40% + GPA 30% + 可用时间 30%
     */
    public double computeAIScore(List<String> requiredSkills, int positionHours) {
        // 技能匹配分数
        int reqCount = requiredSkills != null ? requiredSkills.size() : 0;
        double skillScore = reqCount > 0
            ? ((double) getMatchedSkillCount(requiredSkills) / reqCount) * 100 : 0;
        // GPA 分数（归一化到 4.0 满分）
        double gpaScore = Math.min((gpa / 4.0), 1.0) * 100;
        // 可用时间分数（以 20h/周为满分）
        double availScore = Math.min(((double) hoursAvailable / 20.0), 1.0) * 100;
        return Math.round(0.4 * skillScore + 0.3 * gpaScore + 0.3 * availScore);
    }
}
