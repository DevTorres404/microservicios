package com.example.microservicios.pedidos.client;

import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignClientConfiguration {

    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
                500, TimeUnit.MILLISECONDS,
                1500, TimeUnit.MILLISECONDS,
                true
        );
    }

    @Bean
    public Retryer retryer() {
        return new Retryer.Default(100, 500, 2);
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return new InventarioErrorDecoder();
    }
}
