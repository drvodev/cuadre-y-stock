package com.surquillo.inventariocaja.modelo.stock;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import java.util.Locale;
import java.util.UUID;

/** Identidad estable y versión para editar listas sin reescribir artículos existentes. */
public record OpcionCatalogo(UUID id, ListaCatalogo lista, String nombre, boolean activo, long version) {
    public OpcionCatalogo {
        if(id==null || lista==null) throw new ValidacionException("Identifica la opción y su lista");
        nombre=nombre==null?"":nombre.strip().replaceAll("\\s+"," ");
        if(nombre.isBlank()) throw new ValidacionException("El nombre de la opción es obligatorio");
        if(version<0) throw new ValidacionException("Versión inválida");
    }
    public String clave() { return nombre.toLowerCase(Locale.ROOT); }
    public OpcionCatalogo conVersion(long nueva) { return new OpcionCatalogo(id,lista,nombre,activo,nueva); }
}
