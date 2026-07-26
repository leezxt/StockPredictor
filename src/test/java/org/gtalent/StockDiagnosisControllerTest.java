package org.gtalent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockDiagnosisControllerTest {
    @Test
    void shouldDelegateDiagnosisWithoutChangingSymbolContract() {
        StockDiagnosisService service = mock(StockDiagnosisService.class);
        Map<String, Object> expected = Map.of("symbol", "2330", "status", "多頭穩健");
        when(service.diagnose(" 2330 ")).thenReturn(expected);
        StockDiagnosisController controller = new StockDiagnosisController(service);

        assertSame(expected, controller.getIntegratedDiagnosis(" 2330 "));
        verify(service).diagnose(" 2330 ");
    }
}
