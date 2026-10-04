package com.android.killapps;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class SelectionPolicyTest {
    private static final Set<String> EMPTY = Collections.emptySet();
    private static final Set<String> LISTED = Collections.singleton("app.test");
    @Test public void whitelistKeepsListedApps() {
        assertFalse(SelectionPolicy.eligible("app.test", false, false, true, false, false, EMPTY, LISTED));
        assertTrue(SelectionPolicy.eligible("app.other", false, false, true, false, false, EMPTY, LISTED));
    }
    @Test public void blacklistOnlyStopsListedApps() {
        assertTrue(SelectionPolicy.eligible("app.test", false, false, true, false, true, LISTED, EMPTY));
        assertFalse(SelectionPolicy.eligible("app.other", false, false, true, false, true, LISTED, EMPTY));
        assertFalse(SelectionPolicy.eligible("app.test", false, false, true, false, true, EMPTY, EMPTY));
    }
    @Test public void protectionsOverrideListsAndFilters() {
        for (boolean blackMode : new boolean[]{false, true})
            assertFalse(SelectionPolicy.eligible("app.test", true, true, true, true, blackMode, LISTED, EMPTY));
    }
    @Test public void userAndSystemFiltersAreIndependent() {
        assertFalse(SelectionPolicy.eligible("app.test", true, false, true, false, false, EMPTY, EMPTY));
        assertFalse(SelectionPolicy.eligible("app.test", false, false, false, true, false, EMPTY, EMPTY));
        assertTrue(SelectionPolicy.eligible("app.test", true, false, false, true, false, EMPTY, EMPTY));
        assertTrue(SelectionPolicy.eligible("app.test", false, false, true, false, false, EMPTY, EMPTY));
    }
}
