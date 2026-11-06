package com.tmc;

import com.tmc.shared.config.TmcProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(TmcProperties.class)
public class TmcApplication {

    public static void main(String[] args) {
        SpringApplication.run(TmcApplication.class, args);
    }
}
