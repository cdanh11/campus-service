package com.campus;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** A regression guard for explicit Java dependencies; SQL ownership also requires diff review. */
class ModuleBoundaryTest {
    @Test void productionModulesDoNotReferenceAnotherModulesPersistenceInfrastructure() throws Exception {
        var module = Pattern.compile("package com\\.campus\\.(\\w+)(?:\\.|;)");
        var infrastructure = Pattern.compile("\\bcom\\.campus\\.(\\w+)\\.infrastructure(?:\\.\\w+)*");
        var failures = new ArrayList<String>();
        try (var files = Files.walk(Path.of("src/main/java/com/campus"))) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                var owner = module.matcher(source);
                if (!owner.find()) continue;
                var references = infrastructure.matcher(source);
                while (references.find()) {
                    // This SQL-agnostic shared helper carries no domain/table ownership.
                    if (!owner.group(1).equals(references.group(1))
                            && !references.group().equals("com.campus.shared.infrastructure.reporting.ReportJdbc"))
                        failures.add(file + " references " + references.group());
                }
            }
        }
        assertThat(failures).as("Cross-module infrastructure references").isEmpty();
    }
}
