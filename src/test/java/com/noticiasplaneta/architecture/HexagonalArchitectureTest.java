package com.noticiasplaneta.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Fronteras del monolito modular.
 *
 * <p>Si estas reglas se relajan, el código compila igual y el diseño se muere en silencio.
 */
@AnalyzeClasses(packages = "com.noticiasplaneta")
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule el_dominio_no_conoce_el_framework =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "jakarta.validation..",
                            "com.fasterxml.jackson..");

    @ArchTest
    static final ArchRule el_dominio_no_conoce_aplicacion_ni_adaptadores =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..application..",
                            "..adapter..");

    @ArchTest
    static final ArchRule la_aplicacion_no_conoce_los_adaptadores =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAnyPackage("..adapter..");

    @ArchTest
    static final ArchRule los_modulos_solo_hablan_por_puertos =
            classes().that().resideInAPackage("com.noticiasplaneta..")
                    .and().resideOutsideOfPackage("com.noticiasplaneta.shared..")
                    .should(new ArchCondition<>("no depender de internals de otro módulo") {
                        @Override
                        public void check(JavaClass item, ConditionEvents events) {
                            String propio = modulo(item.getPackageName());
                            for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                                String destino = modulo(dependency.getTargetClass().getPackageName());
                                if (destino == null || destino.equals(propio) || destino.equals("shared")) {
                                    continue;
                                }
                                String paqueteDestino = dependency.getTargetClass().getPackageName();
                                boolean permitido = paqueteDestino.contains(".application.port.in");
                                if (!permitido) {
                                    events.add(SimpleConditionEvent.violated(dependency,
                                            "%s depende de %s (módulo '%s'): solo se permite application.port.in"
                                                    .formatted(item.getName(), dependency.getTargetClass().getName(), destino)));
                                }
                            }
                        }
                    });

    private static String modulo(String paquete) {
        if (paquete == null || !paquete.startsWith("com.noticiasplaneta.")) {
            return null;
        }
        String resto = paquete.substring("com.noticiasplaneta.".length());
        int punto = resto.indexOf('.');
        return punto < 0 ? resto : resto.substring(0, punto);
    }
}
