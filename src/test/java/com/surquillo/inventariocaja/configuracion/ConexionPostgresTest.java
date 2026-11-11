package com.surquillo.inventariocaja.configuracion;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConexionPostgresTest {
    @Test void exigeConfiguracionCompletaSinRevelarSecretos() {
        assertThrows(IllegalArgumentException.class,()->ConexionPostgres.desdeEntorno(Map.of()));
        var ds=ConexionPostgres.desdeEntorno(Map.of("APP_DB_URL","jdbc:postgresql://127.0.0.1/prueba","APP_DB_USER","prueba","APP_DB_PASSWORD","secreto-simulado"));
        assertFalse(ds.toString().contains("secreto-simulado"));
    }
}
