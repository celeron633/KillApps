package com.android.killapps;

import java.util.Set;

final class SelectionPolicy {
    static boolean eligible(String pkg, boolean system, boolean protectedApp, boolean users,
                            boolean systems, boolean blacklistMode, Set<String> blacklist, Set<String> whitelist) {
        return !protectedApp && (system ? systems : users)
                && (blacklistMode ? blacklist.contains(pkg) : !whitelist.contains(pkg));
    }
}
