package com.marsline.eip;

import com.marsline.eip.common.TaskResult;
import com.marsline.eip.task1.Task1Runner;
import com.marsline.eip.task2.Task2Runner;
import com.marsline.eip.task3.Task3Runner;
import com.marsline.eip.task4.Task4Runner;
import com.marsline.eip.task5.Task5Runner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MARSLINE Enterprise Integration Patterns - single entry point.
 *
 * <pre>
 *   mvn compile exec:java -Dexec.args="task1"   (task1 ... task5)
 *   mvn compile exec:java -Dexec.args="all"     (runs all five, then prints the Integration Hub summary)
 * </pre>
 * Each task starts its own embedded ActiveMQ broker and its own CamelContext, runs its test data,
 * prints a [CHECKPOINT] block computed from what really happened, and shuts everything down.
 */
public final class MarslineEipApplication {

    private static final String LINE = "=".repeat(64);

    private MarslineEipApplication() {
    }

    public static void main(String[] args) throws Exception {
        String selected = args.length == 0 ? "all" : args[0].trim().toLowerCase(Locale.ROOT);

        System.out.println(LINE);
        System.out.println("ITP103 MIDTERM LAB EXAM | GROUP MARSLINE");
        System.out.println("Enterprise Integration Patterns - MARSLINE Busline (Cabuyao, Laguna)");
        System.out.println("Selected task: " + selected);
        System.out.println(LINE);

        List<TaskResult> results = new ArrayList<>();
        switch (selected) {
            case "task1", "1" -> results.add(Task1Runner.run());
            case "task2", "2" -> results.add(Task2Runner.run());
            case "task3", "3" -> results.add(Task3Runner.run());
            case "task4", "4" -> results.add(Task4Runner.run());
            case "task5", "5" -> results.add(Task5Runner.run());
            case "all" -> {
                results.add(Task1Runner.run());
                results.add(Task2Runner.run());
                results.add(Task3Runner.run());
                results.add(Task4Runner.run());
                results.add(Task5Runner.run());
                printHub(results);
            }
            default -> {
                System.out.println("Unknown task '" + selected + "'. Use: task1 | task2 | task3 | task4 | task5 | all");
                return;
            }
        }

        boolean allPassed = results.stream().allMatch(TaskResult::passed);
        if (!allPassed) {
            throw new IllegalStateException("One or more MARSLINE checkpoints FAILED - see the [CHECKPOINT] blocks above.");
        }
    }

    private static void printHub(List<TaskResult> results) {
        System.out.println();
        System.out.println(LINE);
        System.out.println("M A R S L I N E   I N T E G R A T I O N   H U B");
        System.out.println("All Five Patterns, One Pipeline");
        System.out.println(LINE);
        for (TaskResult r : results) {
            System.out.printf("%d  %-24s %-38s %-6s %s%n",
                    r.number(), r.pattern(), r.channels(), r.status(), r.summary());
        }
        System.out.println(LINE);
        long passed = results.stream().filter(TaskResult::passed).count();
        System.out.println("Overall: " + passed + "/" + results.size() + " patterns PASS");
        System.out.println(LINE);
    }
}
