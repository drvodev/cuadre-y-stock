package com.surquillo.inventariocaja.modelo.stock;

import java.math.BigDecimal;

/** Propuesta previa a la confirmación; no modifica existencias. */
public record Reserva(BigDecimal solicitada, BigDecimal disponible, BigDecimal faltante) {
    public boolean suficiente() { return faltante.signum() == 0; }
}
