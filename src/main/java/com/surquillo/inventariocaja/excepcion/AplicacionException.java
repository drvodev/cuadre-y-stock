package com.surquillo.inventariocaja.excepcion;

public class AplicacionException extends RuntimeException {
    public AplicacionException(String mensaje) { super(mensaje); }
    public AplicacionException(String mensaje, Throwable causa) { super(mensaje, causa); }
}
