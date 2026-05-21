package com.meridian;

import com.meridian.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(JwtProperties.class)
public class MeridianApplication {
    public static void main(String[] args) {
        SpringApplication.run(MeridianApplication.class, args);
    }
}
