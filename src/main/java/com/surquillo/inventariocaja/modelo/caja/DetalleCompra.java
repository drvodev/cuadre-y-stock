package com.surquillo.inventariocaja.modelo.caja;
import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.SaldoStock;
import java.math.*;
import java.util.Locale;

/** Línea recibida; costo desconocido es null y no se transforma en cero. */
public record DetalleCompra(String sku,String almacen,String descripcion,String unidad,BigDecimal cantidad,BigDecimal costoUnitario) {
    public DetalleCompra {
        sku=sku==null?"":sku.strip().toUpperCase(Locale.ROOT);
        almacen=almacen==null?"":almacen.strip();descripcion=descripcion==null?"":descripcion.strip();unidad=unidad==null?"":unidad.strip();
        if(descripcion.isEmpty() || unidad.isEmpty() || !sku.isEmpty() && almacen.isEmpty())
            throw new ValidacionException("Indica descripción, unidad y almacén si el ítem tiene SKU");
        SaldoStock.validarCantidad(cantidad,true);
        if(cantidad.signum()<=0)throw new ValidacionException("La cantidad recibida debe ser positiva");
        cantidad=cantidad.stripTrailingZeros();
        if(costoUnitario!=null) {
            if(costoUnitario.signum()<0 || costoUnitario.stripTrailingZeros().scale()>2 || costoUnitario.compareTo(new BigDecimal("100000000000000"))>=0)
                throw new ValidacionException("El costo debe ser no negativo y tener hasta dos decimales");
            costoUnitario=costoUnitario.setScale(2);
        }
    }
    public BigDecimal totalConocido() { return costoUnitario==null?null:cantidad.multiply(costoUnitario).setScale(2,RoundingMode.HALF_UP); }
}
