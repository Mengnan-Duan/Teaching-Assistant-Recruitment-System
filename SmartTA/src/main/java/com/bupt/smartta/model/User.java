package com.bupt.smartta.model;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a system user account in the Smart-TA platform.
 *
 * <p>Users may hold one or more roles ({@code TA}, {@code MO}, {@code ADMIN}) and are linked
 * to a {@link TAPplicant} record when they hold the TA role. Passwords are stored as SHA-256
 * hashes and are never stored or transmitted in plain text.</p>
 *
 * <p>Demo accounts are pre-configured in {@code system_config.json} for evaluation purposes.</p>
 *
 * @see TAPplicant
 * @see com.bupt.smartta.servlet.AuthServlet
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Username, used as the unique login identifier. */
    private String username;
    /** SHA-256 hash of the user's password. */
    private String passwordHash;
    /** Display name shown in the UI (may differ from username). */
    private String displayName;
    /** User's email address. */
    private String email;
    /** Account creation date in ISO format (yyyy-MM-dd). */
    private String createdAt;
    /** Set of roles granted to this user. Possible values: "TA", "MO", "ADMIN". */
    private Set<String> roles;
    /** ID of the linked TA applicant record (only meaningful when the user has the TA role). */
    private String applicantId;

    /**
     * Default constructor. Initialises the roles set to an empty {@link HashSet}.
     */
    public User() {
        this.roles = new HashSet<>();
    }

    /**
     * Convenience constructor that creates a new user with basic information.
     *
     * @param username     the unique login name
     * @param passwordHash SHA-256 hash of the plaintext password
     * @param displayName  the name shown in the UI
     * @param email        the user's email address
     */
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

    /**
     * Grants the specified role to this user.
     *
     * @param role one of: "TA", "MO", "ADMIN"
     */
    public void addRole(String role) { roles.add(role); }

    /**
     * Revokes the specified role from this user.
     *
     * @param role one of: "TA", "MO", "ADMIN"
     */
    public void removeRole(String role) { roles.remove(role); }

    /**
     * Checks whether this user holds the given role.
     *
     * @param role the role to check
     * @return {@code true} if the user has the role, {@code false} otherwise
     */
    public boolean hasRole(String role) { return roles.contains(role); }

    /**
     * Verifies a plaintext password against the stored hash.
     *
     * @param password the plaintext password to verify
     * @return {@code true} if the password matches, {@code false} otherwise
     */
    public boolean checkPassword(String password) {
        return passwordHash != null && passwordHash.equals(hashPassword(password));
    }

    /**
     * Produces a SHA-256 hash of the given plaintext password.
     * This method is deterministic and used both for storage and verification.
     *
     * @param password the plaintext password
     * @return the hexadecimal SHA-256 hash string, or an empty string if the input is null
     */
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
