package com.bupt.smartta.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a TA applicant (student) in the Smart-TA system.
 *
 * <p>Each applicant is identified by a system-assigned ID (e.g., "A001") and holds
 * personal, academic, and skills information used by the AI scoring engine to match
 * applicants with positions. A {@link User} account with the "TA" role is linked to
 * this record via the {@code applicantId} field.</p>
 *
 * <p>Scores are computed dynamically using the following weights:
 * <ul>
 *   <li>Skill match: 40%</li>
 *   <li>GPA (normalised to 4.0 scale): 30%</li>
 *   <li>Weekly availability (20 h/week = full score): 30%</li>
 * </ul>
 * </p>
 *
 * @see User
 * @see Application
 * @see com.bupt.smartta.model.Position
 */
public class TAPplicant implements Serializable {
    private static final long serialVersionUID = 1L;

    /** System-assigned unique ID (e.g., "A001"). */
    private String id;
    /** Full name of the applicant. */
    private String name;
    /** Email address of the applicant. */
    private String email;
    /** Year of study (e.g., "Year 2"). */
    private String yearOfStudy;
    /** Grade Point Average on a 4.0 scale. */
    private double gpa;
    /** List of skills the applicant possesses. */
    private List<String> skills;
    /** Available hours per week for TA work. */
    private int hoursAvailable;
    /** UUID filename of the uploaded CV in the {@code cv_uploads/} directory. */
    private String cvFileName;
    /** Record creation date in ISO format (yyyy-MM-dd). */
    private String createdAt;

    /**
     * Default constructor. Initialises skills to an empty list.
     */
    public TAPplicant() {
        this.skills = new ArrayList<>();
    }

    /**
     * Creates a new TA applicant with all required information.
     *
     * @param id             system-assigned unique ID
     * @param name          full display name
     * @param email         email address
     * @param yearOfStudy   year of study
     * @param gpa           GPA on a 4.0 scale
     * @param skills        list of skills (may be null)
     * @param hoursAvailable available hours per week
     */
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
     * Counts how many of the required skills the applicant possesses.
     * Comparison is case-insensitive.
     *
     * @param requiredSkills the list of skills required for a position (may be null or empty)
     * @return the number of matching skills, or 0 if either list is null or empty
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
     * Computes the AI composite match score (0-100) for a given position.
     *
     * <p>Formula: {@code 0.4 × skillScore + 0.3 × gpaScore + 0.3 × availScore}</p>
     *
     * <ul>
     *   <li>Skill score = (matched skills / required skills) × 100</li>
     *   <li>GPA score = (gpa / 4.0) × 100</li>
     *   <li>Availability score = (hoursAvailable / 20) × 100</li>
     * </ul>
     *
     * @param requiredSkills the list of skills required for the position
     * @param positionHours the weekly hours required by the position
     * @return the composite score rounded to the nearest integer (0-100)
     */
    public double computeAIScore(List<String> requiredSkills, int positionHours) {
        int reqCount = requiredSkills != null ? requiredSkills.size() : 0;
        double skillScore = reqCount > 0
            ? ((double) getMatchedSkillCount(requiredSkills) / reqCount) * 100 : 0;
        double gpaScore = Math.min((gpa / 4.0), 1.0) * 100;
        double availScore = Math.min(((double) hoursAvailable / 20.0), 1.0) * 100;
        return Math.round(0.4 * skillScore + 0.3 * gpaScore + 0.3 * availScore);
    }
}
