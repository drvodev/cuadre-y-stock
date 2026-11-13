package com.surquillo.inventariocaja.servicio;
import com.surquillo.inventariocaja.configuracion.UnidadTrabajoPostgres;
import com.surquillo.inventariocaja.excepcion.*;
import com.surquillo.inventariocaja.modelo.caja.*;
import com.surquillo.inventariocaja.modelo.stock.*;
import com.surquillo.inventariocaja.repositorio.*;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;

/** Documento recibido, pago completo y entradas de stock indivisibles e idempotentes. */
public class CompraService {
    private final UnidadTrabajoPostgres unidad;
    private final CompraRepository repositorio;
    private final CatalogoService catalogo;
    private final InventarioService inventario;
    private final CajaService caja;
    private final Clock reloj;
    public CompraService(DataSource ds,Clock reloj) {
        unidad=new UnidadTrabajoPostgres(ds);this.reloj=Objects.requireNonNull(reloj);
        repositorio=new CompraPostgresRepository(unidad);catalogo=new CatalogoService(new ProductoPostgresRepository(unidad));
        inventario=new InventarioService(new StockPostgresRepository(unidad),reloj);caja=new CajaService(new TurnoPostgresRepository(unidad),reloj);
    }
    public Compra registrar(UUID solicitud,UUID compra,UUID turno,String proveedor,Documento documento,MovimientoCaja.Fuente fuente,
                            BigDecimal total,List<DetalleCompra> items,Map<MedioPago,BigDecimal> pagos) {
        Objects.requireNonNull(solicitud);
        var nueva=new Compra(compra,0,turno,reloj.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS),proveedor,documento,fuente,total,items,pagos);
        String firma=firma(nueva);
        try{return unidad.ejecutar(()->{
            var anterior=repositorio.buscarSolicitud(solicitud);
            if(anterior.isPresent()){
                if(!anterior.get().huella().equals(firma))throw new ValidacionException("La solicitud de compra ya se usó con otros datos");
                return consultar(anterior.get().compra());
            }
            caja.registrar(solicitud,turno,MovimientoCaja.Tipo.COMPRA,fuente,nueva.pagos(),"Compra "+nueva.proveedor()+" · "+documento.tipo()+" "+documento.numero());
            for(int i=0;i<nueva.items().size();i++){
                var item=nueva.items().get(i);if(item.sku().isEmpty())continue;
                var v=catalogo.buscarSku(item.sku()).orElseThrow(()->new ValidacionException("SKU de compra no encontrado: "+item.sku()));
                if(!v.activo())throw new ValidacionException("El artículo de compra está inactivo");
                if(!v.unidad().equals(item.unidad()))throw new ValidacionException("La unidad recibida no coincide con la unidad de stock");
                SaldoStock.validarCantidad(item.cantidad(),v.permiteFraccion());
                var clave=new ClaveStock(v.sku(),item.almacen());
                if(inventario.consultar(clave).isEmpty())inventario.iniciar(derivar(solicitud,"inicial-"+i),clave,v.permiteFraccion(),BigDecimal.ZERO,"Inicialización por compra");
                inventario.entrada(derivar(solicitud,"entrada-"+i),clave,MovimientoStock.Tipo.COMPRA,item.cantidad(),documento.numero());
            }
            return repositorio.guardar(nueva,solicitud,firma);
        });}
        catch(ValidacionException e){throw e;}
        catch(Exception e){throw new PersistenciaException("No se pudo confirmar la compra; consulta el historial antes de reintentar",e);}
    }
    public Compra consultar(UUID id){return repositorio.buscar(Objects.requireNonNull(id)).orElseThrow(()->new ValidacionException("Compra no encontrada"));}
    public List<CompraRepository.Resumen> listar(long despues,int limite) {
        if(despues<0 || limite<1 || limite>100)throw new ValidacionException("Cursor o tamaño de página inválido");
        return repositorio.listar(despues,limite);
    }
    private String firma(Compra c) {
        var b=new StringBuilder();parte(b,c.id(),c.turno(),c.proveedor(),c.documento().tipo(),c.documento().numero(),c.fuente(),numero(c.total()));
        for(var i:c.items())parte(b,i.sku(),i.almacen(),i.descripcion(),i.unidad(),numero(i.cantidad()),i.costoUnitario()==null?"DESCONOCIDO":numero(i.costoUnitario()));
        for(var medio:MedioPago.values())if(c.pagos().containsKey(medio))parte(b,medio,numero(c.pagos().get(medio)));
        return b.toString();
    }
    private static void parte(StringBuilder b,Object... valores){for(Object v:valores){String s=String.valueOf(v);b.append(s.length()).append(':').append(s);}}
    private static String numero(BigDecimal n){return n.stripTrailingZeros().toPlainString();}
    private static UUID derivar(UUID id,String accion){return UUID.nameUUIDFromBytes((id+":"+accion).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
}
