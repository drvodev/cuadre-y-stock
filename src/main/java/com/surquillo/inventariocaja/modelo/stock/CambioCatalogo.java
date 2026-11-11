package com.surquillo.inventariocaja.modelo.stock;

import java.time.Instant;
public record CambioCatalogo(long secuencia, Instant fecha, Variante variante) {}
