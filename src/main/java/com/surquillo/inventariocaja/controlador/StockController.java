package com.surquillo.inventariocaja.controlador;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.ClaveStock;
import com.surquillo.inventariocaja.modelo.stock.MovimientoStock;
import com.surquillo.inventariocaja.modelo.stock.SaldoStock;
import com.surquillo.inventariocaja.modelo.stock.Variante;
import com.surquillo.inventariocaja.repositorio.StockRepository;
import com.surquillo.inventariocaja.servicio.CatalogoService;
import com.surquillo.inventariocaja.servicio.InventarioService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Operaciones de Stock para artículos registrados en el catálogo. */
public class StockController {
    private final CatalogoService catalogo;
    private final InventarioService inventario;
    public StockController(CatalogoService catalogo, InventarioService inventario) {
        this.catalogo=Objects.requireNonNull(catalogo); this.inventario=Objects.requireNonNull(inventario);
    }
    public StockRepository.Estado iniciar(UUID solicitud, String sku, String almacen, BigDecimal cantidad) {
        Variante articulo=articulo(sku,true);
        return inventario.iniciar(solicitud,new ClaveStock(articulo.sku(),almacen),articulo.permiteFraccion(),cantidad,"Stock inicial");
    }
    public StockRepository.Estado entrada(UUID solicitud, String sku, String almacen, MovimientoStock.Tipo origen,
                                          BigDecimal cantidad, String referencia) {
        Variante articulo=articulo(sku,true);
        return inventario.entrada(solicitud,new ClaveStock(articulo.sku(),almacen),origen,cantidad,referencia);
    }
    public StockRepository.Estado ajustar(UUID solicitud, String sku, String almacen, BigDecimal cantidad, String motivo) {
        Variante articulo=articulo(sku,false);
        return inventario.ajustar(solicitud,new ClaveStock(articulo.sku(),almacen),cantidad,motivo);
    }
    public Optional<SaldoStock> consultar(String sku,String almacen) {
        Variante articulo=articulo(sku,false);
        return inventario.consultar(new ClaveStock(articulo.sku(),almacen));
    }
    public void configurarMinimo(String sku,String almacen,BigDecimal minimo) { var v=articulo(sku,false);inventario.configurarMinimo(new ClaveStock(v.sku(),almacen),minimo); }
    public boolean stockBajo(String sku,String almacen) { var v=articulo(sku,false);return inventario.stockBajo(new ClaveStock(v.sku(),almacen)); }
    public List<MovimientoStock> historial(String sku,String almacen) {
        Variante articulo=articulo(sku,false);
        return inventario.historial(new ClaveStock(articulo.sku(),almacen));
    }
    public List<com.surquillo.inventariocaja.modelo.stock.MovimientoPaginado> historial(String sku,String almacen,long despues,int limite) {
        var articulo=articulo(sku,false);
        return inventario.historial(new ClaveStock(articulo.sku(),almacen),despues,limite);
    }
    public Optional<SaldoStock> consultarAlCorte(String sku,String almacen,java.time.Instant corteExclusivo) {
        var articulo=articulo(sku,false);
        return inventario.consultarAlCorte(new ClaveStock(articulo.sku(),almacen),corteExclusivo);
    }
    private Variante articulo(String sku, boolean exigirActivo) {
        Variante v=catalogo.buscarSku(sku).orElseThrow(()->new ValidacionException("SKU no encontrado en el catálogo"));
        if (exigirActivo && !v.activo()) throw new ValidacionException("El artículo está inactivo");
        return v;
    }
}
