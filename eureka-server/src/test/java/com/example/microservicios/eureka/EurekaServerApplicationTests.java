package com.example.microservicios.eureka;

import com.netflix.discovery.EurekaClientConfig;
import com.netflix.eureka.EurekaServerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.netflix.eureka.EurekaInstanceConfigBean;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the effective registry configuration, not just that the application starts.
 *
 * <p>These values are what make P03 measurable: with the defaults (60 s sweep, 90 s lease,
 * self-preservation on) a stopped instance would keep receiving traffic for minutes and the
 * failure drill would look broken.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EurekaServerApplicationTests {

    @Autowired
    private EurekaServerConfig serverConfig;

    @Autowired
    private EurekaInstanceConfigBean instanceConfig;

    @Autowired
    private EurekaClientConfig clientConfig;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void disablesSelfPreservationAndShortensEvictionWindow() {
        assertThat(serverConfig.shouldEnableSelfPreservation()).isFalse();
        assertThat(serverConfig.getEvictionIntervalTimerInMs()).isEqualTo(3000);
        assertThat(serverConfig.getResponseCacheUpdateIntervalMs()).isEqualTo(1000);
        assertThat(serverConfig.shouldUseReadOnlyResponseCache()).isFalse();
    }

    @Test
    void publishesShortLeaseSoExpiryIsObservable() {
        assertThat(instanceConfig.getLeaseRenewalIntervalInSeconds()).isEqualTo(5);
        assertThat(instanceConfig.getLeaseExpirationDurationInSeconds()).isEqualTo(15);
        assertThat(instanceConfig.isPreferIpAddress()).isTrue();
    }

    @Test
    void doesNotRegisterWithItself() {
        assertThat(clientConfig.shouldRegisterWithEureka()).isFalse();
        assertThat(clientConfig.shouldFetchRegistry()).isFalse();
    }

    @Test
    void servesTheRegistryEndpoint() {
        ResponseEntity<String> response = rest.getForEntity("/eureka/apps", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull().contains("applications");
    }
}
