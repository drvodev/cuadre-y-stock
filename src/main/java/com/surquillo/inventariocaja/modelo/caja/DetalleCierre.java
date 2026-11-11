package com.surquillo.inventariocaja.modelo.caja;

public record DetalleCierre(VerificacionMedio esperado,VerificacionMedio verificado) {
    public boolean tieneDiferencia() { return !esperado.equals(verificado); }
}
