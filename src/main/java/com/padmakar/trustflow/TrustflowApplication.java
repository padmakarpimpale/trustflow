package com.padmakar.trustflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TrustflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrustflowApplication.class, args);
    }
}
