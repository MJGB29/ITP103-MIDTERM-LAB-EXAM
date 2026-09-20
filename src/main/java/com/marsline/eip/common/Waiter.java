package com.marsline.eip.common;

import java.util.function.BooleanSupplier;

/** Polls a condition until it becomes true or the timeout elapses (used by every checkpoint). */
public final class Waiter {

    private Waiter() {
    }

    public static boolean await(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return condition.getAsBoolean();
    }
}
