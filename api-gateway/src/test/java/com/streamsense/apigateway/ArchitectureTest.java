package com.streamsense.apigateway;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Structural rules for this service, evaluated over the production classes on every test run. The same file
 * lives in every Java service (only the package differs) so the rules stay identical across the codebase: no
 * field injection, no java.util.logging or System.out, and the layering the packages imply: the web layer
 * ({@code web}, {@code controller}, {@code api}, {@code graphql}) never reaches into persistence, persistence
 * never reaches up into the web, service, or messaging layers, and nothing outside the web layer depends on
 * it. Problem-detail advice lives in {@code web}.
 */
@AnalyzeClasses(packages = "com.streamsense.apigateway", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule noFieldInjection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule noJavaUtilLogging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    @ArchTest
    static final ArchRule noStandardStreams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule webLayerDoesNotTouchPersistence = noClasses()
            .that()
            .resideInAnyPackage("..web..", "..controller..", "..api..", "..graphql..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..persistence..", "..repository..")
            .because("controllers go through a service; repositories and entities are a persistence detail")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule persistenceDoesNotDependUpwards = noClasses()
            .that()
            .resideInAnyPackage("..persistence..", "..repository..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..web..", "..controller..", "..api..", "..graphql..", "..service..", "..kafka..", "..client..")
            .because("entities and repositories must not know about the layers that use them")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule onlyTheWebLayerDependsOnTheWebLayer = classes()
            .that()
            .resideInAnyPackage("..web..", "..controller..")
            .should()
            .onlyHaveDependentClassesThat()
            .resideInAnyPackage("..web..", "..controller..")
            .because("response types and advice belong to the edge; services return their own models")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule problemAdviceLivesInWeb = classes()
            .that()
            .areAnnotatedWith(RestControllerAdvice.class)
            .should()
            .resideInAPackage("..web..")
            .because("every service has one web/GlobalExceptionHandler for RFC 9457 problem details")
            .allowEmptyShould(true);
}
