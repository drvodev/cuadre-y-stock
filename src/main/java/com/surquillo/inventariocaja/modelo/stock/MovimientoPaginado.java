package com.surquillo.inventariocaja.modelo.stock;
/** Secuencia estable para consultar movimientos sin cargarlos todos en memoria. */
public record MovimientoPaginado(long secuencia, MovimientoStock movimiento) {}
