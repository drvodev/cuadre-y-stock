package com.surquillo.inventariocaja.servicio;

import com.surquillo.inventariocaja.configuracion.UnidadTrabajoPostgres;
import com.surquillo.inventariocaja.excepcion.*;
import com.surquillo.inventariocaja.modelo.caja.*;
import com.surquillo.inventariocaja.modelo.stock.*;
import com.surquillo.inventariocaja.repositorio.*;
import javax.sql.DataSource;
import java.sql.*;
import java.math.*;
import java.time.*;
import java.util.*;

/** Notas de devolución y cambio con importe reconocido manual, cantidades protegidas y diferencia real. */
public final class CorreccionVentaService {
    private final UnidadTrabajoPostgres unidad;
    private final VentaService ventas;
    private final CajaService caja;
    private final InventarioService inventario;
    private final CatalogoService catalogo;
    private final Clock reloj;
    private record Valuada(DevolucionItem item,BigDecimal valor) {}
    public CorreccionVentaService(DataSource ds,Clock reloj) {
        unidad=new UnidadTrabajoPostgres(ds);this.reloj=Objects.requireNonNull(reloj);
        ventas=new VentaService(unidad,reloj);caja=new CajaService(new TurnoPostgresRepository(unidad),reloj);
        inventario=new InventarioService(new StockPostgresRepository(unidad),reloj);catalogo=new CatalogoService(new ProductoPostgresRepository(unidad));
    }
    public CorreccionVenta registrar(UUID solicitud,UUID correccion,UUID original,UUID turno,CorreccionVenta.Tipo tipo,
        String comprobante,String motivo,List<DevolucionItem> devueltos,BigDecimal reconocido,List<ItemOperacion> reemplazos,
        Documento nuevoDocumento,Map<MedioPago,BigDecimal> pagos,BigDecimal recibido) {
        Objects.requireNonNull(solicitud);Objects.requireNonNull(correccion);Objects.requireNonNull(original);Objects.requireNonNull(turno);Objects.requireNonNull(tipo);
        String nota=obligatorio(comprobante,"Número de nota").toUpperCase(Locale.ROOT),razon=obligatorio(motivo,"Motivo");
        var seleccion=new TreeMap<Integer,DevolucionItem>();
        for(var item:devueltos)if(seleccion.putIfAbsent(item.posicion(),item)!=null)throw new ValidacionException("No repitas una línea devuelta");
        if(seleccion.isEmpty())throw new ValidacionException("Indica qué cantidad se devuelve");
        var retorno=List.copyOf(seleccion.values());var nuevos=List.copyOf(reemplazos);var medios=medios(pagos);
        var importe=moneda(reconocido,true);var entregado=moneda(recibido==null?BigDecimal.ZERO:recibido,true);
        if(tipo==CorreccionVenta.Tipo.DEVOLUCION && (!nuevos.isEmpty()||nuevoDocumento!=null))
            throw new ValidacionException("Una devolución no lleva productos de reemplazo");
        if(tipo==CorreccionVenta.Tipo.CAMBIO && (nuevos.isEmpty()||nuevoDocumento==null))
            throw new ValidacionException("El cambio requiere productos y documento de reemplazo");
        BigDecimal totalNuevo=tipo==CorreccionVenta.Tipo.CAMBIO?moneda(nuevos.stream().map(ItemOperacion::total).reduce(BigDecimal.ZERO,BigDecimal::add),false):new BigDecimal("0.00");
        BigDecimal diferencia=totalNuevo.subtract(importe);
        if(medios.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(diferencia.abs())!=0
            || diferencia.signum()==0&&!medios.isEmpty() || diferencia.signum()<=0&&entregado.signum()!=0)
            throw new ValidacionException("Registra únicamente el cobro o reintegro de la diferencia; si es cero, deja pagos vacíos");
        var firma=new StringBuilder();parte(firma,"CORREGIR",correccion,original,turno,tipo,nota,razon,numero(importe),numero(entregado));
        retorno.forEach(i->parte(firma,i.posicion(),numero(i.cantidad()),i.reingresar()));
        nuevos.forEach(i->parte(firma,i.sku(),i.almacen(),i.descripcion(),i.unidad(),numero(i.cantidad()),numero(i.precio()),i.indicaciones()));
        if(nuevoDocumento!=null)parte(firma,nuevoDocumento.tipo(),nuevoDocumento.numero(),nuevoDocumento.manual());
        for(var medio:MedioPago.values())if(medios.containsKey(medio))parte(firma,medio,numero(medios.get(medio)));
        try{return unidad.ejecutar(()->{
            try(var c=unidad.getConnection();var p=c.prepareStatement("SELECT c.id,s.huella FROM venta_correccion c JOIN venta_solicitud s ON s.id=c.solicitud WHERE c.solicitud=?")){
                p.setObject(1,solicitud);try(var r=p.executeQuery()){if(r.next()){if(!firma.toString().equals(r.getString(2)))throw new ValidacionException("La solicitud ya se usó con otros datos");return consultar(r.getObject(1,UUID.class));}}
            }
            try(var c=unidad.getConnection();var p=c.prepareStatement("SELECT pg_advisory_xact_lock(739102815)")){p.execute();}
            var actual=caja.consultar(turno);if(actual.turno().cerrado())throw new ValidacionException("Abre un turno para registrar la nota");
            var op=ventas.consultar(original);
            if(!admiteCorrecciones(op))throw new ValidacionException("Solo se corrigen productos entregados; revisa el estado de la operación");
            var anteriores=devueltas(original);var valoradas=valorar(op,retorno,anteriores);
            BigDecimal valorOriginal=moneda(valoradas.stream().map(Valuada::valor).reduce(BigDecimal.ZERO,BigDecimal::add),true);
            if(importe.compareTo(valorOriginal)>0)throw new ValidacionException("El importe reconocido supera el valor original de lo devuelto");
            UUID reemplazo=tipo==CorreccionVenta.Tipo.CAMBIO?derivar(correccion,"reemplazo"):null;
            Instant fecha=reloj.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            if(reemplazo!=null)guardarReemplazo(reemplazo,op,nuevoDocumento,nuevos,totalNuevo,fecha);
            String referencia=(tipo==CorreccionVenta.Tipo.CAMBIO?"Cambio ":"Devolución ")+nota+" · "+op.documento().numero()+": "+razon;
            if(diferencia.signum()>0)caja.cobrar(solicitud,turno,diferencia,medios,entregado,referencia);
            else if(diferencia.signum()<0)caja.registrar(solicitud,turno,MovimientoCaja.Tipo.DEVOLUCION,MovimientoCaja.Fuente.CAJA,medios,referencia);
            for(var fila:retorno){
                var item=op.items().get(fila.posicion());if(!fila.reingresar())continue;
                if(item.sku().isEmpty())throw new ValidacionException("Un ítem libre no puede reingresarse automáticamente al inventario");
                var clave=new ClaveStock(item.sku(),item.almacen());
                if(inventario.consultar(clave).isEmpty()){
                    var articulo=catalogo.buscarSku(item.sku()).orElseThrow(()->new ValidacionException("Registra el SKU antes de reingresar este producto"));
                    inventario.iniciar(derivar(solicitud,"inicial-"+fila.posicion()),clave,articulo.permiteFraccion(),BigDecimal.ZERO,"Inicialización por devolución");
                }
                inventario.entrada(derivar(solicitud,"retorno-"+fila.posicion()),clave,MovimientoStock.Tipo.DEVOLUCION,fila.cantidad(),nota);
            }
            for(int i=0;i<nuevos.size();i++){
                var item=nuevos.get(i);if(item.sku().isEmpty())continue;
                var v=catalogo.buscarSku(item.sku());
                if(v.isPresent()){if(!v.get().activo())throw new ValidacionException("El reemplazo está inactivo");SaldoStock.validarCantidad(item.cantidad(),v.get().permiteFraccion());}
                var clave=new ClaveStock(item.sku(),item.almacen());
                if(inventario.consultar(clave).isPresent())inventario.venta(derivar(solicitud,"salida-"+i),clave,item.cantidad(),nota);
            }
            guardarNota(correccion,solicitud,original,turno,tipo,nota,razon,fecha,actual.turno().encargado(),valorOriginal,importe,totalNuevo,reemplazo,valoradas,medios);
            boolean completa=true;
            for(int i=0;i<op.items().size();i++){
                var elegido=seleccion.get(i);
                var cantidad=anteriores.getOrDefault(i,BigDecimal.ZERO).add(elegido==null?BigDecimal.ZERO:elegido.cantidad());
                if(cantidad.compareTo(op.items().get(i).cantidad())<0){completa=false;break;}
            }
            try(var c=unidad.getConnection();var p=c.prepareStatement("UPDATE venta_operacion SET estado=? WHERE id=?")){
                String estado=completa?(tipo==CorreccionVenta.Tipo.CAMBIO?"CAMBIADA":"ANULADA"):
                    (tipo==CorreccionVenta.Tipo.CAMBIO?"CAMBIO_PARCIAL":"DEVOLUCION_PARCIAL");
                p.setString(1,estado);p.setObject(2,original);p.executeUpdate();
            }
            try(var c=unidad.getConnection();var p=c.prepareStatement("INSERT INTO venta_solicitud(id,huella,operacion) VALUES(?,?,?)")){
                p.setObject(1,solicitud);p.setString(2,firma.toString());p.setObject(3,reemplazo==null?original:reemplazo);p.executeUpdate();
            }
            return consultar(correccion);
        });}
        catch(ValidacionException e){throw e;}
        catch(SQLException e){if("23505".equals(e.getSQLState()))throw new ValidacionException("La nota o el documento de reemplazo ya existen");throw new PersistenciaException("No se pudo confirmar la nota",e);}
        catch(Exception e){throw new PersistenciaException("No se pudo confirmar la nota; consulta su estado antes de reintentar",e);}
    }
    private List<Valuada> valorar(Operacion op,List<DevolucionItem> seleccion,Map<Integer,BigDecimal> anteriores) {
        var filas=new ArrayList<Valuada>();
        for(var devuelta:seleccion){
            if(devuelta.posicion()>=op.items().size())throw new ValidacionException("Línea devuelta inexistente");
            var item=op.items().get(devuelta.posicion());var antes=anteriores.getOrDefault(devuelta.posicion(),BigDecimal.ZERO);
            var acumulado=antes.add(devuelta.cantidad());
            if(acumulado.compareTo(item.cantidad())>0)throw new ValidacionException("La cantidad ya fue devuelta o supera lo entregado");
            if(!item.sku().isEmpty())catalogo.buscarSku(item.sku()).ifPresent(v->SaldoStock.validarCantidad(devuelta.cantidad(),v.permiteFraccion()));
            BigDecimal valor=item.precio().multiply(acumulado).setScale(2,RoundingMode.HALF_UP)
                .subtract(item.precio().multiply(antes).setScale(2,RoundingMode.HALF_UP));
            filas.add(new Valuada(devuelta,valor));
        }
        return filas;
    }
    public List<PendienteDevolucion> pendientes(UUID original) {
        var op=ventas.consultar(original);if(!admiteCorrecciones(op))return List.of();
        var anteriores=devueltas(original);var resultado=new ArrayList<PendienteDevolucion>();
        for(int i=0;i<op.items().size();i++){var item=op.items().get(i);var cantidad=item.cantidad().subtract(anteriores.getOrDefault(i,BigDecimal.ZERO));if(cantidad.signum()>0)resultado.add(new PendienteDevolucion(i,item,cantidad));}
        return List.copyOf(resultado);
    }
    public BigDecimal valorarDevolucion(UUID original,List<DevolucionItem> devueltos) {
        var op=ventas.consultar(original);
        if(!admiteCorrecciones(op))throw new ValidacionException("La operación no tiene productos entregados disponibles");
        var indices=new HashSet<Integer>();
        if(devueltos.isEmpty())throw new ValidacionException("Selecciona una cantidad devuelta");
        for(var item:devueltos)if(!indices.add(item.posicion()))throw new ValidacionException("Línea repetida");
        return valorar(op,List.copyOf(devueltos),devueltas(original)).stream().map(Valuada::valor).reduce(new BigDecimal("0.00"),BigDecimal::add);
    }
    private Map<Integer,BigDecimal> devueltas(UUID original){
        String sql="SELECT i.posicion,sum(i.cantidad) FROM venta_correccion_item i JOIN venta_correccion c ON c.id=i.correccion WHERE c.original=? GROUP BY i.posicion";
        try(var c=unidad.getConnection();var p=c.prepareStatement(sql)){p.setObject(1,original);try(var r=p.executeQuery()){var resultado=new HashMap<Integer,BigDecimal>();while(r.next())resultado.put(r.getInt(1),r.getBigDecimal(2));return resultado;}}
        catch(SQLException e){throw new PersistenciaException("No se pudieron consultar las cantidades devueltas",e);}
    }
    private void guardarReemplazo(UUID id,Operacion original,Documento doc,List<ItemOperacion> items,BigDecimal total,Instant fecha)throws SQLException {
        try(var c=unidad.getConnection();var p=c.prepareStatement("INSERT INTO venta_operacion(id,modalidad,tipo,numero,manual,cliente,contacto,total,abonado,estado,fecha) VALUES(?,'VENTA',?,?,?,?,?,?,?,'ENTREGADA',?)")){
            p.setObject(1,id);p.setString(2,doc.tipo().name());p.setString(3,doc.numero());p.setBoolean(4,doc.manual());p.setString(5,original.cliente());p.setString(6,original.contacto());p.setBigDecimal(7,total);p.setBigDecimal(8,total);p.setTimestamp(9,Timestamp.from(fecha));p.executeUpdate();
            try(var q=c.prepareStatement("INSERT INTO venta_documento(operacion,tipo,numero,manual) VALUES(?,?,?,?)")){
                q.setObject(1,id);q.setString(2,doc.tipo().name());q.setString(3,doc.numero());q.setBoolean(4,doc.manual());q.executeUpdate();
            }
            try(var q=c.prepareStatement("INSERT INTO venta_item(operacion,posicion,sku,almacen,descripcion,unidad,cantidad,precio,indicaciones) VALUES(?,?,?,?,?,?,?,?,?)")){
                for(int i=0;i<items.size();i++){var item=items.get(i);q.setObject(1,id);q.setInt(2,i);q.setString(3,item.sku());q.setString(4,item.almacen());q.setString(5,item.descripcion());q.setString(6,item.unidad());q.setBigDecimal(7,item.cantidad());q.setBigDecimal(8,item.precio());q.setString(9,item.indicaciones());q.addBatch();}q.executeBatch();
            }
        }
    }
    private void guardarNota(UUID id,UUID solicitud,UUID original,UUID turno,CorreccionVenta.Tipo tipo,String nota,String motivo,Instant fecha,String encargado,
        BigDecimal valorOriginal,BigDecimal reconocido,BigDecimal totalNuevo,UUID reemplazo,List<Valuada> devueltos,Map<MedioPago,BigDecimal> pagos)throws SQLException {
        String sql="INSERT INTO venta_correccion(id,solicitud,original,turno,tipo,comprobante,motivo,fecha,encargado,valor_original,reconocido,total_reemplazo,reemplazo) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try(var c=unidad.getConnection();var p=c.prepareStatement(sql)){
            p.setObject(1,id);p.setObject(2,solicitud);p.setObject(3,original);p.setObject(4,turno);p.setString(5,tipo.name());p.setString(6,nota);p.setString(7,motivo);p.setTimestamp(8,Timestamp.from(fecha));p.setString(9,encargado);
            p.setBigDecimal(10,valorOriginal);p.setBigDecimal(11,reconocido);p.setBigDecimal(12,totalNuevo);p.setObject(13,reemplazo);p.executeUpdate();
            try(var q=c.prepareStatement("INSERT INTO venta_correccion_item(correccion,posicion,cantidad,reingresar,valor_original) VALUES(?,?,?,?,?)")){
                for(var fila:devueltos){q.setObject(1,id);q.setInt(2,fila.item().posicion());q.setBigDecimal(3,fila.item().cantidad());q.setBoolean(4,fila.item().reingresar());q.setBigDecimal(5,fila.valor());q.addBatch();}q.executeBatch();
            }
            try(var q=c.prepareStatement("INSERT INTO venta_correccion_pago(correccion,medio,importe) VALUES(?,?,?)")){
                for(var pago:pagos.entrySet()){q.setObject(1,id);q.setString(2,pago.getKey().name());q.setBigDecimal(3,pago.getValue());q.addBatch();}q.executeBatch();
            }
        }
    }
    private static final String COLUMNAS="id,secuencia,solicitud,original,turno,tipo,comprobante,motivo,fecha,encargado,valor_original,reconocido,total_reemplazo,diferencia,reemplazo";
    public CorreccionVenta consultar(UUID id) {
        try(var c=unidad.getConnection();var p=c.prepareStatement("SELECT "+COLUMNAS+" FROM venta_correccion WHERE id=?")){
            p.setObject(1,id);try(var r=p.executeQuery()){if(!r.next())throw new ValidacionException("Nota no encontrada");return leer(c,r);}
        }catch(SQLException e){throw new PersistenciaException("No se pudo consultar la nota",e);}
    }
    public List<CorreccionVenta> listar(UUID original,long despues,int limite){
        if(original==null || despues<0 || limite<1 || limite>100)throw new ValidacionException("Indica operación, cursor y página válidos");
        try(var c=unidad.getConnection();var p=c.prepareStatement("SELECT "+COLUMNAS+" FROM venta_correccion WHERE original=? AND secuencia>? ORDER BY secuencia LIMIT ?")){
            p.setObject(1,original);p.setLong(2,despues);p.setInt(3,limite);try(var r=p.executeQuery()){var lista=new ArrayList<CorreccionVenta>();while(r.next())lista.add(leer(c,r));return List.copyOf(lista);}
        }catch(SQLException e){throw new PersistenciaException("No se pudo consultar el historial de notas",e);}
    }
    private CorreccionVenta leer(Connection c,ResultSet r)throws SQLException{
        UUID id=r.getObject(1,UUID.class);var devueltos=new ArrayList<DevolucionItem>();var pagos=new EnumMap<MedioPago,BigDecimal>(MedioPago.class);
        try(var p=c.prepareStatement("SELECT posicion,cantidad,reingresar FROM venta_correccion_item WHERE correccion=? ORDER BY posicion")){p.setObject(1,id);try(var filas=p.executeQuery()){while(filas.next())devueltos.add(new DevolucionItem(filas.getInt(1),filas.getBigDecimal(2),filas.getBoolean(3)));}}
        try(var p=c.prepareStatement("SELECT medio,importe FROM venta_correccion_pago WHERE correccion=?")){p.setObject(1,id);try(var filas=p.executeQuery()){while(filas.next())pagos.put(MedioPago.valueOf(filas.getString(1)),filas.getBigDecimal(2));}}
        return new CorreccionVenta(id,r.getLong(2),r.getObject(3,UUID.class),r.getObject(4,UUID.class),r.getObject(5,UUID.class),CorreccionVenta.Tipo.valueOf(r.getString(6)),r.getString(7),r.getString(8),r.getTimestamp(9).toInstant(),r.getString(10),r.getBigDecimal(11),r.getBigDecimal(12),r.getBigDecimal(13),r.getBigDecimal(14),r.getObject(15,UUID.class),devueltos,pagos);
    }
    private static BigDecimal moneda(BigDecimal n,boolean cero){
        if(n==null || n.signum()<0 || !cero&&n.signum()==0 || n.stripTrailingZeros().scale()>2 || n.compareTo(new BigDecimal("100000000000000"))>=0)
            throw new ValidacionException("Importe inválido; usa hasta dos decimales");
        return n.setScale(2);
    }
    private static boolean admiteCorrecciones(Operacion op) {
        return op.estado()==Operacion.Estado.ENTREGADA || op.estado()==Operacion.Estado.DEVOLUCION_PARCIAL || op.estado()==Operacion.Estado.CAMBIO_PARCIAL;
    }
    private static Map<MedioPago,BigDecimal> medios(Map<MedioPago,BigDecimal> pagos){
        var resultado=new EnumMap<MedioPago,BigDecimal>(MedioPago.class);pagos.forEach((medio,importe)->resultado.put(Objects.requireNonNull(medio),moneda(importe,false)));return Collections.unmodifiableMap(resultado);
    }
    private static String obligatorio(String texto,String campo){if(texto==null||texto.isBlank())throw new ValidacionException(campo+" obligatorio");return texto.strip();}
    private static void parte(StringBuilder b,Object... valores){for(var v:valores){String s=String.valueOf(v);b.append(s.length()).append(':').append(s);}}
    private static String numero(BigDecimal n){return n.stripTrailingZeros().toPlainString();}
    private static UUID derivar(UUID id,String accion){return UUID.nameUUIDFromBytes((id+":"+accion).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
}
