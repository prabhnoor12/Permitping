package com.permitping.domain;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Immutable role definition. Built-in roles are constants; workspace roles are persisted definitions. */
public final class Role {
    public static final Role ADMIN = system("ADMIN", EnumSet.allOf(Permission.class));
    public static final Role MANAGER = system("MANAGER", EnumSet.of(Permission.VIEW_DOCUMENTS, Permission.MANAGE_DOCUMENTS, Permission.VIEW_PROFILES, Permission.MANAGE_PROFILES, Permission.MANAGE_ASSIGNMENTS, Permission.MANAGE_REMINDERS, Permission.VIEW_REPORTS, Permission.VIEW_AUDIT));
    public static final Role EDITOR = system("EDITOR", EnumSet.of(Permission.VIEW_DOCUMENTS, Permission.MANAGE_DOCUMENTS, Permission.VIEW_PROFILES, Permission.MANAGE_PROFILES, Permission.MANAGE_ASSIGNMENTS, Permission.VIEW_REPORTS));
    public static final Role VIEWER = system("VIEWER", EnumSet.of(Permission.VIEW_DOCUMENTS, Permission.VIEW_PROFILES, Permission.VIEW_REPORTS));
    private final String name;
    private final Set<Permission> permissions;
    private final boolean system;
    private Role(String name, Set<Permission> permissions, boolean system) { this.name = normalize(name); this.permissions = Set.copyOf(permissions); this.system = system; }
    private static Role system(String name, Set<Permission> permissions) { return new Role(name, permissions, true); }
    public static Role custom(String name, Set<Permission> permissions) { if (permissions == null) throw new IllegalArgumentException("Permissions are required"); return new Role(name, permissions, false); }
    public static Role builtIn(String name) { return switch (normalize(name)) { case "ADMIN" -> ADMIN; case "MANAGER" -> MANAGER; case "EDITOR" -> EDITOR; case "VIEWER" -> VIEWER; default -> throw new IllegalArgumentException("Unknown built-in role: " + name); }; }
    public String name() { return name; }
    public boolean system() { return system; }
    public boolean allows(Permission permission) { return permission != null && permissions.contains(permission); }
    public Set<Permission> permissions() { return permissions; }
    private static String normalize(String value) { if (value == null || !value.trim().matches("[A-Za-z][A-Za-z0-9 _-]{1,49}")) throw new IllegalArgumentException("Role name must be 2-50 characters and use letters, numbers, spaces, hyphens, or underscores"); return value.trim().toUpperCase(Locale.ROOT); }
    @Override public boolean equals(Object other) { return other instanceof Role role && name.equals(role.name); }
    @Override public int hashCode() { return Objects.hash(name); }
    @Override public String toString() { return name; }
}
