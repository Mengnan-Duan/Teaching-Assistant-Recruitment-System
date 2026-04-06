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
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getYearOfStudy() { return yearOfStudy; }
    public void setYearOfStudy(String yearOfStudy) { this.yearOfStudy = yearOfStudy; }
    public double getGpa() { return gpa; }
    public void setGpa(double gpa) { this.gpa = gpa; }
    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }
    public int getHoursAvailable() { return hoursAvailable; }
    public void setHoursAvailable(int hoursAvailable) { this.hoursAvailable = hoursAvailable; }
    public String getCvFileName() { return cvFileName; }
    public void setCvFileName(String cvFileName) { this.cvFileName = cvFileName; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public int getMatchedSkillCount(List<String> requiredSkills) {
        int count = 0;
        for (String req : requiredSkills) {
            for (String has : skills) {
                if (has.equalsIgnoreCase(req.trim())) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    public double computeAIScore(List<String> requiredSkills, int positionHours) {
        int reqCount = requiredSkills.size();
        double skillScore = reqCount > 0
            ? ((double) getMatchedSkillCount(requiredSkills) / reqCount) * 100 : 0;
        double gpaScore = (gpa / 4.0) * 100;
        double availScore = Math.min(((double) hoursAvailable / 20.0) * 100, 100);
        return Math.round(0.4 * skillScore + 0.3 * gpaScore + 0.3 * availScore);
    }
}
