package com.surquillo.inventariocaja.modelo.stock;

import java.math.BigDecimal;
import java.util.UUID;

/** Reserva de una línea de separación; sus cambios se guardan junto al saldo. */
public record ReservaOperacion(UUID id, UUID separacionId, ClaveStock clave, BigDecimal solicitada,
                               BigDecimal reservada, Estado estado) {
    public enum Estado { ACTIVA, CANCELADA, DESPACHADA }
    public BigDecimal faltante() { return solicitada.subtract(reservada); }
}
