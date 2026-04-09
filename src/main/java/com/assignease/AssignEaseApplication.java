package com.assignease;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class AssignEaseApplication {
    public static void main(String[] args) {
        SpringApplication.run(AssignEaseApplication.class, args);
    }
}
