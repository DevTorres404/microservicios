package com.example.microservicios.persistence.routing;

import io.micrometer.core.instrument.MeterRegistry;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;

/**
 * AOP Aspect intercepting transactional operations and @CriticalRead methods to decide the target database node.
 * Order is higher than TransactionInterceptor (order = 100) so that it executes *internally* to the transaction:
 * the transaction is already active, readOnly status is visible, and the key is bound to ThreadLocal
 * before the first statement forces connection acquisition through LazyConnectionDataSourceProxy.
 */
@Aspect
@Order(100)
public class ReadWriteRoutingAspect {

    private static final Logger log = LoggerFactory.getLogger(ReadWriteRoutingAspect.class);

    private final ReplicaSelector replicaSelector;
    private final MeterRegistry meterRegistry;
    private final RoutingDecisionRecorder decisionRecorder;

    public ReadWriteRoutingAspect(ReplicaSelector replicaSelector) {
        this(replicaSelector, null, null);
    }

    public ReadWriteRoutingAspect(ReplicaSelector replicaSelector, MeterRegistry meterRegistry) {
        this(replicaSelector, meterRegistry, null);
    }

    public ReadWriteRoutingAspect(ReplicaSelector replicaSelector,
                                  MeterRegistry meterRegistry,
                                  RoutingDecisionRecorder decisionRecorder) {
        this.replicaSelector = replicaSelector;
        this.meterRegistry = meterRegistry;
        this.decisionRecorder = decisionRecorder;
    }

    @Around("@annotation(com.example.microservicios.persistence.routing.CriticalRead) || " +
            "@annotation(org.springframework.transaction.annotation.Transactional) || " +
            "@within(org.springframework.transaction.annotation.Transactional)")
    public Object route(ProceedingJoinPoint joinPoint) throws Throwable {
        RoutingDecision decision = determineDecision(joinPoint);
        DataSourceKey key = decision.key();
        DataSourceContextHolder.push(key);

        if (decisionRecorder != null) {
            decisionRecorder.record(decision);
        }
        if (meterRegistry != null) {
            meterRegistry.counter("db_routing_total", "node", key.name(), "outcome", "success").increment();
        }

        try {
            return joinPoint.proceed();
        } finally {
            DataSourceContextHolder.pop();
        }
    }

    public RoutingDecision determineDecision(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = joinPoint.getTarget() != null ? joinPoint.getTarget().getClass() : signature.getDeclaringType();

        Method specificMethod = ClassUtils.getMostSpecificMethod(method, targetClass);

        // 1. CriticalRead (method level only) always wins: read-your-writes guarantees PRIMARY
        boolean isCritical = AnnotatedElementUtils.hasAnnotation(specificMethod, CriticalRead.class) ||
                             AnnotatedElementUtils.hasAnnotation(method, CriticalRead.class);
        if (isCritical) {
            if (meterRegistry != null) {
                meterRegistry.counter("critical_read_total").increment();
            }
            Transactional tx = findTransactional(specificMethod, method, targetClass);
            boolean isReadOnly = tx != null && tx.readOnly();
            return new RoutingDecision(DataSourceKey.PRIMARY, isReadOnly, true);
        }

        // 2. Transactional annotation on method or class
        Transactional tx = findTransactional(specificMethod, method, targetClass);
        if (tx != null) {
            if (tx.readOnly()) {
                DataSourceKey key = (replicaSelector != null) ? replicaSelector.select() : DataSourceKey.PRIMARY;
                return new RoutingDecision(key, true, false);
            }
            return new RoutingDecision(DataSourceKey.PRIMARY, false, false);
        }

        // 3. Plain execution without transaction or annotation -> authoritative PRIMARY
        return new RoutingDecision(DataSourceKey.PRIMARY, false, false);
    }

    public DataSourceKey determineKey(ProceedingJoinPoint joinPoint) {
        return determineDecision(joinPoint).key();
    }

    private Transactional findTransactional(Method specificMethod, Method method, Class<?> targetClass) {
        Transactional tx = AnnotatedElementUtils.findMergedAnnotation(specificMethod, Transactional.class);
        if (tx == null) {
            tx = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
        }
        if (tx == null) {
            tx = AnnotatedElementUtils.findMergedAnnotation(targetClass, Transactional.class);
        }
        return tx;
    }

    public int getOrderValue() {
        return 100;
    }
}
