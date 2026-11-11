package com.surquillo.inventariocaja.servicio;

public class DocumentoService {
    public java.util.Optional<String> siguiente(String ultimo) {
        if(ultimo==null) return java.util.Optional.empty();
        var m=java.util.regex.Pattern.compile("^(.*?)([0-9]+)$").matcher(ultimo.strip());
        if(!m.matches()) return java.util.Optional.empty();
        String numero=new java.math.BigInteger(m.group(2)).add(java.math.BigInteger.ONE).toString();
        return java.util.Optional.of(m.group(1)+"0".repeat(Math.max(0,m.group(2).length()-numero.length()))+numero);
    }
}
