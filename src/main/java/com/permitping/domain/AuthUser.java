package com.permitping.domain;

public record AuthUser(long id, String username, String displayName, Role role, boolean active) {
    public AuthUser {
        if (username == null || username.isBlank() || role == null) throw new IllegalArgumentException("Invalid authenticated user");
    }
    public boolean allows(Permission permission) { return active && role.allows(permission); }
}
