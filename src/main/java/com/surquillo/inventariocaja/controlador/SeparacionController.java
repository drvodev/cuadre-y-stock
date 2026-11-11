package com.surquillo.inventariocaja.controlador;

import com.surquillo.inventariocaja.modelo.stock.ClaveStock;
import com.surquillo.inventariocaja.modelo.stock.Reserva;
import com.surquillo.inventariocaja.repositorio.StockRepository;
import com.surquillo.inventariocaja.servicio.ReservaService;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Solicita la confirmación explícita antes de reservar menos unidades de las pedidas. */
public class SeparacionController {
    private final ReservaService servicio;
    public SeparacionController(ReservaService servicio) { this.servicio=Objects.requireNonNull(servicio); }
    public Reserva proponer(ClaveStock clave, BigDecimal solicitada) { return servicio.proponer(clave,solicitada); }
    /** Llamar solo tras aceptar la propuesta; conservar los mismos argumentos en cada reintento. */
    public StockRepository.ResultadoReserva confirmar(UUID solicitud, UUID reserva, UUID separacion,
                                                       ClaveStock clave, BigDecimal solicitada, BigDecimal aceptada) {
        return servicio.crear(solicitud,reserva,separacion,clave,solicitada,aceptada);
    }
}
