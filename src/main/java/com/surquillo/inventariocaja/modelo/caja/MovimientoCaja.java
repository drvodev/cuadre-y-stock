package com.surquillo.inventariocaja.modelo.caja;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MovimientoCaja(UUID id, Instant fecha, Tipo tipo, Fuente fuente,
                             Map<MedioPago,BigDecimal> importes, String referencia) {
    public enum Tipo { COBRO, OTRO_INGRESO, COMPRA, GASTO, DEVOLUCION, RETIRO }
    public enum Fuente { CAJA, EXTERNA }
    public MovimientoCaja { importes=Map.copyOf(importes); }
    public boolean ingreso() { return tipo==Tipo.COBRO || tipo==Tipo.OTRO_INGRESO; }
}
