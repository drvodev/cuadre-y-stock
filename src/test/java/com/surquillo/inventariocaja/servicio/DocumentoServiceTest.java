package com.surquillo.inventariocaja.servicio;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DocumentoServiceTest {
    @Test void conservaPrefijoYCerosYPermiteNumeroManualSinCorrelativo() {
        var servicio=new DocumentoService(); assertEquals("B3848",servicio.siguiente("B3847").orElseThrow());
        assertEquals("N0100",servicio.siguiente("N0099").orElseThrow()); assertTrue(servicio.siguiente("MANUAL").isEmpty());
    }
}
