package com.surquillo.inventariocaja.excepcion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AplicacionExceptionTest {
    @Test
    void conservaMensajeYCausa() {
        var causa = new IllegalStateException("origen");
        var error = new PersistenciaException("No se pudo guardar", causa);
        assertEquals("No se pudo guardar", error.getMessage());
        assertSame(causa, error.getCause());
    }
}
