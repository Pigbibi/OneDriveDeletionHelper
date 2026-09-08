package cn.lisiyi.photokeep.core;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.util.*;
import static org.junit.Assert.*;

public class MatchingAndHashTest {
    private Media local(String key, String name, String sha) { return new Media(key, "DCIM/Camera/", name, 100, 1, 1, sha, false, false); }
    @Test public void matchesAcrossDifferentYearMonthLayout() {
        List<Binding> links = new Matcher().propose(List.of(local("1", "a.jpg", "a".repeat(64))), List.of(new CloudItem("remote", "a.jpg", "Pictures/2026/09/a.jpg", 100, "v1", false)), Set.of("DCIM/"), List.of());
        assertEquals(1, links.size()); assertEquals("remote", links.get(0).cloudId);
    }
    @Test public void ambiguousCloudDuplicatesAreNotPaired() {
        assertTrue(new Matcher().propose(List.of(local("1", "a.jpg", "a".repeat(64))), List.of(new CloudItem("1", "a.jpg", "A", 100,"v1",false),new CloudItem("2", "a.jpg", "B",100,"v1",false)), Set.of("DCIM/"), List.of()).isEmpty());
    }
    @Test public void sameRemoteCannotBeClaimedByTwoDifferentLocalFiles() {
        assertTrue(new Matcher().propose(List.of(local("1", "a.jpg", "a".repeat(64)), local("2", "b.jpg", "b".repeat(64))), List.of(new CloudItem("1", "c.jpg", "A",100,"v1",false)), Set.of("DCIM/"), List.of()).isEmpty());
    }
    @Test public void scopeDoesNotMatchSiblingPrefix() { assertFalse(DeletionPolicy.isWithin("Pictures-old/", "Pictures/")); }
    @Test public void hashRequiresCompleteOriginalBytes() throws Exception {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ContentHash.sha256(new ByteArrayInputStream("abc".getBytes()),3,()->false));
        assertThrows(Exception.class, () -> ContentHash.sha256(new ByteArrayInputStream("ab".getBytes()),3,()->false));
        assertThrows(Exception.class, () -> ContentHash.sha256(new ByteArrayInputStream("abcd".getBytes()),3,()->false));
        assertThrows(Exception.class, () -> ContentHash.sha256(new ByteArrayInputStream("abc".getBytes()),3,()->true));
    }
    @Test public void historicalCloudOnlyFileNeverCreatesBinding() {
        assertTrue(new Matcher().propose(List.of(), List.of(new CloudItem("old", "old.jpg", "2020/",100,"v1",false)), Set.of("DCIM/"), List.of()).isEmpty());
    }
}
