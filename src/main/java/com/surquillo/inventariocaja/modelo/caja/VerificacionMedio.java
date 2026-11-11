package com.surquillo.inventariocaja.modelo.caja;
import java.math.*;
import com.surquillo.inventariocaja.excepcion.ValidacionException;
public record VerificacionMedio(BigDecimal cobros,BigDecimal devoluciones,BigDecimal egresos,BigDecimal otrosIngresos) {
    public VerificacionMedio { cobros=validar(cobros); devoluciones=validar(devoluciones); egresos=validar(egresos); otrosIngresos=validar(otrosIngresos); }
    private static BigDecimal validar(BigDecimal v) {
        if(v==null||v.signum()<0) throw new ValidacionException("La verificación del medio requiere importes no negativos");
        try { return v.setScale(2,RoundingMode.UNNECESSARY); } catch(ArithmeticException e) { throw new ValidacionException("La verificación admite dos decimales"); }
    }
    public static VerificacionMedio cero() { return new VerificacionMedio(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO); }
    public BigDecimal neto() { return cobros.add(otrosIngresos).subtract(devoluciones).subtract(egresos); }
}
