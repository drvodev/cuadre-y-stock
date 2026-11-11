package com.surquillo.inventariocaja.modelo.stock;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import java.math.BigDecimal;
import java.util.Locale;

/** Saldo inmutable de una variante en un almacén. */
public record SaldoStock(String sku, String almacen, boolean permiteFraccion,
                         BigDecimal fisico, BigDecimal reservado) {
    public SaldoStock {
        if (sku == null || sku.isBlank() || almacen == null || almacen.isBlank()) {
            throw new ValidacionException("SKU y almacén son obligatorios");
        }
        sku = sku.strip().toUpperCase(Locale.ROOT);
        almacen = almacen.strip();
        validarCantidad(fisico, permiteFraccion);
        validarCantidad(reservado, permiteFraccion);
        if (reservado.compareTo(fisico) > 0) {
            throw new ValidacionException("La reserva no puede superar el stock físico");
        }
    }

    public BigDecimal disponible() { return fisico.subtract(reservado); }

    public static void validarCantidad(BigDecimal cantidad, boolean permiteFraccion) {
        if (cantidad == null || cantidad.signum() < 0) {
            throw new ValidacionException("La cantidad debe ser igual o mayor a cero");
        }
        BigDecimal normalizada = cantidad.stripTrailingZeros();
        if (normalizada.scale() > (permiteFraccion ? 3 : 0)) {
            throw new ValidacionException("La cantidad no respeta la precisión del producto");
        }
    }
}
