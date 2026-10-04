package com.android.killapps;

import java.util.Locale;

/** Locates an actionable positive button in both View and Compose accessibility trees. */
final class PositiveButtonFinder<T> {
    interface Tree<T> {
        CharSequence text(T node);
        CharSequence description(T node);
        String id(T node);
        boolean enabled(T node);
        boolean visible(T node);
        boolean clickable(T node);
        int childCount(T node);
        T child(T node, int index);
        T parent(T node);
        T copy(T node);
        void release(T node);
    }
    private final Tree<T> tree;
    PositiveButtonFinder(Tree<T> tree) { this.tree = tree; }

    T find(T root, String[] ids, String[] labels) {
        T button = search(root, ids, true, 0);
        return button != null ? button : search(root, labels, false, 0);
    }
    private T search(T node, String[] values, boolean matchId, int depth) {
        if (depth > 64 || !tree.visible(node)) return null;
        if (matches(matchId ? tree.id(node) : tree.text(node), values)
                || !matchId && matches(tree.description(node), values)) {
            T target = actionable(node);
            if (target != null) return target;
        }
        for (int i = 0; i < tree.childCount(node); i++) {
            T child = tree.child(node, i);
            if (child == null) continue;
            try {
                T result = search(child, values, matchId, depth + 1);
                if (result != null) return result;
            } finally { tree.release(child); }
        }
        return null;
    }
    private T actionable(T node) {
        T cursor = tree.copy(node);
        for (int i = 0; cursor != null && i < 12; i++) {
            if (!tree.visible(cursor) || !tree.enabled(cursor)) { tree.release(cursor); return null; }
            if (tree.clickable(cursor)) return cursor;
            T parent = tree.parent(cursor); tree.release(cursor); cursor = parent;
        }
        if (cursor != null) tree.release(cursor);
        return null;
    }
    static boolean matches(CharSequence text, String[] values) {
        if (text == null) return false;
        String normalized = normalize(text.toString());
        if (normalized.isEmpty()) return false;
        for (String value : values) if (normalized.equals(normalize(value))) return true;
        return false;
    }
    private static String normalize(String text) {
        return text.replaceAll("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]", "")
                .trim().toLowerCase(Locale.ROOT);
    }
}
