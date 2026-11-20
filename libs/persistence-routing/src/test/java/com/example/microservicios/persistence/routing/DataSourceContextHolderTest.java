package com.example.microservicios.persistence.routing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceContextHolderTest {

    @AfterEach
    void tearDown() {
        DataSourceContextHolder.clear();
    }

    @Test
    @DisplayName("Pushes and peeks current key")
    void shouldPushAndPeekKey() {
        assertThat(DataSourceContextHolder.peek()).isNull();

        DataSourceContextHolder.push(DataSourceKey.PRIMARY);
        assertThat(DataSourceContextHolder.peek()).isEqualTo(DataSourceKey.PRIMARY);
    }

    @Test
    @DisplayName("Nesting pushes and restores previous key on pop (symmetry)")
    void shouldRestorePreviousKeyOnPop() {
        DataSourceContextHolder.push(DataSourceKey.PRIMARY);
        assertThat(DataSourceContextHolder.peek()).isEqualTo(DataSourceKey.PRIMARY);

        // Nested call (e.g. read operation inside write)
        DataSourceContextHolder.push(DataSourceKey.REPLICA_1);
        assertThat(DataSourceContextHolder.peek()).isEqualTo(DataSourceKey.REPLICA_1);

        // Exit inner scope
        DataSourceKey popped = DataSourceContextHolder.pop();
        assertThat(popped).isEqualTo(DataSourceKey.REPLICA_1);
        assertThat(DataSourceContextHolder.peek()).isEqualTo(DataSourceKey.PRIMARY);

        // Exit outer scope
        DataSourceKey outerPopped = DataSourceContextHolder.pop();
        assertThat(outerPopped).isEqualTo(DataSourceKey.PRIMARY);
        assertThat(DataSourceContextHolder.peek()).isNull();
    }

    @Test
    @DisplayName("Clear removes all nested keys and leaves thread clean")
    void shouldClearThreadLocal() {
        DataSourceContextHolder.push(DataSourceKey.PRIMARY);
        DataSourceContextHolder.push(DataSourceKey.REPLICA_2);

        DataSourceContextHolder.clear();

        assertThat(DataSourceContextHolder.peek()).isNull();
    }
}
