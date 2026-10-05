package com.campus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class CampusServiceApplication {

    public static void main(String[] args) {
        if (java.util.Arrays.asList(args).contains("--bootstrap-admin")) {
            com.campus.identity.infrastructure.bootstrap.AdministratorBootstrapCommand.run(args);
            return;
        }
        SpringApplication.run(CampusServiceApplication.class, args);
    }
}
