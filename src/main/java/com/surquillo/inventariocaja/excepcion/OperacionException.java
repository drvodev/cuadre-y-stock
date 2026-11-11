package com.surquillo.inventariocaja.excepcion;

public class OperacionException extends AplicacionException {
    public OperacionException(String mensaje) { super(mensaje); }
    public OperacionException(String mensaje, Throwable causa) { super(mensaje, causa); }
}
