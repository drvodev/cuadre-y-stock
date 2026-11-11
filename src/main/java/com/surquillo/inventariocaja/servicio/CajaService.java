package com.surquillo.inventariocaja.servicio;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.caja.*;
import com.surquillo.inventariocaja.repositorio.TurnoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/** Reglas de caja independientes de las pantallas y del origen comercial de los pagos. */
public class CajaService {
    private record Apertura(LocalDate fecha,String encargado,BigDecimal fondo) {}
    private record Registro(MovimientoCaja.Tipo tipo,MovimientoCaja.Fuente fuente,Map<MedioPago,BigDecimal> importes,String referencia) {}
    private record Confirmacion(long version,BigDecimal contado,String firma,String observacion) {}
    public record Cobro(TurnoRepository.Estado estado,BigDecimal vuelto) {}
    private final TurnoRepository repo;
    private final Clock reloj;
    public CajaService(TurnoRepository repo,Clock reloj) { this.repo=Objects.requireNonNull(repo); this.reloj=Objects.requireNonNull(reloj); }
    public TurnoRepository.Estado abrir(UUID solicitud,UUID turno,String caja,LocalDate fecha,String encargado,BigDecimal fondo) {
        String cajaNormal=texto(caja,"Caja"), firma=texto(encargado,"Encargado");
        Objects.requireNonNull(fecha); BigDecimal inicial=importe(fondo,true);
        var comando=new Apertura(fecha,firma,inicial);
        return repo.aplicar(solicitud,comando,cajaNormal,turno,anterior->{
            if(anterior.isPresent()) throw new ValidacionException("El identificador del turno ya existe");
            return new TurnoRepository.Estado(new Turno(turno,cajaNormal,fecha,firma,inicial,0,false),List.of(),null);
        });
    }
    public TurnoRepository.Estado consultar(UUID turno) { return repo.consultar(turno).orElseThrow(()->new ValidacionException("Turno no encontrado")); }

