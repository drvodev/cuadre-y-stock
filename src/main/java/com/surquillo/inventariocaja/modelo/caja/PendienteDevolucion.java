package com.surquillo.inventariocaja.modelo.caja;
import java.math.BigDecimal;
public record PendienteDevolucion(int posicion,ItemOperacion item,BigDecimal cantidad) {}
