package com.happymart;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.happymart.mapper")
public class HappyMartApplication {

    public static void main(String[] args) {
        SpringApplication.run(HappyMartApplication.class, args);
    }
}