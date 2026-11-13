package com.surquillo.inventariocaja.repositorio;

import com.surquillo.inventariocaja.modelo.stock.ClaveStock;
import com.surquillo.inventariocaja.modelo.stock.MovimientoStock;
import com.surquillo.inventariocaja.modelo.stock.SaldoStock;
import com.surquillo.inventariocaja.modelo.stock.ReservaOperacion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Cada escritura debe guardar saldo y movimiento de forma atómica e idempotente. */
public interface StockRepository {
    void configurarMinimo(ClaveStock clave, java.math.BigDecimal cantidad);
    java.math.BigDecimal minimo(ClaveStock clave);
    record Estado(SaldoStock saldo, MovimientoStock movimiento) {}
    record ContextoReserva(SaldoStock saldo, Optional<ReservaOperacion> reserva) {}
    record ResultadoReserva(SaldoStock saldo, MovimientoStock movimiento, ReservaOperacion reserva) {}
    Optional<SaldoStock> buscar(ClaveStock clave);
    List<MovimientoStock> historial(ClaveStock clave);
    List<com.surquillo.inventariocaja.modelo.stock.MovimientoPaginado> historial(ClaveStock clave,long despues,int limite);
    Optional<SaldoStock> buscarAlCorte(ClaveStock clave,java.time.Instant corteExclusivo);
    Estado registrar(UUID solicitud, String huella, ClaveStock clave, Function<Optional<SaldoStock>, Estado> operacion);
    Optional<ReservaOperacion> buscarReserva(UUID reservaId);
    ResultadoReserva registrarReserva(UUID solicitud, String huella, ClaveStock clave, UUID reservaId,
                                       Function<ContextoReserva, ResultadoReserva> operacion);
}
