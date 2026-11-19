package com.example.microservicios.inventario.web;

import com.example.microservicios.persistence.routing.DataSourceKey;
import com.example.microservicios.persistence.routing.RoutingDecision;
import com.example.microservicios.persistence.routing.RoutingDecisionRecorder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InstanceHeaderFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InstanceHeaderFilter.class);

    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    public static final String HEADER_SERVICE_INSTANCE = "X-Service-Instance";
    public static final String HEADER_DATA_SOURCE = "X-Data-Source";

    private final String instanceId;
    private final String serviceName;
    private final RoutingDecisionRecorder decisionRecorder;

    public InstanceHeaderFilter(
            @Value("${service.instance.id:${spring.application.name}:${INSTANCE_ID:${HOSTNAME:local}}}") String instanceId) {
        this(instanceId, null, "servicio-inventario");
    }

    @Autowired
    public InstanceHeaderFilter(
            @Value("${service.instance.id:${spring.application.name}:${INSTANCE_ID:${HOSTNAME:local}}}") String instanceId,
            @Autowired(required = false) RoutingDecisionRecorder decisionRecorder,
            @Value("${spring.application.name:servicio-inventario}") String serviceName) {
        this.instanceId = instanceId;
        this.decisionRecorder = decisionRecorder;
        this.serviceName = serviceName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        request.setAttribute("correlationId", correlationId);
        response.setHeader(HEADER_CORRELATION_ID, correlationId);
        response.setHeader(HEADER_SERVICE_INSTANCE, instanceId);

        MDC.put("correlationId", correlationId);
        MDC.put("serviceInstance", instanceId);

        long start = System.currentTimeMillis();
        String outcome = "SUCCESS";
        try {
            filterChain.doFilter(request, response);
            if (response.getStatus() >= 500) {
                outcome = "ERROR";
            }
        } catch (Exception ex) {
            outcome = "ERROR";
            throw ex;
        } finally {
            long durationMs = System.currentTimeMillis() - start;

            DataSourceKey key = null;
            boolean txReadOnly = false;
            boolean criticalRead = false;

            if (decisionRecorder != null) {
                key = decisionRecorder.lastDecision();
                RoutingDecision ctx = decisionRecorder.lastDecisionContext();
                if (ctx != null) {
                    txReadOnly = ctx.txReadOnly();
                    criticalRead = ctx.criticalRead();
                }
            }

            if (key != null) {
                response.setHeader(HEADER_DATA_SOURCE, key.name());
            }

            String routingKeyStr = key != null ? key.name() : "-";
            log.info("correlationId={} service={} instanceId={} routingKey={} txReadOnly={} criticalRead={} sql=- durationMs={} outcome={}",
                    correlationId, serviceName, instanceId, routingKeyStr, txReadOnly, criticalRead, durationMs, outcome);

            MDC.remove("correlationId");
            MDC.remove("serviceInstance");
            if (decisionRecorder != null) {
                decisionRecorder.clear();
            }
        }
    }
}
