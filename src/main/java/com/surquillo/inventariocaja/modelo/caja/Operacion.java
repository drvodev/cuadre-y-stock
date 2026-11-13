package com.surquillo.inventariocaja.modelo.caja;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public record Operacion(UUID id,Modalidad modalidad,Documento documento,String cliente,String contacto,
                        BigDecimal total,BigDecimal abonado,Estado estado,Instant fecha,List<ItemOperacion> items) {
    public enum Modalidad { VENTA, SEPARACION }
    public enum Estado { CON_SALDO, PAGADA, ENTREGADA, DEVOLUCION_PARCIAL, CAMBIO_PARCIAL, ANULADA, CAMBIADA }
    public Operacion { items=List.copyOf(items); }
    public BigDecimal saldo() { return estado==Estado.ANULADA||estado==Estado.CAMBIADA?BigDecimal.ZERO:total.subtract(abonado); }
}
