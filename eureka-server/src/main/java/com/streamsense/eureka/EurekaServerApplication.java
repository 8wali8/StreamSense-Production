package com.streamsense.eureka; // FIX THIS LATER shouldnt need main.java.

import org.springframework.boot.SpringApplication; //spring
import org.springframework.boot.autoconfigure.SpringBootApplication; //config, autoconfig, componentscan
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer; //turns into registry server

@SpringBootApplication // main app
@EnableEurekaServer // spin up eureka server

public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}