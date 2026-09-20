package com.marsline.eip.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prints the [CHECKPOINT] block of a task. The PASS/FAIL flag is always computed by the caller from
 * what the program actually observed (counts, payload comparisons ...), never hard-coded here.
 */
public final class Checkpoint {

    private static final String LINE = "=".repeat(64);

    private final int taskNo;
    private final String title;
    private String expected = "";
    private String actual = "";
    private final List<String> details = new ArrayList<>();
    private final Map<String, String> stats = new LinkedHashMap<>();

    public Checkpoint(int taskNo, String title) {
        this.taskNo = taskNo;
        this.title = title;
    }

    public Checkpoint expected(String text) {
        this.expected = text;
        return this;
    }

    public Checkpoint actual(String text) {
        this.actual = text;
        return this;
    }

    public Checkpoint detail(String line) {
        details.add(line);
        return this;
    }

    public Checkpoint stat(String label, Object value) {
        stats.put(label, String.valueOf(value));
        return this;
    }

    /** Prints the block and returns "PASS" or "FAIL". */
    public String print(boolean pass) {
        String status = pass ? "PASS" : "FAIL";
        StringBuilder sb = new StringBuilder();
        sb.append('\n').append(LINE).append('\n');
        sb.append("MARSLINE EIP TASK ").append(taskNo).append('\n');
        sb.append(title).append(" CHECKPOINT\n");
        sb.append(LINE).append('\n');
        sb.append("[CHECKPOINT]\n");
        sb.append(" Expected : ").append(expected).append('\n');
        sb.append(" Actual   : ").append(actual).append('\n');
        if (!details.isEmpty()) {
            sb.append("\nPer-item detail:\n");
            for (String d : details) {
                sb.append("   ").append(d).append('\n');
            }
        }
        sb.append('\n');
        for (Map.Entry<String, String> e : stats.entrySet()) {
            sb.append(String.format("%-24s: %s%n", e.getKey(), e.getValue()));
        }
        sb.append(String.format("%-24s: %s%n", "Status", status));
        sb.append(LINE);
        System.out.println(sb);
        return status;
    }
}
