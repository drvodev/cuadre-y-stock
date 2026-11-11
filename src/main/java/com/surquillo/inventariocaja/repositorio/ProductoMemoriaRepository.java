package com.surquillo.inventariocaja.repositorio;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.Variante;
import java.util.*;

/** Para pruebas y demostración, sin persistencia. */
public class ProductoMemoriaRepository implements ProductoRepository {
    private final NavigableMap<String,Variante> datos = new TreeMap<>();
    public synchronized Variante crear(Variante articulo) {
        if (datos.containsKey(articulo.sku())) throw new ValidacionException("El SKU ya existe");
        datos.put(articulo.sku(),articulo); return articulo;
    }
    public synchronized Variante actualizar(Variante articulo) {
        Variante anterior = datos.get(articulo.sku());
        if (anterior == null || anterior.version() != articulo.version()) throw new ValidacionException("El artículo cambió o no existe; actualice la consulta");
        if (anterior.permiteFraccion() != articulo.permiteFraccion()) throw new ValidacionException("La precisión de la unidad no puede cambiarse automáticamente");
        Variante nuevo = articulo.conVersion(articulo.version()+1); datos.put(nuevo.sku(),nuevo); return nuevo;
    }
    public synchronized Optional<Variante> buscarSku(String sku) { return Optional.ofNullable(datos.get(sku)); }
    public synchronized List<Variante> buscar(String texto, String despues, int limite, boolean incluirInactivos) {
        String consulta = texto.toLowerCase(Locale.ROOT);
        return datos.tailMap(despues,false).values().stream().filter(v -> incluirInactivos || v.activo())
                .filter(v -> String.join(" ",v.sku(),v.descripcion(),v.categoria(),v.marca(),v.presentacion(),v.tipo(),v.unidad()).toLowerCase(Locale.ROOT).contains(consulta))
                .limit(limite).toList();
    }
}
