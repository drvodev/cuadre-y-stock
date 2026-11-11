package com.surquillo.inventariocaja.servicio;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.Variante;
import com.surquillo.inventariocaja.repositorio.ProductoRepository;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class CatalogoService {
    private final ProductoRepository repositorio;
    public CatalogoService(ProductoRepository repositorio) { this.repositorio = Objects.requireNonNull(repositorio); }
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
