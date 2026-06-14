package com.rowingclub.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RowingClubApplication {
    public static void main(String[] args) {
        SpringApplication.run(RowingClubApplication.class, args);
    }
}
