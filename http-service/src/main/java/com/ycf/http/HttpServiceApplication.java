package com.ycf.http;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class HttpServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(HttpServiceApplication.class, args);
    }
}
