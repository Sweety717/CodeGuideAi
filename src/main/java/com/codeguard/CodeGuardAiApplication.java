package com.codeguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CodeGuardAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeGuardAiApplication.class, args);
    }
}
