package com.grupomariposa.orders.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = ArchitectureTest.ROOT,
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String ROOT = "com.grupomariposa.orders";
    private static final String DOMAIN = ROOT + ".domain..";
    private static final String APPLICATION = ROOT + ".application..";
    private static final String INFRASTRUCTURE = ROOT + ".infrastructure..";
    private static final String CONFIG = ROOT + ".infrastructure.config..";
    private static final String JAVA = "java..";

    @ArchTest
    static final ArchRule DOMAIN_IS_PURE_JAVA = classes().that().resideInAPackage(DOMAIN)
            .should().onlyDependOnClassesThat().resideInAnyPackage(DOMAIN, JAVA)
            .because("the domain must stay free of frameworks");

    @ArchTest
    static final ArchRule APPLICATION_DEPENDS_ONLY_ON_DOMAIN = classes().that()
            .resideInAPackage(APPLICATION)
            .should().onlyDependOnClassesThat().resideInAnyPackage(APPLICATION, DOMAIN, JAVA)
            .because("use cases talk to the outside world only through ports");

    @ArchTest
    static final ArchRule CORE_DOES_NOT_KNOW_ADAPTERS = noClasses().that()
            .resideInAnyPackage(DOMAIN, APPLICATION)
            .should().dependOnClassesThat().resideInAPackage(INFRASTRUCTURE);

    @ArchTest
    static final ArchRule ADAPTERS_DO_NOT_DEPEND_ON_WIRING = noClasses().that()
            .resideInAPackage(INFRASTRUCTURE).and().resideOutsideOfPackage(CONFIG)
            .should().dependOnClassesThat().resideInAPackage(CONFIG)
            .because("wiring depends on adapters, never the other way around");

    @ArchTest
    static final ArchRule LAYERS_ARE_FREE_OF_CYCLES = slices().matching(ROOT + ".(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule ADAPTERS_ARE_FREE_OF_CYCLES = slices()
            .matching(ROOT + ".infrastructure.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule INBOUND_PORTS_ARE_INTERFACES = classes().that()
            .resideInAPackage(ROOT + ".application.port.in..")
            .should().beInterfaces();
}
