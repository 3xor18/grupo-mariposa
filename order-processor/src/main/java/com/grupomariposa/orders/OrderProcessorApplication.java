package com.grupomariposa.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class OrderProcessorApplication {

    public static void main(final String[] args) {
        SpringApplication.run(OrderProcessorApplication.class, args);
    }
}
