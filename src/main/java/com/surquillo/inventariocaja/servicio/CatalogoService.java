package com.surquillo.inventariocaja.servicio;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.*;
import com.surquillo.inventariocaja.repositorio.ProductoRepository;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class CatalogoService {
    public List<String> opciones(ListaCatalogo lista,int limite) {
        validarLimite(limite); return repositorio.opciones(Objects.requireNonNull(lista),limite);
    }
    public List<com.surquillo.inventariocaja.modelo.stock.CambioCatalogo> historial(String sku,long despues,int limite) {
        validarLimite(limite); if(despues<0) throw new ValidacionException("Cursor inválido");
        return repositorio.historial(normalizar(sku),despues,limite);
    }
    private void validarLimite(int limite) { if(limite<1 || limite>100) throw new ValidacionException("El tamaño de página debe estar entre 1 y 100"); }
    private final ProductoRepository repositorio;
    public CatalogoService(ProductoRepository repositorio) { this.repositorio = Objects.requireNonNull(repositorio); }
    public OpcionCatalogo crearOpcion(ListaCatalogo lista,String nombre) {
        return repositorio.crearOpcion(new OpcionCatalogo(java.util.UUID.randomUUID(),lista,nombre,true,0));
    }
    public OpcionCatalogo actualizarOpcion(OpcionCatalogo opcion) { return repositorio.actualizarOpcion(Objects.requireNonNull(opcion)); }
    public Optional<OpcionCatalogo> buscarOpcion(java.util.UUID id) { return repositorio.buscarOpcion(Objects.requireNonNull(id)); }
    public List<OpcionCatalogo> listarOpciones(ListaCatalogo lista,String texto,String despues,int limite,boolean incluirInactivos) {
        validarLimite(limite);
        return repositorio.listarOpciones(Objects.requireNonNull(lista),texto==null?"":texto.strip(),
                despues==null?"":despues.strip().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT),limite,incluirInactivos);
    }
    public List<CambioOpcionCatalogo> historialOpcion(java.util.UUID id,long despues,int limite) {
        validarLimite(limite);if(despues<0)throw new ValidacionException("Cursor inválido");
        return repositorio.historialOpcion(Objects.requireNonNull(id),despues,limite);
    }
    public List<Variante> buscar(FiltroCatalogo filtro,String despuesDeSku,int limite) {
        validarLimite(limite);
        return repositorio.buscar(Objects.requireNonNull(filtro),
                despuesDeSku==null||despuesDeSku.isBlank()?"":normalizar(despuesDeSku),limite);
    }
    public Variante crear(Variante articulo) {
        Objects.requireNonNull(articulo);
        if (articulo.version() != 0) throw new ValidacionException("La versión inicial debe ser cero");
        return repositorio.crear(articulo);
    }
    public Variante actualizar(Variante articulo) { return repositorio.actualizar(Objects.requireNonNull(articulo)); }
    public Optional<Variante> buscarSku(String sku) { return repositorio.buscarSku(normalizar(sku)); }
    public List<Variante> buscar(String texto, String despuesDeSku, int limite, boolean incluirInactivos) {
        if (limite < 1 || limite > 100) throw new ValidacionException("El tamaño de página debe estar entre 1 y 100");
        return repositorio.buscar(texto == null ? "" : texto.strip(),
                despuesDeSku == null || despuesDeSku.isBlank() ? "" : normalizar(despuesDeSku), limite, incluirInactivos);
    }
    private String normalizar(String sku) {
        if (sku == null || sku.isBlank()) throw new ValidacionException("SKU obligatorio");
        return sku.strip().toUpperCase(Locale.ROOT);
    }
}
