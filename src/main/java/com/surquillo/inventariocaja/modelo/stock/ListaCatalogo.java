package com.surquillo.inventariocaja.modelo.stock;

/** Listas de sugerencias; la unidad de stock de un artículo conserva su propia precisión. */
public enum ListaCatalogo {
    CATEGORIA("Categorías"), MARCA("Marcas"), UNIDAD("Unidades"), PRESENTACION("Presentaciones");
    private final String titulo;
    ListaCatalogo(String titulo) { this.titulo=titulo; }
    @Override public String toString() { return titulo; }
}
