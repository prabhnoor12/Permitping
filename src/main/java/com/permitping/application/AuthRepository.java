package com.permitping.application;

import com.permitping.domain.AuthUser;
import com.permitping.domain.Role;
import java.util.List;

public interface AuthRepository {
    long countUsers();
    void saveUser(String username, String displayName, String passwordHash, Role role);
    StoredUser findByUsername(String username);
    List<AuthUser> findUsers();
    void setActive(long id, boolean active);
    void setRole(long id, Role role);
    default void updatePassword(long id, String passwordHash) { throw new UnsupportedOperationException("Password changes are not supported by this repository"); }
    default List<Role> findRoles() { return List.of(Role.ADMIN, Role.MANAGER, Role.EDITOR, Role.VIEWER); }
    default Role findRole(String name) { return Role.builtIn(name); }
    default void saveRole(Role role) { throw new UnsupportedOperationException("Custom roles are not supported by this repository"); }
    default void updateRole(Role role) { throw new UnsupportedOperationException("Custom roles are not supported by this repository"); }
    default void deleteRole(String name) { throw new UnsupportedOperationException("Custom roles are not supported by this repository"); }
    default long countUsersWithRole(String name) { return 0; }
    record StoredUser(AuthUser user, String passwordHash) { }
}