    public Cobro cobrar(UUID solicitud,UUID turno,BigDecimal total,Map<MedioPago,BigDecimal> pagos,BigDecimal efectivoRecibido,String referencia) {
        Map<MedioPago,BigDecimal> lineas=lineas(pagos); BigDecimal monto=importe(total,false);
        BigDecimal suma=lineas.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
        if(suma.compareTo(monto)!=0) throw new ValidacionException("Los pagos deben cubrir exactamente el importe a cobrar");
        BigDecimal recibido=importe(efectivoRecibido,true);
        BigDecimal aplicado=lineas.getOrDefault(MedioPago.EFECTIVO,BigDecimal.ZERO);
        if(recibido.compareTo(aplicado)<0 || (aplicado.signum()==0 && recibido.signum()!=0))
            throw new ValidacionException("Revisa el efectivo recibido");
        var estado=registrar(solicitud,turno,MovimientoCaja.Tipo.COBRO,MovimientoCaja.Fuente.CAJA,lineas,referencia);
        return new Cobro(estado,recibido.subtract(aplicado));
    }
    public TurnoRepository.Estado registrar(UUID solicitud,UUID turno,MovimientoCaja.Tipo tipo,MovimientoCaja.Fuente fuente,
                                            Map<MedioPago,BigDecimal> pagos,String referencia) {
        Objects.requireNonNull(tipo); Objects.requireNonNull(fuente);
        Map<MedioPago,BigDecimal> lineas=lineas(pagos); String ref=texto(referencia,"Referencia");
        if((tipo==MovimientoCaja.Tipo.COBRO || tipo==MovimientoCaja.Tipo.OTRO_INGRESO) && fuente!=MovimientoCaja.Fuente.CAJA)
            throw new ValidacionException("Los ingresos requieren una caja");
        if(tipo==MovimientoCaja.Tipo.RETIRO && (fuente!=MovimientoCaja.Fuente.CAJA || lineas.size()!=1 || !lineas.containsKey(MedioPago.EFECTIVO)))
            throw new ValidacionException("El retiro de fondos debe ser efectivo desde caja");
        var comando=new Registro(tipo,fuente,lineas,ref);
        String caja=consultar(turno).turno().caja();
        return repo.aplicar(solicitud,comando,caja,turno,anterior->{
            var estado=abierto(anterior);
            var movimientos=new ArrayList<>(estado.movimientos());
            movimientos.add(new MovimientoCaja(solicitud,reloj.instant(),tipo,fuente,lineas,ref));
            return new TurnoRepository.Estado(version(estado.turno(),false),movimientos,null);
        });
    }
    public BigDecimal efectivoEsperado(UUID turno) {
        var estado=consultar(turno);
        return estado.turno().fondoInicial().add(flujo(estado).get(MedioPago.EFECTIVO));
    }
    public Map<MedioPago,BigDecimal> flujo(TurnoRepository.Estado estado) {
        Map<MedioPago,BigDecimal> totales=new EnumMap<>(MedioPago.class);
        for(var medio:MedioPago.values()) totales.put(medio,new BigDecimal("0.00"));
        for(var movimiento:estado.movimientos()) if(movimiento.fuente()==MovimientoCaja.Fuente.CAJA)
            movimiento.importes().forEach((medio,monto)->totales.merge(medio,movimiento.ingreso()?monto:monto.negate(),BigDecimal::add));
        return Map.copyOf(totales);
    }
    public TurnoRepository.Estado cerrar(UUID solicitud,UUID turno,long version,BigDecimal contado,String firma,String observacion) {
        BigDecimal efectivo=importe(contado,true); String nombre=texto(firma,"Firma"); String nota=observacion==null?"":observacion.strip();
        String caja=consultar(turno).turno().caja(); var comando=new Confirmacion(version,efectivo,nombre,nota);
        return repo.aplicar(solicitud,comando,caja,turno,anterior->{
            var estado=abierto(anterior);
            if(estado.turno().version()!=version) throw new ValidacionException("La caja cambió después del conteo; vuelve a revisar el cuadre");
            var flujos=flujo(estado); BigDecimal esperado=estado.turno().fondoInicial().add(flujos.get(MedioPago.EFECTIVO));
            BigDecimal diferencia=efectivo.subtract(esperado);
            if(diferencia.signum()!=0 && nota.isBlank()) throw new ValidacionException("Explica la diferencia antes de cerrar");
            var cierre=new Cierre(reloj.instant(),nombre,esperado,efectivo,diferencia,nota,flujos);
            return new TurnoRepository.Estado(version(estado.turno(),true),estado.movimientos(),cierre);
        });
    }
    private TurnoRepository.Estado abierto(Optional<TurnoRepository.Estado> estado) {
        var valor=estado.orElseThrow(()->new ValidacionException("Turno no encontrado"));
        if(valor.turno().cerrado()) throw new ValidacionException("La caja está cerrada; abre un nuevo turno"); return valor;
    }
    private Turno version(Turno t,boolean cerrado) { return new Turno(t.id(),t.caja(),t.fechaOperativa(),t.encargado(),t.fondoInicial(),t.version()+1,cerrado); }
    private Map<MedioPago,BigDecimal> lineas(Map<MedioPago,BigDecimal> pagos) {
        if(pagos==null || pagos.isEmpty()) throw new ValidacionException("Indica los medios e importes del pago");
        Map<MedioPago,BigDecimal> copia=new EnumMap<>(MedioPago.class);
        pagos.forEach((medio,monto)->copia.put(Objects.requireNonNull(medio),importe(monto,false))); return Map.copyOf(copia);
    }
    private BigDecimal importe(BigDecimal monto,boolean permiteCero) {
        if(monto==null || monto.signum()<0 || (!permiteCero && monto.signum()==0)) throw new ValidacionException("Importe inválido");
        try { return monto.setScale(2,RoundingMode.UNNECESSARY); }
        catch(ArithmeticException e) { throw new ValidacionException("Los importes admiten hasta dos decimales"); }
    }
    private String texto(String valor,String campo) {
        if(valor==null || valor.isBlank()) throw new ValidacionException(campo+" es obligatorio"); return valor.strip();
    }
}
