package com.campus.identity.infrastructure.bootstrap;

import java.io.Console;
import java.util.Arrays;
import java.util.HashMap;

import com.campus.CampusServiceApplication;
import com.campus.identity.application.FirstAdministratorProvisioningService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

/** Runs through the same migrations, entity validation and Identity transaction as the application. */
public final class AdministratorBootstrapCommand {
    private AdministratorBootstrapCommand() { }

    public static void run(String[] arguments) {
        Console console = System.console();
        String email;
        String displayName;
        char[] password;
        if (console != null) {
            email = console.readLine("First administrator email: ");
            displayName = console.readLine("Display name: ");
            password = console.readPassword("Password (12–64 characters; input hidden): ");
            char[] confirmation = console.readPassword("Confirm password: ");
            try {
                if (password == null || confirmation == null || !Arrays.equals(password, confirmation)) {
                    if (password != null) Arrays.fill(password, '\0');
                    throw new IllegalArgumentException("Password confirmation does not match.");
                }
            } finally { if (confirmation != null) Arrays.fill(confirmation, '\0'); }
        } else {
            // For an automated demo wrapper: secrets are process environment, never command arguments.
            email = requiredEnvironment("CAMPUS_BOOTSTRAP_EMAIL");
            displayName = requiredEnvironment("CAMPUS_BOOTSTRAP_DISPLAY_NAME");
            password = requiredEnvironment("CAMPUS_BOOTSTRAP_PASSWORD").toCharArray();
        }
        try {
            var application = new SpringApplication(CampusServiceApplication.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            var defaults = new HashMap<String, Object>();
            defaults.put("spring.datasource.hikari.connection-init-sql", "SET lock_timeout TO '5s'");
            application.setDefaultProperties(defaults);
            String[] configuration = java.util.stream.Stream.concat(
                    Arrays.stream(arguments).filter(arg -> !arg.equals("--bootstrap-admin")
                            && !arg.startsWith("--spring.main.web-application-type=")),
                    java.util.stream.Stream.of("--spring.main.web-application-type=none"))
                    .toArray(String[]::new);
            try (var context = application.run(configuration)) {
                context.getBean(FirstAdministratorProvisioningService.class)
                        .provision(email, displayName, new String(password));
                System.out.println("First administrator created. Sign in through the normal application.");
            }
        } finally { Arrays.fill(password, '\0'); }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Interactive terminal or configured " + name + " is required.");
        return value;
    }
}
