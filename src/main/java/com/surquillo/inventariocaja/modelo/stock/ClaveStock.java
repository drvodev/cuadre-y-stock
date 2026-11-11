package com.surquillo.inventariocaja.modelo.stock;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import java.util.Locale;

public record ClaveStock(String sku, String almacen) {
    public ClaveStock {
        if (sku == null || sku.isBlank() || almacen == null || almacen.isBlank())
            throw new ValidacionException("SKU y almacén son obligatorios");
        sku = sku.strip().toUpperCase(Locale.ROOT);
        almacen = almacen.strip();
    }
}
