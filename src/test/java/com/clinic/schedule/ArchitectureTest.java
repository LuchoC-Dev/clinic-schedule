package com.clinic.schedule;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import jakarta.persistence.Entity;

/**
 * What package visibility guarded until the layered modules were split into packages (D-42),
 * checked on the compiled classes instead:
 *
 * <ul>
 *   <li>a module reaches another only through its {@code api/} (D-05);</li>
 *   <li>inside the layered modules — {@code patient}, {@code practitioner}, {@code security}
 *       and {@code user} — each layer is reached only by the one above it, so nothing but the
 *       service reads or writes storage;</li>
 *   <li>only the service changes an entity (D-17): presentation may read one, through its
 *       getters, and nothing more.</li>
 * </ul>
 *
 * <p>Only production classes are checked; tests reach wherever they need to.
 */
class ArchitectureTest {

    private static final String BASE = "com.clinic.schedule.";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.clinic.schedule");

    @ParameterizedTest
    @ValueSource(strings = {"appointment", "clinicalrecord", "patient", "practitioner", "security", "user"})
    void anotherModuleIsReachedOnlyThroughItsApi(String module) {
        noClasses().that().resideOutsideOfPackage(BASE + module + "..")
                .should().dependOnClassesThat(resideInAPackage(BASE + module + "..")
                        .and(not(resideInAPackage(BASE + module + ".api.."))))
                .because("a module talks to another only through that module's api/ (D-05)")
                .check(CLASSES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"patient", "practitioner", "security", "user"})
    void eachLayerIsReachedOnlyByTheOneAboveIt(String module) {
        String root = BASE + module;
        // Optional layers: not every module has every layer — security keeps no entities, user has no screen.
        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .withOptionalLayers(true)
                .layer("Presentation").definedBy(root + ".presentation..")
                .layer("Service").definedBy(root + ".service..")
                .layer("Persistence").definedBy(root + ".persistence..")
                .layer("Entity").definedBy(root + ".entity..")
                .whereLayer("Presentation").mayNotBeAccessedByAnyLayer()
                .whereLayer("Service").mayOnlyBeAccessedByLayers("Presentation")
                .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service")
                .whereLayer("Entity").mayOnlyBeAccessedByLayers("Presentation", "Service", "Persistence")
                .check(CLASSES);
    }

    @org.junit.jupiter.api.Test
    void onlyTheServiceChangesAnEntity() {
        DescribedPredicate<JavaMethodCall> changesAnEntity = new DescribedPredicate<>(
                "a method of an @Entity of a layered module other than a getter") {
            @Override
            public boolean test(JavaMethodCall call) {
                return call.getTargetOwner().isAnnotatedWith(Entity.class)
                        && call.getTargetOwner().getPackageName().endsWith(".entity")
                        && !call.getName().startsWith("get");
            }
        };

        noClasses().that().resideOutsideOfPackages("..service..", "..entity..")
                .should().callMethodWhere(changesAnEntity)
                .because("the service is the only way to change an entity (D-17)")
                .check(CLASSES);
    }
}
