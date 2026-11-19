package com.example.microservicios.persistence.routing;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * ThreadLocal stack for DataSourceKey routing decisions.
 * Stack-based approach ensures symmetry for nested transactions (e.g., @CriticalRead within a read-only method):
 * pop() in a finally block restores the previous key instead of wiping the context.
 * When the stack becomes empty, CONTEXT.remove() is called to prevent ThreadLocal memory leaks in thread pools.
 */
public final class DataSourceContextHolder {

    private static final ThreadLocal<Deque<DataSourceKey>> CONTEXT = new ThreadLocal<>();

    private DataSourceContextHolder() {
    }

    public static void push(DataSourceKey key) {
        Deque<DataSourceKey> stack = CONTEXT.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            CONTEXT.set(stack);
        }
        stack.push(key);
    }

    public static DataSourceKey pop() {
        Deque<DataSourceKey> stack = CONTEXT.get();
        if (stack == null || stack.isEmpty()) {
            CONTEXT.remove();
            return null;
        }
        DataSourceKey key = stack.pop();
        if (stack.isEmpty()) {
            CONTEXT.remove();
        }
        return key;
    }

    public static DataSourceKey peek() {
        Deque<DataSourceKey> stack = CONTEXT.get();
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.peek();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
