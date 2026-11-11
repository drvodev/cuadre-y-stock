package com.surquillo.inventariocaja.repositorio;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.ClaveStock;
import com.surquillo.inventariocaja.modelo.stock.MovimientoStock;
import com.surquillo.inventariocaja.modelo.stock.SaldoStock;
import com.surquillo.inventariocaja.modelo.stock.ReservaOperacion;
import java.util.*;
import java.util.function.Function;

/** Solo para desarrollo y pruebas; los datos desaparecen al cerrar el proceso. */
public class StockMemoriaRepository implements StockRepository {
    private record Solicitud(String huella, ClaveStock clave, Object estado) {}
    private final Map<ClaveStock, SaldoStock> saldos = new HashMap<>();
    private final Map<ClaveStock, List<MovimientoStock>> movimientos = new HashMap<>();
    private final Map<UUID, Solicitud> solicitudes = new HashMap<>();
    private final Map<UUID, ReservaOperacion> reservas = new HashMap<>();

    @Override public synchronized Optional<SaldoStock> buscar(ClaveStock clave) {
        return Optional.ofNullable(saldos.get(Objects.requireNonNull(clave)));
    }
    @Override public synchronized List<MovimientoStock> historial(ClaveStock clave) {
        return List.copyOf(movimientos.getOrDefault(Objects.requireNonNull(clave), List.of()));
    }
    @Override public synchronized Estado registrar(UUID id, String huella, ClaveStock clave,
                                                    Function<Optional<SaldoStock>, Estado> operacion) {
        Objects.requireNonNull(id); Objects.requireNonNull(huella); Objects.requireNonNull(clave);
        var anterior = solicitudes.get(id);
        if (anterior != null) {
            if (!anterior.huella().equals(huella) || !anterior.clave().equals(clave))
                throw new ValidacionException("El identificador de solicitud ya se usó con otros datos");
            if (!(anterior.estado() instanceof Estado estado)) throw new ValidacionException("Solicitud incompatible");
            return estado;
        }
        Estado resultado = Objects.requireNonNull(operacion.apply(Optional.ofNullable(saldos.get(clave))));
        if (!new ClaveStock(resultado.saldo().sku(), resultado.saldo().almacen()).equals(clave)
                || !resultado.movimiento().id().equals(id)) throw new IllegalStateException("Resultado inconsistente");
        saldos.put(clave, resultado.saldo());
        movimientos.computeIfAbsent(clave, k -> new ArrayList<>()).add(resultado.movimiento());
        solicitudes.put(id, new Solicitud(huella, clave, resultado));
        return resultado;
    }

    @Override public synchronized Optional<ReservaOperacion> buscarReserva(UUID id) {
        return Optional.ofNullable(reservas.get(Objects.requireNonNull(id)));
    }

    @Override public synchronized ResultadoReserva registrarReserva(UUID id, String huella, ClaveStock clave, UUID reservaId,
                                                                    Function<ContextoReserva, ResultadoReserva> operacion) {
        Objects.requireNonNull(id); Objects.requireNonNull(huella); Objects.requireNonNull(clave); Objects.requireNonNull(reservaId);
        var previa = solicitudes.get(id);
        if (previa != null) {
            if (!previa.huella().equals(huella) || !previa.clave().equals(clave) || !(previa.estado() instanceof ResultadoReserva resultado)
                    || !resultado.reserva().id().equals(reservaId))
                throw new ValidacionException("El identificador de solicitud ya se usó con otros datos");
            return (ResultadoReserva) previa.estado();
        }
        SaldoStock saldo = Optional.ofNullable(saldos.get(clave)).orElseThrow(() -> new ValidacionException("Stock no registrado"));
        var resultado = Objects.requireNonNull(operacion.apply(new ContextoReserva(saldo, Optional.ofNullable(reservas.get(reservaId)))));
        if (!resultado.reserva().id().equals(reservaId) || !resultado.reserva().clave().equals(clave)
                || !resultado.movimiento().id().equals(id) || !new ClaveStock(resultado.saldo().sku(), resultado.saldo().almacen()).equals(clave))
            throw new IllegalStateException("Resultado inconsistente");
        saldos.put(clave, resultado.saldo());
        reservas.put(reservaId, resultado.reserva());
        movimientos.computeIfAbsent(clave, k -> new ArrayList<>()).add(resultado.movimiento());
        solicitudes.put(id, new Solicitud(huella, clave, resultado));
        return resultado;
    }
}
