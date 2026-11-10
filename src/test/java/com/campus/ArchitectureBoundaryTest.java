package com.campus;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.campus", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureBoundaryTest {
    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses().that().resideInAPackage("com.campus..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "jakarta.servlet..",
                    "com.campus..api..", "com.campus..infrastructure..");

    @ArchTest
    static final ArchRule apiDoesNotUseJpaPersistence = noClasses().that().resideInAPackage("com.campus..api..")
            .should().dependOnClassesThat().resideInAPackage("com.campus..infrastructure.persistence..");

    @ArchTest
    static final ArchRule persistenceDoesNotDependOnApi = noClasses().that().resideInAPackage("com.campus..infrastructure.persistence..")
            .should().dependOnClassesThat().resideInAPackage("com.campus..api..");
}
