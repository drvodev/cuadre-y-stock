package com.surquillo.inventariocaja.excepcion;

public class PersistenciaException extends AplicacionException {
    public PersistenciaException(String mensaje) { super(mensaje); }
    public PersistenciaException(String mensaje, Throwable causa) { super(mensaje, causa); }
}
