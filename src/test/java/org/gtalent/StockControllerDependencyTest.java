package org.gtalent;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StockControllerDependencyTest {

    @Test
    void shouldReceiveAdviceServicesThroughConstructor() {
        Constructor<?> constructor = StockAdviceController.class.getDeclaredConstructors()[0];
        Set<Class<?>> parameterTypes = Arrays.stream(constructor.getParameterTypes())
                .collect(Collectors.toSet());

        assertTrue(parameterTypes.contains(InstitutionalService.class));
        assertTrue(parameterTypes.contains(StockDiagnosisService.class));
        assertTrue(parameterTypes.contains(StockKdAnalysisService.class));
        assertTrue(parameterTypes.contains(StockHistoryService.class));
    }
}
