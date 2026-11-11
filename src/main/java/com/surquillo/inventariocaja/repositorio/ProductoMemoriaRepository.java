package com.surquillo.inventariocaja.repositorio;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.Variante;
import java.util.*;

/** Para pruebas y demostración, sin persistencia. */
public class ProductoMemoriaRepository implements ProductoRepository {
    private final List<com.surquillo.inventariocaja.modelo.stock.CambioCatalogo> cambios=new ArrayList<>();
    @Override public synchronized List<String> opciones(Lista lista,int limite) {
        return datos.values().stream().map(v->switch(lista) { case CATEGORIA->v.categoria(); case MARCA->v.marca(); case UNIDAD->v.unidad(); case PRESENTACION->v.presentacion(); })
                .filter(s->!s.isBlank()).distinct().sorted().limit(limite).toList();
    }
    @Override public synchronized List<com.surquillo.inventariocaja.modelo.stock.CambioCatalogo> historial(String sku,long despues,int limite) {
        return cambios.stream().filter(c->c.variante().sku().equals(sku) && c.secuencia()>despues).limit(limite).toList();
    }
    private void auditar(Variante v) { cambios.add(new com.surquillo.inventariocaja.modelo.stock.CambioCatalogo(cambios.size()+1,java.time.Instant.now(),v)); }
    private final NavigableMap<String,Variante> datos = new TreeMap<>();
    public synchronized Variante crear(Variante articulo) {
        if (datos.containsKey(articulo.sku())) throw new ValidacionException("El SKU ya existe");
        datos.put(articulo.sku(),articulo); auditar(articulo); return articulo;
    }
    public synchronized Variante actualizar(Variante articulo) {
        Variante anterior = datos.get(articulo.sku());
        if (anterior == null || anterior.version() != articulo.version()) throw new ValidacionException("El artículo cambió o no existe; actualice la consulta");
        if (anterior.permiteFraccion() != articulo.permiteFraccion()) throw new ValidacionException("La precisión de la unidad no puede cambiarse automáticamente");
        if (!anterior.unidad().equals(articulo.unidad())) throw new ValidacionException("La unidad de stock no puede cambiarse automáticamente");
        Variante nuevo = articulo.conVersion(articulo.version()+1); datos.put(nuevo.sku(),nuevo); auditar(nuevo); return nuevo;
    }
    public synchronized Optional<Variante> buscarSku(String sku) { return Optional.ofNullable(datos.get(sku)); }
    public synchronized List<Variante> buscar(String texto, String despues, int limite, boolean incluirInactivos) {
        String consulta = texto.toLowerCase(Locale.ROOT);
        return datos.tailMap(despues,false).values().stream().filter(v -> incluirInactivos || v.activo())
                .filter(v -> String.join(" ",v.sku(),v.descripcion(),v.categoria(),v.marca(),v.presentacion(),v.tipo(),v.unidad()).toLowerCase(Locale.ROOT).contains(consulta))
                .limit(limite).toList();
    }
}
