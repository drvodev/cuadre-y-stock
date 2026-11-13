package com.surquillo.inventariocaja.modelo.stock;
import java.time.Instant;
public record CambioOpcionCatalogo(long secuencia, Instant fecha, OpcionCatalogo opcion) {}
