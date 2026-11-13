package com.surquillo.inventariocaja.modelo.caja;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
/** Nota histórica vinculada: importe reconocido y dinero real se distinguen del valor de los productos. */
public record CorreccionVenta(UUID id,long secuencia,UUID solicitud,UUID original,UUID turno,Tipo tipo,
    String comprobante,String motivo,Instant fecha,String encargado,BigDecimal valorOriginal,BigDecimal reconocido,
    BigDecimal totalReemplazo,BigDecimal diferencia,UUID reemplazo,List<DevolucionItem> devueltos,Map<MedioPago,BigDecimal> pagos) {
    public enum Tipo { DEVOLUCION, CAMBIO }
    public CorreccionVenta { devueltos=List.copyOf(devueltos);pagos=Map.copyOf(pagos); }
    public BigDecimal noDevuelto(){return valorOriginal.subtract(reconocido);}
}
