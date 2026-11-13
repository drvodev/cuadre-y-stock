package com.surquillo.inventariocaja.servicio;

import com.surquillo.inventariocaja.configuracion.UnidadTrabajoPostgres;
import com.surquillo.inventariocaja.excepcion.*;
import com.surquillo.inventariocaja.modelo.caja.*;
import com.surquillo.inventariocaja.modelo.stock.*;
import com.surquillo.inventariocaja.repositorio.*;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Clock;
import java.util.*;

/** Operaciones comerciales con documento, caja y stock en una misma transacción. */
public class VentaService {
    private final UnidadTrabajoPostgres unidad;
    private final Clock reloj;
    private final CajaService caja;
    private final InventarioService inventario;
    private final ReservaService reservas;
    private final CatalogoService catalogo;
    public VentaService(DataSource ds,Clock reloj) {
        this.unidad=new UnidadTrabajoPostgres(ds); this.reloj=Objects.requireNonNull(reloj);
        caja=new CajaService(new TurnoPostgresRepository(unidad),reloj);
        inventario=new InventarioService(new StockPostgresRepository(unidad),reloj);
        catalogo=new CatalogoService(new ProductoPostgresRepository(unidad));
        reservas=new ReservaService(new StockPostgresRepository(unidad),reloj,id->{
            var op=consultar(id); if(op.saldo().signum()!=0||op.estado()==Operacion.Estado.ANULADA) throw new ValidacionException("La separación tiene saldo o está anulada");
        });
    }
    public UUID registrar(UUID solicitud,UUID operacion,UUID turno,Operacion.Modalidad modalidad,Documento documento,
                           String cliente,String contacto,List<ItemOperacion> items,Map<MedioPago,BigDecimal> pagos,
                           BigDecimal efectivoRecibido,Map<Integer,BigDecimal> reservasAceptadas) {
        Objects.requireNonNull(solicitud); Objects.requireNonNull(operacion); Objects.requireNonNull(turno); Objects.requireNonNull(modalidad); Objects.requireNonNull(documento);
        List<ItemOperacion> lineas=List.copyOf(items); var medios=Map.copyOf(pagos); var aceptadas=Map.copyOf(reservasAceptadas);
        String nombre=texto(cliente), telefono=texto(contacto);
        if(lineas.isEmpty()) throw new ValidacionException("Agrega al menos un ítem");
        if(modalidad==Operacion.Modalidad.SEPARACION && (documento.tipo()!=Documento.Tipo.NOTA_VENTA||nombre.isBlank())) throw new ValidacionException("La separación requiere Nota de Venta y nombre del cliente");
        var firma=new StringBuilder(huella("REGISTRAR",operacion,turno,modalidad,documento.tipo(),documento.numero(),documento.manual(),nombre,telefono));
        for(var item:lineas) firma.append(huella(item.sku(),item.almacen(),item.descripcion(),item.unidad(),item.cantidad(),item.precio(),item.indicaciones()));
        firma.append(firmaPagos(medios,efectivoRecibido));
        aceptadas.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->firma.append(huella(e.getKey(),e.getValue().stripTrailingZeros().toPlainString())));
        return ejecutar(()-> {
            var previo=reintento(solicitud,firma.toString()); if(previo.isPresent()) return previo.get();
            BigDecimal total=lineas.stream().map(ItemOperacion::total).reduce(new BigDecimal("0.00"),BigDecimal::add);
            BigDecimal abono=suma(medios);
            if(abono.signum()<=0||abono.compareTo(total)>0||(modalidad==Operacion.Modalidad.VENTA&&abono.compareTo(total)!=0)) throw new ValidacionException("Una venta requiere pago completo; una separación admite abono hasta su total");
            caja.cobrar(solicitud,turno,abono,medios,efectivoRecibido,documento.numero());
            try(Connection c=unidad.getConnection();var p=c.prepareStatement("INSERT INTO venta_operacion(id,modalidad,tipo,numero,manual,cliente,contacto,total,abonado,estado,fecha) VALUES(?,?,?,?,?,?,?,?,?,?,?)")) {
                p.setObject(1,operacion); p.setString(2,modalidad.name()); p.setString(3,documento.tipo().name()); p.setString(4,documento.numero()); p.setBoolean(5,documento.manual()); p.setString(6,nombre); p.setString(7,telefono); p.setBigDecimal(8,total); p.setBigDecimal(9,abono);
                p.setString(10,modalidad==Operacion.Modalidad.VENTA?Operacion.Estado.ENTREGADA.name():abono.compareTo(total)==0?Operacion.Estado.PAGADA.name():Operacion.Estado.CON_SALDO.name());
                p.setTimestamp(11,Timestamp.from(reloj.instant())); p.executeUpdate();
            }
            documento(operacion,documento);
            for(int i=0;i<lineas.size();i++) {
                ItemOperacion item=lineas.get(i); guardarItem(operacion,i,item);
                if(item.sku().isEmpty()) continue;
                var articulo=catalogo.buscarSku(item.sku());
                if(articulo.isPresent()) {
                    if(!articulo.get().activo()) throw new ValidacionException("El artículo está inactivo");
                    SaldoStock.validarCantidad(item.cantidad(),articulo.get().permiteFraccion());
                }
                ClaveStock clave=new ClaveStock(item.sku(),item.almacen());
                var saldo=inventario.consultar(clave); if(saldo.isEmpty()) continue;
                if(modalidad==Operacion.Modalidad.VENTA) inventario.venta(derivar(solicitud,"venta-"+i),clave,item.cantidad(),documento.numero());
                else {
                    var propuesta=reservas.proponer(clave,item.cantidad());
                    BigDecimal aceptada=aceptadas.get(i);
                    if(aceptada==null) {
                        if(!propuesta.suficiente()) throw new ValidacionException("Confirma la cantidad disponible para reservar el ítem "+(i+1)+"; faltan "+propuesta.faltante().toPlainString());
                        aceptada=item.cantidad();
                    }
                    SaldoStock.validarCantidad(aceptada,saldo.get().permiteFraccion());
                    if(aceptada.compareTo(item.cantidad())>0) throw new ValidacionException("La reserva supera la cantidad pedida");
                    if(aceptada.signum()>0) reservas.crear(derivar(solicitud,"reserva-"+i),derivar(operacion,"linea-"+i),operacion,clave,item.cantidad(),aceptada);
                }
            }
            guardarSolicitud(solicitud,firma.toString(),operacion); return operacion;
        });
    }
    public UUID abonar(UUID solicitud,UUID operacion,UUID turno,Map<MedioPago,BigDecimal> pagos,BigDecimal recibido) {
        var medios=Map.copyOf(pagos); String firma=huella("ABONAR",operacion,turno)+firmaPagos(medios,recibido);
        return ejecutar(()-> {
            var previo=reintento(solicitud,firma); if(previo.isPresent()) return previo.get();
            var op=consultar(operacion);
            if(op.modalidad()!=Operacion.Modalidad.SEPARACION||(op.estado()!=Operacion.Estado.CON_SALDO&&op.estado()!=Operacion.Estado.PAGADA)) throw new ValidacionException("La operación no admite abonos");
            BigDecimal monto=suma(medios); if(monto.signum()<=0||monto.compareTo(op.saldo())>0) throw new ValidacionException("El abono debe ser positivo y no superar el saldo");
            caja.cobrar(solicitud,turno,monto,medios,recibido,op.documento().numero());
            BigDecimal abonado=op.abonado().add(monto);
            actualizar(operacion,abonado,abonado.compareTo(op.total())==0?Operacion.Estado.PAGADA:Operacion.Estado.CON_SALDO);
            guardarSolicitud(solicitud,firma,operacion); return operacion;
        });
    }
    public UUID entregar(UUID solicitud,UUID operacion,Documento finalDocumento) {
        String firma=huella("ENTREGAR",operacion,finalDocumento==null?"":finalDocumento.tipo(),finalDocumento==null?"":finalDocumento.numero(),finalDocumento!=null&&finalDocumento.manual());
        return ejecutar(()-> {
            var previo=reintento(solicitud,firma); if(previo.isPresent()) return previo.get();
            var op=consultar(operacion);
            if(op.modalidad()!=Operacion.Modalidad.SEPARACION||op.estado()!=Operacion.Estado.PAGADA||op.saldo().signum()!=0) throw new ValidacionException("Solo se entrega una separación pagada pendiente de entrega");
            for(int i=0;i<op.items().size();i++) {
                var item=op.items().get(i); if(item.sku().isEmpty()) continue;
                var clave=new ClaveStock(item.sku(),item.almacen()); if(inventario.consultar(clave).isEmpty()) continue;
                UUID reserva=derivar(operacion,"linea-"+i); var actual=reservas.consultar(reserva);
                if(actual.isEmpty()) reservas.crear(derivar(solicitud,"crear-"+i),reserva,operacion,clave,item.cantidad(),item.cantidad());
                else if(actual.get().faltante().signum()>0) reservas.ampliar(derivar(solicitud,"ampliar-"+i),reserva,clave,actual.get().faltante());
                reservas.despachar(derivar(solicitud,"despacho-"+i),reserva,clave);
            }
            if(finalDocumento!=null) documento(operacion,finalDocumento);
            actualizar(operacion,op.abonado(),Operacion.Estado.ENTREGADA); guardarSolicitud(solicitud,firma,operacion); return operacion;
        });
    }
    public Operacion consultar(UUID id) {
        try(Connection c=unidad.getConnection();var p=c.prepareStatement("SELECT modalidad,tipo,numero,manual,cliente,contacto,total,abonado,estado,fecha FROM venta_operacion WHERE id=?")) {
            p.setObject(1,id); try(var r=p.executeQuery()) {
                if(!r.next()) throw new ValidacionException("Operación no encontrada");
                var items=new ArrayList<ItemOperacion>();
                try(var q=c.prepareStatement("SELECT sku,almacen,descripcion,unidad,cantidad,precio,indicaciones FROM venta_item WHERE operacion=? ORDER BY posicion")) {
                    q.setObject(1,id); try(var s=q.executeQuery()) { while(s.next()) items.add(new ItemOperacion(s.getString(1),s.getString(2),s.getString(3),s.getString(4),s.getBigDecimal(5),s.getBigDecimal(6),s.getString(7))); }
                }
                return new Operacion(id,Operacion.Modalidad.valueOf(r.getString(1)),new Documento(Documento.Tipo.valueOf(r.getString(2)),r.getString(3),r.getBoolean(4)),r.getString(5),r.getString(6),r.getBigDecimal(7),r.getBigDecimal(8),Operacion.Estado.valueOf(r.getString(9)),r.getTimestamp(10).toInstant(),items);
            }
        } catch(SQLException e) { throw new PersistenciaException("No se pudo consultar la operación",e); }
    }
    public List<Operacion> listar(int limite) {
        if(limite<1||limite>100) throw new ValidacionException("El límite debe estar entre 1 y 100");
        try(Connection c=unidad.getConnection();var p=c.prepareStatement("SELECT id FROM venta_operacion ORDER BY fecha DESC,id LIMIT ?")) {
            p.setInt(1,limite); try(var r=p.executeQuery()) { var lista=new ArrayList<Operacion>(); while(r.next()) lista.add(consultar(r.getObject(1,UUID.class))); return List.copyOf(lista); }
        } catch(SQLException e) { throw new PersistenciaException("No se pudieron consultar las operaciones",e); }
    }
    public Optional<String> sugerirNumero(Documento.Tipo tipo,String prefijo) {
        String serie=texto(prefijo).toUpperCase(Locale.ROOT);
        try(Connection c=unidad.getConnection();var p=c.prepareStatement("SELECT numero FROM venta_documento WHERE tipo=? ORDER BY secuencia DESC")) {
            p.setString(1,tipo.name()); try(var r=p.executeQuery()) { while(r.next()) {
                String n=r.getString(1); var m=java.util.regex.Pattern.compile("^(.*?)([0-9]+)$").matcher(n);
                if(m.matches()&&m.group(1).equals(serie)) return new DocumentoService().siguiente(n);
            } }
            return Optional.empty();
        } catch(SQLException e) { throw new PersistenciaException("No se pudo consultar la numeración",e); }
    }
    public Operacion buscarDocumento(Documento.Tipo tipo,String numero) {
        String n=new Documento(tipo,numero,true).numero();
        try(Connection c=unidad.getConnection();var p=c.prepareStatement("SELECT operacion FROM venta_documento WHERE tipo=? AND numero=?")) {
            p.setString(1,tipo.name()); p.setString(2,n); try(var r=p.executeQuery()) { if(!r.next()) throw new ValidacionException("Documento no encontrado"); return consultar(r.getObject(1,UUID.class)); }
        } catch(SQLException e) { throw new PersistenciaException("No se pudo buscar el documento",e); }
    }
    /** Anulación completa; las devoluciones parciales requieren otro flujo explícito. */
    public UUID anular(UUID solicitud,UUID operacion,UUID turno,String motivo,Map<MedioPago,BigDecimal> devolucion,Set<Integer> reingresar) {
        String razon=texto(motivo); if(razon.isEmpty()) throw new ValidacionException("Indica el motivo de anulación");
        var pagos=Map.copyOf(devolucion); var posiciones=new TreeSet<>(reingresar);
        String firma=huella("ANULAR",operacion,turno,razon)+firmaPagos(pagos,BigDecimal.ZERO)+huella(posiciones);
        return ejecutar(()-> {
            var previo=reintento(solicitud,firma); if(previo.isPresent()) return previo.get();
            var op=consultar(operacion);
            if(op.estado()==Operacion.Estado.ANULADA||op.estado()==Operacion.Estado.CAMBIADA) throw new ValidacionException("La operación ya fue anulada o cambiada");
            try(var c=unidad.getConnection();var p=c.prepareStatement("SELECT 1 FROM venta_correccion WHERE original=? LIMIT 1")){
                p.setObject(1,operacion);try(var filas=p.executeQuery()){if(filas.next())throw new ValidacionException("La operación ya tiene notas. Usa Devoluciones y cambios para las cantidades restantes");}
            }
            if(posiciones.stream().anyMatch(i->i<0||i>=op.items().size())) throw new ValidacionException("Ítem de devolución inválido");
            BigDecimal monto=suma(pagos);
            if(monto.compareTo(op.abonado())!=0) throw new ValidacionException("La anulación completa requiere devolver el total abonado");
            caja.registrar(solicitud,turno,MovimientoCaja.Tipo.DEVOLUCION,MovimientoCaja.Fuente.CAJA,pagos,"Anulación "+op.documento().numero()+": "+razon);
            for(int i=0;i<op.items().size();i++) {
                var item=op.items().get(i); if(item.sku().isEmpty()) continue;
                var clave=new ClaveStock(item.sku(),item.almacen()); if(inventario.consultar(clave).isEmpty()) continue;
                if(op.estado()==Operacion.Estado.ENTREGADA) {
                    if(posiciones.contains(i)) inventario.entrada(derivar(solicitud,"retorno-"+i),clave,MovimientoStock.Tipo.DEVOLUCION,item.cantidad(),op.documento().numero());
                } else {
                    UUID reserva=derivar(operacion,"linea-"+i);
                    if(reservas.consultar(reserva).isPresent()) reservas.cancelar(derivar(solicitud,"cancelar-"+i),reserva,clave);
                }
            }
            actualizar(operacion,op.abonado(),Operacion.Estado.ANULADA);
            try(Connection c=unidad.getConnection();var p=c.prepareStatement("INSERT INTO venta_anulacion(solicitud,operacion,turno,motivo,devuelto,fecha,reingresados) VALUES(?,?,?,?,?,?,?)")) {
                p.setObject(1,solicitud); p.setObject(2,operacion); p.setObject(3,turno); p.setString(4,razon); p.setBigDecimal(5,monto); p.setTimestamp(6,Timestamp.from(reloj.instant())); p.setString(7,posiciones.toString()); p.executeUpdate();
            }
            guardarSolicitud(solicitud,firma,operacion); return operacion;
        });
    }
    private void guardarItem(UUID op,int posicion,ItemOperacion i) throws SQLException {
        try(Connection c=unidad.getConnection();var p=c.prepareStatement("INSERT INTO venta_item(operacion,posicion,sku,almacen,descripcion,unidad,cantidad,precio,indicaciones) VALUES(?,?,?,?,?,?,?,?,?)")) {
            p.setObject(1,op); p.setInt(2,posicion); p.setString(3,i.sku()); p.setString(4,i.almacen()); p.setString(5,i.descripcion()); p.setString(6,i.unidad()); p.setBigDecimal(7,i.cantidad()); p.setBigDecimal(8,i.precio()); p.setString(9,i.indicaciones()); p.executeUpdate();
        }
    }
    private void documento(UUID op,Documento doc) throws SQLException {
        try(Connection c=unidad.getConnection();var p=c.prepareStatement("INSERT INTO venta_documento(operacion,tipo,numero,manual) VALUES(?,?,?,?)")) {
            p.setObject(1,op); p.setString(2,doc.tipo().name()); p.setString(3,doc.numero()); p.setBoolean(4,doc.manual()); p.executeUpdate();
        }
    }
    private void actualizar(UUID op,BigDecimal abonado,Operacion.Estado estado) throws SQLException {
        try(Connection c=unidad.getConnection();var p=c.prepareStatement("UPDATE venta_operacion SET abonado=?,estado=? WHERE id=?")) { p.setBigDecimal(1,abonado); p.setString(2,estado.name()); p.setObject(3,op); p.executeUpdate(); }
    }
    private Optional<UUID> reintento(UUID id,String firma) throws SQLException {
        Objects.requireNonNull(id);
        try(Connection c=unidad.getConnection();var p=c.prepareStatement("SELECT huella,operacion FROM venta_solicitud WHERE id=?")) {
            p.setObject(1,id); try(var r=p.executeQuery()) { if(!r.next()) return Optional.empty(); if(!firma.equals(r.getString(1))) throw new ValidacionException("La solicitud ya se utilizó con otros datos"); return Optional.of(r.getObject(2,UUID.class)); }
        }
    }
    private void guardarSolicitud(UUID id,String firma,UUID op) throws SQLException {
        try(Connection c=unidad.getConnection();var p=c.prepareStatement("INSERT INTO venta_solicitud(id,huella,operacion) VALUES(?,?,?)")) { p.setObject(1,id); p.setString(2,firma); p.setObject(3,op); p.executeUpdate(); }
    }
    private UUID ejecutar(java.util.concurrent.Callable<UUID> accion) {
        try { return unidad.ejecutar(accion); }
        catch(ValidacionException e) { throw e; }
        catch(Exception e) { throw new PersistenciaException("No se pudo confirmar la operación; consulta su estado antes de reintentar",e); }
    }
    private static String texto(String s) { return s==null?"":s.strip(); }
    private static UUID derivar(UUID id,String accion) { return UUID.nameUUIDFromBytes((id+":"+accion).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    private static BigDecimal suma(Map<MedioPago,BigDecimal> pagos) {
        if(pagos.isEmpty()) throw new ValidacionException("Registra el pago");
        for(var monto:pagos.values()) if(monto==null||monto.signum()<=0) throw new ValidacionException("Importe inválido");
        return pagos.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
    }
    private static String firmaPagos(Map<MedioPago,BigDecimal> pagos,BigDecimal recibido) {
        var s=new StringBuilder(huella(recibido==null?"":recibido.stripTrailingZeros().toPlainString()));
        for(var medio:MedioPago.values()) if(pagos.containsKey(medio)) s.append(huella(medio,pagos.get(medio).stripTrailingZeros().toPlainString())); return s.toString();
    }
    private static String huella(Object... valores) { var s=new StringBuilder(); for(Object v:valores) { String t=Objects.toString(v,""); s.append(t.length()).append(':').append(t); } return s.toString(); }
}
