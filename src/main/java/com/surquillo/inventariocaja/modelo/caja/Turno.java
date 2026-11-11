package com.surquillo.inventariocaja.modelo.caja;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
public record Turno(UUID id, String caja, LocalDate fechaOperativa, String encargado,
                    BigDecimal fondoInicial, long version, boolean cerrado) {}
