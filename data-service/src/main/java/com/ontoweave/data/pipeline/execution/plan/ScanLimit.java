package com.ontoweave.data.pipeline.execution.plan;

public record ScanLimit(boolean limited, int maxRows) {

    public ScanLimit {
        if (limited && maxRows <= 0) {
            throw new IllegalArgumentException("limited scans require a positive maxRows");
        }
        if (!limited && maxRows != 0) {
            throw new IllegalArgumentException("unlimited scans must not carry maxRows");
        }
    }

    public static ScanLimit preview(int maxRows) {
        return new ScanLimit(true, maxRows);
    }

    public static ScanLimit unlimited() {
        return new ScanLimit(false, 0);
    }
}
