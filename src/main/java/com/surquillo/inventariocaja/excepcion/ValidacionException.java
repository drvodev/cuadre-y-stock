package com.surquillo.inventariocaja.excepcion;

public class ValidacionException extends AplicacionException {
    public ValidacionException(String mensaje) { super(mensaje); }
    public ValidacionException(String mensaje, Throwable causa) { super(mensaje, causa); }
}
