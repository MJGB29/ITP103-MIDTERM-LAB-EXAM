package com.marsline.eip.common;

/** Outcome of one task, used for the final "Integration Hub" summary. */
public record TaskResult(int number, String pattern, String channels, String status, String summary) {

    public boolean passed() {
        return "PASS".equals(status);
    }
}
