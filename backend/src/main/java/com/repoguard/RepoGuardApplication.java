package com.repoguard;

import com.repoguard.config.RepoGuardProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RepoGuardProperties.class)
public class RepoGuardApplication {
    public static void main(String[] args) {
        SpringApplication.run(RepoGuardApplication.class, args);
    }
}
