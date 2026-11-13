package com.surquillo.inventariocaja.repositorio;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.*;
import java.time.Instant;
import java.util.*;

/** Repositorio de pruebas: mismas reglas de listas, versiones y filtros; sin persistencia. */
public class ProductoMemoriaRepository implements ProductoRepository {
    private final NavigableMap<String,Variante> datos=new TreeMap<>();
    private final Map<UUID,OpcionCatalogo> opciones=new HashMap<>();
    private final List<CambioCatalogo> cambios=new ArrayList<>();
    private final List<CambioOpcionCatalogo> cambiosOpciones=new ArrayList<>();
    @Override public synchronized OpcionCatalogo crearOpcion(OpcionCatalogo opcion) {
        if(opcion.version()!=0 || opciones.containsKey(opcion.id())) throw new ValidacionException("La opción ya existe o su versión inicial es inválida");
        validarNombre(opcion);opciones.put(opcion.id(),opcion);auditar(opcion);return opcion;
    }
    @Override public synchronized OpcionCatalogo actualizarOpcion(OpcionCatalogo opcion) {
        var anterior=opciones.get(opcion.id());
        if(anterior==null || anterior.version()!=opcion.version() || anterior.lista()!=opcion.lista())
            throw new ValidacionException("La opción cambió, no existe o pertenece a otra lista");
        validarNombre(opcion);var nueva=opcion.conVersion(opcion.version()+1);
        opciones.put(nueva.id(),nueva);auditar(nueva);return nueva;
    }
    private void validarNombre(OpcionCatalogo opcion) {
        if(opciones.values().stream().anyMatch(o->!o.id().equals(opcion.id()) && o.lista()==opcion.lista() && o.clave().equals(opcion.clave())))
            throw new ValidacionException("El nombre ya existe en esta lista, incluso si está inactivo");
    }
    private void auditar(OpcionCatalogo opcion) { cambiosOpciones.add(new CambioOpcionCatalogo(cambiosOpciones.size()+1,Instant.now(),opcion)); }
    @Override public synchronized Optional<OpcionCatalogo> buscarOpcion(UUID id) { return Optional.ofNullable(opciones.get(id)); }
    @Override public synchronized List<OpcionCatalogo> listarOpciones(ListaCatalogo lista,String texto,String despues,int limite,boolean incluirInactivos) {
        String consulta=texto.toLowerCase(Locale.ROOT);
        return opciones.values().stream().filter(o->o.lista()==lista && (incluirInactivos || o.activo()))
                .filter(o->o.clave().compareTo(despues)>0 && o.clave().contains(consulta))
                .sorted(Comparator.comparing(OpcionCatalogo::clave)).limit(limite).toList();
    }
    @Override public synchronized List<String> opciones(ListaCatalogo lista,int limite) {
        return listarOpciones(lista,"","",limite,false).stream().map(OpcionCatalogo::nombre).toList();
    }
    @Override public synchronized List<CambioOpcionCatalogo> historialOpcion(UUID id,long despues,int limite) {
        return cambiosOpciones.stream().filter(c->c.opcion().id().equals(id) && c.secuencia()>despues).limit(limite).toList();
    }
    private String valor(Variante v,ListaCatalogo lista) {
        return switch(lista) { case CATEGORIA->v.categoria();case MARCA->v.marca();case UNIDAD->v.unidad();case PRESENTACION->v.presentacion(); };
    }
    private void registrarOpciones(Variante anterior,Variante nuevo) {
        for(var lista:ListaCatalogo.values()) {
            String nombre=valor(nuevo,lista);
            if(nombre.isBlank() || anterior!=null && valor(anterior,lista).equals(nombre))continue;
            var opcion=new OpcionCatalogo(UUID.randomUUID(),lista,nombre,true,0);
            if(opciones.values().stream().noneMatch(o->o.lista()==lista && o.clave().equals(opcion.clave()))) crearOpcion(opcion);
        }
    }
    @Override public synchronized List<CambioCatalogo> historial(String sku,long despues,int limite) {
        return cambios.stream().filter(c->c.variante().sku().equals(sku) && c.secuencia()>despues).limit(limite).toList();
    }
    private void auditar(Variante v) { cambios.add(new CambioCatalogo(cambios.size()+1,Instant.now(),v)); }
    @Override public synchronized Variante crear(Variante articulo) {
        if(datos.containsKey(articulo.sku())) throw new ValidacionException("El SKU ya existe");
        registrarOpciones(null,articulo);datos.put(articulo.sku(),articulo);auditar(articulo);return articulo;
    }
    @Override public synchronized Variante actualizar(Variante articulo) {
        var anterior=datos.get(articulo.sku());
        if(anterior==null || anterior.version()!=articulo.version()) throw new ValidacionException("El artículo cambió o no existe; actualice la consulta");
        if(anterior.permiteFraccion()!=articulo.permiteFraccion() || !anterior.unidad().equals(articulo.unidad()))
            throw new ValidacionException("La unidad y precisión de stock no pueden cambiarse automáticamente");
        var nuevo=articulo.conVersion(articulo.version()+1);
        registrarOpciones(anterior,nuevo);datos.put(nuevo.sku(),nuevo);auditar(nuevo);return nuevo;
    }
    @Override public synchronized Optional<Variante> buscarSku(String sku) { return Optional.ofNullable(datos.get(sku)); }
    @Override public synchronized List<Variante> buscar(String texto,String despues,int limite,boolean incluirInactivos) {
        return buscar(FiltroCatalogo.texto(texto,incluirInactivos),despues,limite);
    }
    private String normalizar(String texto) { return texto.strip().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT); }
    private boolean coincide(String dato,String filtro) { return filtro.isEmpty() || normalizar(dato).equals(normalizar(filtro)); }
    @Override public synchronized List<Variante> buscar(FiltroCatalogo f,String despues,int limite) {
        String consulta=f.texto().toLowerCase(Locale.ROOT);
        return datos.tailMap(despues,false).values().stream()
            .filter(v->f.estado()==FiltroCatalogo.Estado.TODOS || v.activo()==(f.estado()==FiltroCatalogo.Estado.ACTIVOS))
            .filter(v->f.origen()==null || v.origen()==f.origen())
            .filter(v->coincide(v.categoria(),f.categoria()) && coincide(v.marca(),f.marca()) && coincide(v.unidad(),f.unidad())
                && coincide(v.presentacion(),f.presentacion()) && coincide(v.tipo(),f.tipo()))
            .filter(v->String.join(" ",v.sku(),v.descripcion(),v.categoria(),v.marca(),v.presentacion(),v.tipo(),v.unidad(),
                v.atributos().entrySet().stream().map(e->e.getKey()+" "+e.getValue()).collect(java.util.stream.Collectors.joining(" ")))
                .toLowerCase(Locale.ROOT).contains(consulta))
            .filter(v->f.atributos().entrySet().stream().allMatch(e->v.atributos().containsKey(e.getKey()) && v.atributos().get(e.getKey()).equalsIgnoreCase(e.getValue())))
            .limit(limite).toList();
    }
}
