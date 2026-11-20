package com.example.microservicios.pedidos;

import com.example.microservicios.persistence.routing.CriticalRead;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

import static org.assertj.core.api.Assertions.assertThat;

class CriticalReadWhitelistTest {

    private static final Set<String> WHITELISTED_METHODS = Set.of(
            "com.example.microservicios.pedidos.web.PedidoController.getOrder"
    );

    @Test
    @DisplayName("Only whitelisted methods in servicio-pedidos are annotated with @CriticalRead")
    void shouldOnlyAllowWhitelistedCriticalReadMethods() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(java.util.regex.Pattern.compile("com\\.example\\.microservicios\\.pedidos\\..*")));

        List<String> annotatedMethods = new ArrayList<>();

        for (BeanDefinition bd : scanner.findCandidateComponents("com.example.microservicios.pedidos")) {
            Class<?> clazz = Class.forName(bd.getBeanClassName());
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(CriticalRead.class)) {
                    annotatedMethods.add(clazz.getName() + "." + method.getName());
                }
            }
        }

        // Verify that getOrder is indeed annotated
        assertThat(annotatedMethods)
                .as("Whitelisted method must be annotated with @CriticalRead")
                .contains("com.example.microservicios.pedidos.web.PedidoController.getOrder");

        // Verify that NO OTHER method is annotated
        for (String methodSignature : annotatedMethods) {
            assertThat(WHITELISTED_METHODS)
                    .as("Method %s must be present in @CriticalRead whitelist", methodSignature)
                    .contains(methodSignature);
        }
    }
}
