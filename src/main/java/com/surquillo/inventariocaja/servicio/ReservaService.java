package com.surquillo.inventariocaja.servicio;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.*;
import com.surquillo.inventariocaja.repositorio.StockRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;

/** Coordina reservas por línea; requiere una transacción compartida con Caja para despachos reales. */
public class ReservaService {
    @FunctionalInterface public interface AutorizadorDespacho { void verificar(UUID separacionId); }
    private final StockRepository repo;
    private final Clock reloj;
    private final StockService calculos = new StockService();
    private final AutorizadorDespacho autorizador;
    public ReservaService(StockRepository repo, Clock reloj, AutorizadorDespacho autorizador) {
        this.repo = Objects.requireNonNull(repo); this.reloj = Objects.requireNonNull(reloj);
        this.autorizador = Objects.requireNonNull(autorizador);
    }
    public Reserva proponer(ClaveStock clave, BigDecimal solicitada) {
        return calculos.proponerReserva(repo.buscar(clave).orElseThrow(() -> new ValidacionException("Stock no registrado")),solicitada);
    }
    public Optional<ReservaOperacion> consultar(UUID id) { return repo.buscarReserva(id); }

    public StockRepository.ResultadoReserva crear(UUID solicitud, UUID reservaId, UUID separacionId, ClaveStock clave,
                                                   BigDecimal solicitada, BigDecimal aceptada) {
        Objects.requireNonNull(separacionId);
        String huella = huella("CREAR", reservaId, separacionId, solicitada, aceptada);
        return repo.registrarReserva(solicitud,huella,clave,reservaId,ctx -> {
            if (ctx.reserva().isPresent()) throw new ValidacionException("La reserva ya existe");
            SaldoStock.validarCantidad(solicitada,ctx.saldo().permiteFraccion());
            if (solicitada.signum() <= 0 || aceptada == null || aceptada.compareTo(solicitada)>0)
                throw new ValidacionException("Cantidad solicitada o aceptada no válida");
            SaldoStock saldo = calculos.reservar(ctx.saldo(),aceptada);
            var reserva = new ReservaOperacion(reservaId,separacionId,clave,solicitada,aceptada,ReservaOperacion.Estado.ACTIVA);
            return resultado(solicitud,ctx.saldo(),saldo,reserva,MovimientoStock.Tipo.RESERVA,aceptada);
        });
    }
    public StockRepository.ResultadoReserva ampliar(UUID solicitud, UUID reservaId, ClaveStock clave, BigDecimal cantidad) {
        String huella = huella("AMPLIAR",reservaId,cantidad);
        return repo.registrarReserva(solicitud,huella,clave,reservaId,ctx -> {
            ReservaOperacion r = activa(ctx,clave);
            if (cantidad == null || cantidad.compareTo(r.faltante())>0) throw new ValidacionException("Cantidad superior al faltante");
            SaldoStock saldo = calculos.reservar(ctx.saldo(),cantidad);
            var nueva = new ReservaOperacion(r.id(),r.separacionId(),clave,r.solicitada(),r.reservada().add(cantidad),r.estado());
            return resultado(solicitud,ctx.saldo(),saldo,nueva,MovimientoStock.Tipo.AMPLIACION_RESERVA,cantidad);
        });
    }
    public StockRepository.ResultadoReserva cancelar(UUID solicitud, UUID reservaId, ClaveStock clave) {
        return repo.registrarReserva(solicitud,huella("CANCELAR",reservaId),clave,reservaId,ctx -> {
            var r = activa(ctx,clave);
            SaldoStock saldo = calculos.liberarReserva(ctx.saldo(),r.reservada());
            var nueva = new ReservaOperacion(r.id(),r.separacionId(),clave,r.solicitada(),BigDecimal.ZERO,ReservaOperacion.Estado.CANCELADA);
            return resultado(solicitud,ctx.saldo(),saldo,nueva,MovimientoStock.Tipo.CANCELACION_RESERVA,r.reservada());
        });
    }
    public StockRepository.ResultadoReserva despachar(UUID solicitud, UUID reservaId, ClaveStock clave) {
        return repo.registrarReserva(solicitud,huella("DESPACHAR",reservaId),clave,reservaId,ctx -> {
            var r = activa(ctx,clave);
            if (r.faltante().signum()!=0) throw new ValidacionException("Falta reservar unidades para el despacho completo");
            autorizador.verificar(r.separacionId());
            SaldoStock saldo = calculos.despacharReserva(ctx.saldo(),r.reservada());
            var nueva = new ReservaOperacion(r.id(),r.separacionId(),clave,r.solicitada(),r.reservada(),ReservaOperacion.Estado.DESPACHADA);
            return resultado(solicitud,ctx.saldo(),saldo,nueva,MovimientoStock.Tipo.DESPACHO_RESERVA,r.reservada());
        });
    }
    private ReservaOperacion activa(StockRepository.ContextoReserva ctx, ClaveStock clave) {
        var r = ctx.reserva().orElseThrow(() -> new ValidacionException("Reserva no encontrada"));
        if (!r.clave().equals(clave) || r.estado()!=ReservaOperacion.Estado.ACTIVA)
            throw new ValidacionException("Reserva no activa para este stock");
        return r;
    }
    private StockRepository.ResultadoReserva resultado(UUID id, SaldoStock anterior, SaldoStock nuevo,
                                                      ReservaOperacion reserva, MovimientoStock.Tipo tipo, BigDecimal cantidad) {
        var movimiento = new MovimientoStock(id,reloj.instant(),tipo,nuevo.sku(),nuevo.almacen(),cantidad,
                nuevo.fisico().subtract(anterior.fisico()),anterior.fisico(),nuevo.fisico(),reserva.id().toString(),"");
        return new StockRepository.ResultadoReserva(nuevo,movimiento,reserva);
    }
    private String huella(Object... valores) {
        var b = new StringBuilder();
        for (var valor: valores) {
            String texto = valor == null ? "null" : valor instanceof BigDecimal n ? n.stripTrailingZeros().toPlainString() : valor.toString();
            b.append(texto.length()).append(':').append(texto);
        }
        return b.toString();
    }
}
