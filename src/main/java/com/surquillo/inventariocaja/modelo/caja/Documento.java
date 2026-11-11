package com.surquillo.inventariocaja.modelo.caja;

public record Documento(Tipo tipo,String numero,boolean manual) {
    public enum Tipo { BOLETA, FACTURA, NOTA_VENTA }
    public Documento {
        java.util.Objects.requireNonNull(tipo);
        if(numero==null||numero.isBlank()) throw new com.surquillo.inventariocaja.excepcion.ValidacionException("Indica el número del documento");
        numero=numero.strip().toUpperCase(java.util.Locale.ROOT);
    }
}
