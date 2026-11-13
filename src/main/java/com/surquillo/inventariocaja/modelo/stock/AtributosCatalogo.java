package com.surquillo.inventariocaja.modelo.stock;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import java.util.*;

/** Características descriptivas, sin conversiones automáticas de existencias. */
public final class AtributosCatalogo {
    private AtributosCatalogo() {}
    public static Map<String,String> normalizar(Map<String,String> datos) {
        if(datos==null || datos.isEmpty()) return Map.of();
        if(datos.size()>50) throw new ValidacionException("Se permiten hasta 50 características por artículo");
        var resultado=new TreeMap<String,String>();
        datos.forEach((nombre,valor)->{
            String clave=nombre==null?"":nombre.strip().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
            String texto=valor==null?"":valor.strip();
            if(clave.isBlank() || clave.length()>60 || texto.isBlank() || texto.length()>500)
                throw new ValidacionException("Cada característica requiere un nombre de hasta 60 caracteres y un valor de hasta 500");
            if(resultado.putIfAbsent(clave,texto)!=null) throw new ValidacionException("Característica repetida: "+clave);
        });
        return Collections.unmodifiableMap(resultado);
    }
}
