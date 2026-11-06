package com.tmc.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Executable version of the module-boundary rules from the design document.
 * These fail the build if the architecture drifts.
 */
@AnalyzeClasses(packages = "com.tmc", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllers_never_touch_repositories =
            noClasses().that().areAnnotatedWith(RestController.class)
                    .should().dependOnClassesThat().resideInAPackage("..repo..")
                    .because("controllers delegate to services; persistence stays behind ports");

    @ArchTest
    static final ArchRule domain_modules_never_reach_into_integration =
            noClasses().that().resideInAnyPackage(
                            "com.tmc.attendance..", "com.tmc.reporting..", "com.tmc.identity..",
                            "com.tmc.gate..")
                    .should().dependOnClassesThat().resideInAPackage("com.tmc.integration..")
                    .because("domain modules depend on ports, not adapters (DIP)");

    @ArchTest
    static final ArchRule attendance_never_touches_reporting_or_admin =
            noClasses().that().resideInAPackage("com.tmc.attendance..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.tmc.reporting..", "com.tmc.admin..")
                    .because("the attendance pipeline must not depend on downstream consumers");

    @ArchTest
    static final ArchRule only_integration_calls_http_clients =
            noClasses().that().resideOutsideOfPackage("com.tmc.integration..")
                    .should().dependOnClassesThat().areAssignableTo(RestClient.class)
                    .because("all outbound HTTP lives in the integration layer");

    @ArchTest
    static final ArchRule ports_live_in_api_packages =
            classes().that().haveSimpleNameEndingWith("Port")
                    .should().resideInAPackage("..api")
                    .because("ports are the published contracts of a module");

    @ArchTest
    static final ArchRule services_are_not_in_web_packages =
            noClasses().that().areAnnotatedWith(RestController.class)
                    .should().haveSimpleNameEndingWith("Service")
                    .because("web adapters stay thin");
}
