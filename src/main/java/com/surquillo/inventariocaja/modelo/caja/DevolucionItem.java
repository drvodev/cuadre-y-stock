package com.surquillo.inventariocaja.modelo.caja;
import java.math.BigDecimal;
import com.surquillo.inventariocaja.excepcion.ValidacionException;
/** Cantidad efectivamente devuelta; reingreso indica si vuelve a estar disponible para vender. */
public record DevolucionItem(int posicion,BigDecimal cantidad,boolean reingresar) {
    public DevolucionItem {
        if(posicion<0 || cantidad==null || cantidad.signum()<=0 || cantidad.stripTrailingZeros().scale()>3)
            throw new ValidacionException("Revisa la línea y cantidad devuelta");
        cantidad=cantidad.stripTrailingZeros();
    }
}
