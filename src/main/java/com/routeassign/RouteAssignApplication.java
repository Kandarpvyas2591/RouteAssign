package com.routeassign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // activates @Scheduled on AssignmentExpiryServiceImpl
public class RouteAssignApplication {

    public static void main(String[] args) {
        SpringApplication.run(RouteAssignApplication.class, args);
    }
}
