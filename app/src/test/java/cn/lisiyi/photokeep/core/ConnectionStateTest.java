package cn.lisiyi.photokeep.core;

import cn.lisiyi.photokeep.data.State;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConnectionStateTest {
    private static final String FIRST = "11111111-1111-4111-8111-111111111111";
    private static final String SECOND = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";

    private State connected() {
        State s = new State(); s.clientId = FIRST; s.accountId = "account"; s.accountLabel = "label";
        s.driveId = "drive"; s.folders.add("Pictures/"); s.cloudRoots.put("root", "Photos");
        s.automatic = true; s.scheduled = true; s.lastScan = 100;
        s.bindings.put("cloud", new Binding("cloud", "local", "a".repeat(64), 100, "test.jpg", "Pictures/", "Photos/test.jpg"));
        return s;
    }
    @Test public void switchingRegistrationRequiresFreshAccountAndMapping() {
        State s = connected(); s.changeClientId(SECOND);
        assertEquals(SECOND, s.clientId); assertTrue(s.accountId.isEmpty()); assertTrue(s.driveId.isEmpty());
        assertTrue(s.cloudRoots.isEmpty()); assertTrue(s.bindings.isEmpty());
        assertFalse(s.automatic); assertFalse(s.scheduled); assertEquals(0, s.lastScan);
        assertTrue(s.folders.contains("Pictures/"));
    }
    @Test public void sameRegistrationPreservesExistingConnection() {
        State s = connected(); s.changeClientId("  " + FIRST + "  ");
        assertEquals("account", s.accountId); assertEquals(1, s.bindings.size()); assertTrue(s.automatic);
    }
    @Test public void invalidRegistrationDoesNotMutateConnection() {
        for (String invalid : new String[]{"", "not-an-id", "00000000-0000-0000-0000-000000000000"}) {
            State s = connected(); assertThrows(IllegalArgumentException.class, () -> s.changeClientId(invalid));
            assertEquals(FIRST, s.clientId); assertEquals("account", s.accountId); assertTrue(s.automatic);
        }
    }
    @Test public void oldLoginCallbackCannotBindAccountToAnotherRegistration() {
        State s = connected(); s.changeClientId(SECOND);
        assertFalse(s.completeLogin(FIRST, "old-account", "old label"));
        assertTrue(s.accountId.isEmpty()); assertFalse(s.automatic);
        assertTrue(s.completeLogin(SECOND, "new-account", "new label"));
        assertEquals("new-account", s.accountId);
    }
    @Test public void changingAccountStopsScheduledCleanupAndClearsCloudScope() {
        State s = connected(); assertTrue(s.completeLogin(FIRST, "other-account", "other label"));
        assertTrue(s.cloudRoots.isEmpty()); assertTrue(s.bindings.isEmpty()); assertFalse(s.scheduled); assertFalse(s.automatic);
    }
}
