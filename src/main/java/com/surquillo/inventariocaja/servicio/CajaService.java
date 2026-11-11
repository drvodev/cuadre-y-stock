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
    public record Cobro(TurnoRepository.Estado estado,BigDecimal vuelto) {}
    private final TurnoRepository repo;
    private final Clock reloj;
    public CajaService(TurnoRepository repo,Clock reloj) { this.repo=Objects.requireNonNull(repo); this.reloj=Objects.requireNonNull(reloj); }
    public TurnoRepository.Estado abrir(UUID solicitud,UUID turno,String caja,LocalDate fecha,String encargado,BigDecimal fondo) {
        String cajaNormal=texto(caja,"Caja"), firma=texto(encargado,"Encargado");
        Objects.requireNonNull(fecha); BigDecimal inicial=importe(fondo,true);
        var comando=huella("ABRIR",fecha,firma,inicial);
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
        var partes=new ArrayList<Object>(); partes.add("REGISTRAR"); partes.add(tipo); partes.add(fuente); partes.add(ref);
        for(var medio:MedioPago.values()) if(lineas.containsKey(medio)) { partes.add(medio); partes.add(lineas.get(medio)); }
        var comando=huella(partes.toArray());
        String caja=consultar(turno).turno().caja();
        return repo.aplicar(solicitud,comando,caja,turno,anterior->{
            var estado=abierto(anterior);
            var movimientos=new ArrayList<>(estado.movimientos());
            movimientos.add(new MovimientoCaja(solicitud,reloj.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS),tipo,fuente,lineas,ref));
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
        return cerrar(solicitud,turno,version,contado,firma,observacion,Map.of());
    }
    public Map<MedioPago,VerificacionMedio> resumenDigital(UUID turno) { return resumenDigital(consultar(turno)); }
    private Map<MedioPago,VerificacionMedio> resumenDigital(TurnoRepository.Estado estado) {
        Map<MedioPago,VerificacionMedio> resumen=new EnumMap<>(MedioPago.class);
        for(var medio:MedioPago.values()) if(medio!=MedioPago.EFECTIVO) {
            BigDecimal cobros=BigDecimal.ZERO,devoluciones=BigDecimal.ZERO,egresos=BigDecimal.ZERO,otros=BigDecimal.ZERO;
            for(var m:estado.movimientos()) if(m.fuente()==MovimientoCaja.Fuente.CAJA) {
                BigDecimal monto=m.importes().getOrDefault(medio,BigDecimal.ZERO);
                switch(m.tipo()) { case COBRO->cobros=cobros.add(monto); case DEVOLUCION->devoluciones=devoluciones.add(monto); case OTRO_INGRESO->otros=otros.add(monto); default->egresos=egresos.add(monto); }
            }
            resumen.put(medio,new VerificacionMedio(cobros,devoluciones,egresos,otros));
        }
        return Map.copyOf(resumen);
    }
    public TurnoRepository.Estado cerrar(UUID solicitud,UUID turno,long version,BigDecimal contado,String firma,String observacion,Map<MedioPago,VerificacionMedio> verificaciones) {
        BigDecimal efectivo=importe(contado,true); String nombre=texto(firma,"Firma"); String nota=observacion==null?"":observacion.strip();
        var verificados=Map.copyOf(verificaciones);
        if(verificados.containsKey(MedioPago.EFECTIVO)) throw new ValidacionException("El efectivo se verifica con el conteo");
        var partes=new ArrayList<Object>(List.of("CERRAR",version,efectivo,nombre,nota));
        for(var medio:MedioPago.values()) if(verificados.containsKey(medio)) { partes.add(medio); partes.add(verificados.get(medio)); }
        String caja=consultar(turno).turno().caja(); var comando=huella(partes.toArray());
        return repo.aplicar(solicitud,comando,caja,turno,anterior->{
            var estado=abierto(anterior);
            if(estado.turno().version()!=version) throw new ValidacionException("La caja cambió después del conteo; vuelve a revisar el cuadre");
            var flujos=flujo(estado); BigDecimal esperado=estado.turno().fondoInicial().add(flujos.get(MedioPago.EFECTIVO));
            BigDecimal diferencia=efectivo.subtract(esperado);
            if(diferencia.signum()!=0 && nota.isBlank()) throw new ValidacionException("Explica la diferencia antes de cerrar");
            var digitales=new EnumMap<MedioPago,DetalleCierre>(MedioPago.class);
            for(var entrada:resumenDigital(estado).entrySet()) {
                var verificado=verificados.get(entrada.getKey());
                if(verificado==null && !entrada.getValue().equals(VerificacionMedio.cero())) throw new ValidacionException("Verifica el medio "+entrada.getKey()+" antes del cierre");
                var detalle=new DetalleCierre(entrada.getValue(),verificado==null?VerificacionMedio.cero():verificado);
                if(detalle.tieneDiferencia() && nota.isBlank()) throw new ValidacionException("Explica las diferencias digitales antes de cerrar");
                digitales.put(entrada.getKey(),detalle);
            }
            var cierre=new Cierre(reloj.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS),nombre,esperado,efectivo,diferencia,nota,flujos,digitales);
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
    private String huella(Object... valores) {
        var resultado=new StringBuilder();
        for(Object valor:valores) { String s=valor.toString(); resultado.append(s.length()).append(':').append(s); }
        return resultado.toString();
    }
}
