package com.permitping;

import com.permitping.application.*;
import com.permitping.domain.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class AuthServiceTest {
    @Test void provisionsOnlyOneInitialAdminAndNeverStoresPlaintext() {
        MemoryAuthRepository repository = new MemoryAuthRepository(); AuthService auth = new AuthService(repository);
        char[] password = "correct horse battery staple".toCharArray(); auth.registerFirstAdmin("Owner", "Owner", password);
        AuthUser owner = auth.authenticate("owner", "correct horse battery staple".toCharArray());
        assertNotNull(owner); assertEquals(Role.ADMIN, owner.role()); assertNotEquals("correct horse battery staple", repository.password);
        assertThrows(SecurityException.class, () -> auth.registerFirstAdmin("second", "Second", "another secure password".toCharArray()));
    }
    @Test void deniesUnauthorizedUserManagementAndDeactivationOfSelf() {
        MemoryAuthRepository repository = new MemoryAuthRepository(); AuthService auth = new AuthService(repository);
        auth.registerFirstAdmin("owner", "Owner", "correct horse battery staple".toCharArray()); AuthUser owner = auth.authenticate("owner", "correct horse battery staple".toCharArray());
        AuthUser viewer = auth.createUser(owner, "viewer", "Viewer", "viewer secure password".toCharArray(), Role.VIEWER);
        assertThrows(SecurityException.class, () -> auth.users(viewer)); assertThrows(SecurityException.class, () -> auth.setActive(owner, owner.id(), false));
        AuthUser forgedAdmin = new AuthUser(viewer.id(), viewer.username(), viewer.displayName(), Role.ADMIN, true);
        assertThrows(SecurityException.class, () -> auth.users(forgedAdmin));
        assertFalse(viewer.allows(Permission.MANAGE_DOCUMENTS)); assertTrue(owner.allows(Permission.MANAGE_USERS));
    }
    @Test void throttlesRepeatedInvalidPasswordsAndFailsInvalidUsernamesSafely() {
        MemoryAuthRepository repository = new MemoryAuthRepository(); AuthService auth = new AuthService(repository);
        auth.registerFirstAdmin("owner", "Owner", "correct horse battery staple".toCharArray());
        for (int attempt = 0; attempt < 5; attempt++) assertNull(auth.authenticate("owner", "wrong password".toCharArray()));
        assertNull(auth.authenticate("owner", "correct horse battery staple".toCharArray()));
        assertNull(auth.authenticate("bad user!", "wrong password".toCharArray()));
    }
    @Test void rejectsMalformedPasswordHashes() {
        PasswordHasher hasher = new PasswordHasher();
        assertFalse(hasher.matches("a secure password".toCharArray(), "pbkdf2_sha256$1$bad$bad"));
        assertThrows(IllegalArgumentException.class, () -> hasher.hash("short".toCharArray()));
    }
    @Test void invalidatesAStoredSessionAfterAccountDeactivation() {
        MemoryAuthRepository repository = new MemoryAuthRepository(); AuthService auth = new AuthService(repository);
        auth.registerFirstAdmin("owner", "Owner", "correct horse battery staple".toCharArray());
        AuthUser owner = auth.authenticate("owner", "correct horse battery staple".toCharArray());
        AuthUser viewer = auth.createUser(owner, "viewer", "Viewer", "viewer secure password".toCharArray(), Role.VIEWER);
        auth.setActive(owner, viewer.id(), false);

        assertThrows(SecurityException.class, () -> auth.validateSession(viewer));
    }
    @Test void changesOwnPasswordOnlyAfterVerifyingTheCurrentPassword() {
        MemoryAuthRepository repository = new MemoryAuthRepository(); AuthService auth = new AuthService(repository);
        auth.registerFirstAdmin("owner", "Owner", "correct horse battery staple".toCharArray());
        AuthUser owner = auth.authenticate("owner", "correct horse battery staple".toCharArray());

        assertThrows(SecurityException.class, () -> auth.changePassword(owner, "wrong current password".toCharArray(), "new secure password".toCharArray()));
        assertNotNull(auth.authenticate("owner", "correct horse battery staple".toCharArray()));

        auth.changePassword(owner, "correct horse battery staple".toCharArray(), "new secure password".toCharArray());
        assertNull(auth.authenticate("owner", "correct horse battery staple".toCharArray()));
        assertNotNull(auth.authenticate("owner", "new secure password".toCharArray()));
    }
    @Test void administratorCanResetAnotherUsersPassword() {
        MemoryAuthRepository repository = new MemoryAuthRepository(); AuthService auth = new AuthService(repository);
        auth.registerFirstAdmin("owner", "Owner", "correct horse battery staple".toCharArray());
        AuthUser owner = auth.authenticate("owner", "correct horse battery staple".toCharArray());
        AuthUser viewer = auth.createUser(owner, "viewer", "Viewer", "viewer secure password".toCharArray(), Role.VIEWER);

        auth.resetPassword(owner, viewer.id(), "reset secure password".toCharArray());

        assertNull(auth.authenticate("viewer", "viewer secure password".toCharArray()));
        assertNotNull(auth.authenticate("viewer", "reset secure password".toCharArray()));
        assertThrows(SecurityException.class, () -> auth.resetPassword(viewer, owner.id(), "not allowed password".toCharArray()));
    }
    private static final class MemoryAuthRepository implements AuthRepository {
        String password; long next=1; final Map<String,StoredUser> users=new LinkedHashMap<>();
        public long countUsers(){return users.size();}
        public void saveUser(String username,String displayName,String passwordHash,Role role){password=passwordHash;users.put(username,new StoredUser(new AuthUser(next++,username,displayName,role,true),passwordHash));}
        public void updatePassword(long id,String passwordHash){users.values().stream().filter(u->u.user().id()==id).findFirst().ifPresent(u->{password=passwordHash;users.put(u.user().username(),new StoredUser(u.user(),passwordHash));});}
        public StoredUser findByUsername(String username){return users.get(username);}
        public List<AuthUser> findUsers(){return users.values().stream().map(StoredUser::user).toList();}
        public void setActive(long id,boolean active){users.values().stream().filter(u->u.user().id()==id).findFirst().ifPresent(u->users.put(u.user().username(),new StoredUser(new AuthUser(id,u.user().username(),u.user().displayName(),u.user().role(),active),u.passwordHash())));}
        public void setRole(long id,Role role){users.values().stream().filter(u->u.user().id()==id).findFirst().ifPresent(u->users.put(u.user().username(),new StoredUser(new AuthUser(id,u.user().username(),u.user().displayName(),role,u.user().active()),u.passwordHash())));}
    }
}
