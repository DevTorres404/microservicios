package com.example.microservicios.persistence.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thread-safe recorder capturing routing decisions made during the current request.
 * Used by instance filters to populate X-Data-Source header and emit structured logs.
 */
public class RoutingDecisionRecorder {

    private static final ThreadLocal<List<DataSourceKey>> DECISIONS = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<RoutingDecision> LAST_CONTEXT = new ThreadLocal<>();

    public void record(DataSourceKey key) {
        record(new RoutingDecision(key, false, false));
    }

    public void record(RoutingDecision decision) {
        DECISIONS.get().add(decision.key());
        LAST_CONTEXT.set(decision);
    }

    public List<DataSourceKey> decisions() {
        return Collections.unmodifiableList(DECISIONS.get());
    }

    public DataSourceKey lastDecision() {
        List<DataSourceKey> list = DECISIONS.get();
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    public RoutingDecision lastDecisionContext() {
        return LAST_CONTEXT.get();
    }

    public void clear() {
        DECISIONS.remove();
        LAST_CONTEXT.remove();
    }
}
