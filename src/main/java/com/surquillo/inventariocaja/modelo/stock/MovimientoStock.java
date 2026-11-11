package com.surquillo.inventariocaja.modelo.stock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Registro inmutable: las cantidades son diferencias físicas, no solicitudes. */
public record MovimientoStock(UUID id, Instant fecha, Tipo tipo, String sku, String almacen,
                              BigDecimal cantidadSolicitada, BigDecimal cambioFisico,
                              BigDecimal fisicoAnterior, BigDecimal fisicoPosterior,
                              String referencia, String motivo) {
    public enum Tipo { INICIAL, COMPRA, FABRICACION, AJUSTE, VENTA, DEVOLUCION, RESERVA, AMPLIACION_RESERVA, CANCELACION_RESERVA, DESPACHO_RESERVA }
}
