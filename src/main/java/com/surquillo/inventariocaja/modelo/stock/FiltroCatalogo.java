package com.surquillo.inventariocaja.modelo.stock;
import java.util.Map;

/** Filtros combinados con AND; cadenas vacías y origen nulo no restringen resultados. */
public record FiltroCatalogo(String texto, String categoria, String marca, String unidad,
                             String presentacion, String tipo, Variante.Origen origen,
                             Estado estado, Map<String,String> atributos) {
    public enum Estado {
        TODOS("Todos"), ACTIVOS("Activos"), INACTIVOS("Inactivos");
        private final String titulo;
        Estado(String titulo) { this.titulo=titulo; }
        @Override public String toString() { return titulo; }
    }
    public FiltroCatalogo {
        texto=limpiar(texto);categoria=limpiar(categoria);marca=limpiar(marca);
        unidad=limpiar(unidad);presentacion=limpiar(presentacion);tipo=limpiar(tipo);
        estado=estado==null?Estado.TODOS:estado;
        atributos=AtributosCatalogo.normalizar(atributos);
    }
    public static FiltroCatalogo texto(String texto, boolean incluirInactivos) {
        return new FiltroCatalogo(texto,"","","","","",null,incluirInactivos?Estado.TODOS:Estado.ACTIVOS,Map.of());
    }
    private static String limpiar(String texto) { return texto==null?"":texto.strip(); }
}
