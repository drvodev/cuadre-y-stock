package com.surquillo.inventariocaja.modelo.caja;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
public record Cierre(Instant fecha, String firma, BigDecimal efectivoEsperado,
                     BigDecimal efectivoContado, BigDecimal diferencia, String observacion,
                     Map<MedioPago,BigDecimal> flujoPorMedio,Map<MedioPago,DetalleCierre> digitales) {
    public Cierre { flujoPorMedio=Map.copyOf(flujoPorMedio); digitales=Map.copyOf(digitales); }
}
