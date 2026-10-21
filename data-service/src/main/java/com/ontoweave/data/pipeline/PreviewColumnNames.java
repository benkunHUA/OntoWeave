package com.ontoweave.data.pipeline;

/**
 * Reserved names used by preview execution. Hidden columns are compiler-owned
 * and must never be supplied by pipeline users.
 */
public final class PreviewColumnNames {

    public static final String HIDDEN_PREFIX = "__ontoweave_preview_";

    private PreviewColumnNames() {
    }

    public static boolean isReserved(String name) {
        return name != null && name.startsWith(HIDDEN_PREFIX);
    }
}
