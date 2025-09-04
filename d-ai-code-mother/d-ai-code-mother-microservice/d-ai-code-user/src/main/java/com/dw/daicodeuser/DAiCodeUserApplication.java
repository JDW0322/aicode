package com.dw.daicodeuser;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan("com.dw.daicodeuser.mapper")
@ComponentScan("com.dw")
@EnableDubbo
public class DAiCodeUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(DAiCodeUserApplication.class, args);
    }
}
