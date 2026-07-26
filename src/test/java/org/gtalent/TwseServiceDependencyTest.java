package org.gtalent;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TwseServiceDependencyTest {

    @Test
    void servicesReceiveTwseServiceThroughConstructors() {
        List<Class<?>> serviceTypes = List.of(
                BulkFetchService.class,
                EnhancedInstitutionalService.class,
                InstitutionalService.class,
                ScannerService.class,
                ScheduledService.class
        );

        for (Class<?> serviceType : serviceTypes) {
            assertTrue(hasConstructorParameter(serviceType, TwseService.class),
                    () -> serviceType.getSimpleName() + " 應透過 constructor injection 接收 TwseService");
        }
    }

    private boolean hasConstructorParameter(Class<?> owner, Class<?> dependency) {
        for (Constructor<?> constructor : owner.getDeclaredConstructors()) {
            for (Class<?> parameterType : constructor.getParameterTypes()) {
                if (parameterType == dependency) {
                    return true;
                }
            }
        }
        return false;
    }
}
