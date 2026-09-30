package com.duka;

import com.duka.config.DukaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(DukaProperties.class)
@EnableScheduling
public class DukaApplication {
    public static void main(String[] args) {
        SpringApplication.run(DukaApplication.class, args);
    }
}
