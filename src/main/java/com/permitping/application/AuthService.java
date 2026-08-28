package com.permitping.application;

import com.permitping.domain.*;
import java.util.List;
import java.util.Locale;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

public final class AuthService {
    private static final int MAX_FAILURES = 5; private static final long LOCK_MILLIS = 60_000;
    private final AuthRepository repository; private final PasswordHasher hasher; private final Clock clock; private final AuditService audit; private final ConcurrentHashMap<String, LoginState> failures = new ConcurrentHashMap<>();
    public AuthService(AuthRepository repository) { this(repository, new PasswordHasher(), Clock.systemUTC(), null); }
    public AuthService(AuthRepository repository, PasswordHasher hasher) { this(repository, hasher, Clock.systemUTC(), null); }
    public AuthService(AuthRepository repository, PasswordHasher hasher, Clock clock) { this(repository, hasher, clock, null); }
    public AuthService(AuthRepository repository, AuditService audit) { this(repository, new PasswordHasher(), Clock.systemUTC(), audit); }
    public AuthService(AuthRepository repository, PasswordHasher hasher, Clock clock, AuditService audit) { this.repository = repository; this.hasher = hasher; this.clock = clock; this.audit = audit; }
    public boolean isProvisioned() { return repository.countUsers() > 0; }
    public void registerFirstAdmin(String username, String displayName, char[] password) { if (isProvisioned()) throw new SecurityException("Initial administrator is already configured"); String normalized = normalize(username); repository.saveUser(normalized, requiredName(displayName), hasher.hash(password), Role.ADMIN); record("Created initial administrator", normalized); }
    public AuthUser authenticate(String username, char[] password) {
        if (username == null || password == null) return null;
        try {
            String normalized;
            try { normalized = normalize(username); } catch (IllegalArgumentException ex) { return null; }
            long now=clock.millis(); LoginState state=failures.get(normalized);
            if(state!=null && now-state.lastFailureMillis()<LOCK_MILLIS && state.count()>=MAX_FAILURES) return null;
            AuthRepository.StoredUser stored = repository.findByUsername(normalized);
            if (stored == null || !stored.user().active() || !hasher.matches(password, stored.passwordHash())) {
                failures.compute(normalized,(key,current)->new LoginState(current==null?1:current.count()+1,now)); return null;
            }
            failures.remove(normalized); return stored.user();
        } finally { java.util.Arrays.fill(password, '\0'); }
    }
    public AuthUser createUser(AuthUser actor, String username, String displayName, char[] password, Role role) { require(actor, Permission.MANAGE_USERS); if (role == null) throw new IllegalArgumentException("Role is required"); Role storedRole = repository.findRole(role.name()); repository.saveUser(normalize(username), requiredName(displayName), hasher.hash(password), storedRole); AuthUser created = repository.findByUsername(normalize(username)).user(); record("Created user", created.username() + " as " + storedRole.name()); return created; }
    public List<AuthUser> users(AuthUser actor) { require(actor, Permission.MANAGE_USERS); return repository.findUsers(); }
    public List<Role> roles(AuthUser actor) { require(actor, Permission.MANAGE_USERS); return repository.findRoles(); }
    public Role createRole(AuthUser actor, String name, Set<Permission> permissions) { require(actor, Permission.MANAGE_USERS); Role role = Role.custom(name, permissions); repository.saveRole(role); record("Created role", role.name() + " with " + role.permissions().size() + " permissions"); return role; }
    public Role updateRole(AuthUser actor, String name, Set<Permission> permissions) { require(actor, Permission.MANAGE_USERS); Role current = repository.findRole(name); if (current.system()) throw new SecurityException("Built-in roles cannot be changed"); Role role = Role.custom(current.name(), permissions); repository.updateRole(role); record("Updated role permissions", role.name()); return role; }
    public void deleteRole(AuthUser actor, String name) { require(actor, Permission.MANAGE_USERS); Role role = repository.findRole(name); if (role.system()) throw new SecurityException("Built-in roles cannot be deleted"); if (repository.countUsersWithRole(role.name()) > 0) throw new IllegalStateException("Reassign users before deleting this role"); repository.deleteRole(role.name()); record("Deleted role", role.name()); }
    public void setActive(AuthUser actor, long userId, boolean active) { require(actor, Permission.MANAGE_USERS); if (actor.id() == userId && !active) throw new SecurityException("You cannot deactivate your own account"); repository.setActive(userId, active); record(active ? "Activated user" : "Deactivated user", Long.toString(userId)); }
    public void setRole(AuthUser actor, long userId, Role role) { require(actor, Permission.MANAGE_USERS); if (role == null) throw new IllegalArgumentException("Role is required"); if (actor.id() == userId && !role.equals(Role.ADMIN)) throw new SecurityException("You cannot remove your own administrator role"); Role storedRole = repository.findRole(role.name()); repository.setRole(userId, storedRole); record("Changed user role", userId + " -> " + storedRole.name()); }
    public void changePassword(AuthUser actor, char[] currentPassword, char[] newPassword) {
        AuthRepository.StoredUser stored = null;
        try {
            AuthUser current = validateSession(actor);
            stored = repository.findByUsername(current.username());
            if (stored == null || !hasher.matches(currentPassword, stored.passwordHash())) throw new SecurityException("Current password is incorrect");
            repository.updatePassword(current.id(), hasher.hash(newPassword));
            record("Changed own password", current.username());
        } finally {
            clear(currentPassword); clear(newPassword);
        }
    }
    public void resetPassword(AuthUser actor, long userId, char[] newPassword) {
        try {
            require(actor, Permission.MANAGE_USERS);
            repository.updatePassword(userId, hasher.hash(newPassword));
            record("Reset user password", Long.toString(userId));
        } finally { clear(newPassword); }
    }
    public void require(AuthUser actor, Permission permission) {
        if (permission == null || !validateSession(actor).allows(permission)) {
            throw new SecurityException("Permission denied");
        }
    }
    public AuthUser validateSession(AuthUser actor) {
        if (actor == null) throw new SecurityException("Session expired");
        AuthRepository.StoredUser current = repository.findByUsername(actor.username());
        if (current == null || current.user().id() != actor.id() || !current.user().active()) throw new SecurityException("Session expired");
        return current.user();
    }
    private String normalize(String username) { if (username == null || !username.matches("[A-Za-z0-9._-]{3,80}")) throw new IllegalArgumentException("Username must be 3-80 characters and contain only letters, numbers, dots, underscores, or hyphens"); return username.toLowerCase(Locale.ROOT); }
    private String requiredName(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("Display name is required"); return value.trim(); }
    private void clear(char[] value) { if (value != null) java.util.Arrays.fill(value, '\0'); }
    private void record(String action, String subject) { if (audit != null) audit.record(action, subject); }
    private record LoginState(long count, long lastFailureMillis) { }
}
