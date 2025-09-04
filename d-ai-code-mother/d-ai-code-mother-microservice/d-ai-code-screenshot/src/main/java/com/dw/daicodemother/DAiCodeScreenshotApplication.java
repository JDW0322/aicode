package com.dw.daicodemother;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDubbo
public class DAiCodeScreenshotApplication {
    public static void main(String[] args) {
        SpringApplication.run(DAiCodeScreenshotApplication.class, args);
    }
}

