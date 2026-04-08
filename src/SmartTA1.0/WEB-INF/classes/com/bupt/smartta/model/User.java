package com.bupt.smartta.model;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private String passwordHash;
    private String displayName;
    private String email;
    private String createdAt;
    private Set<String> roles; // "TA", "MO", "ADMIN"
    private String applicantId; // linked TA applicant ID

    public User() {
        this.roles = new HashSet<>();
    }

    public User(String username, String passwordHash, String displayName, String email) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.email = email;
        this.roles = new HashSet<>();
        this.createdAt = java.time.LocalDate.now().toString();
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }
    public String getApplicantId() { return applicantId; }
    public void setApplicantId(String applicantId) { this.applicantId = applicantId; }

    public void addRole(String role) { roles.add(role); }
    public void removeRole(String role) { roles.remove(role); }
    public boolean hasRole(String role) { return roles.contains(role); }

    public boolean checkPassword(String password) {
        return passwordHash != null && passwordHash.equals(hashPassword(password));
    }

    public static String hashPassword(String password) {
        if (password == null) return "";
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return password;
        }
    }
}
