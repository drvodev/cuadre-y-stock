package com.surquillo.inventariocaja.controlador;

import com.surquillo.inventariocaja.servicio.ReporteService;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

public final class ReporteController {
    private final ReporteService servicio;
    public ReporteController(ReporteService servicio) { this.servicio=Objects.requireNonNull(servicio); }
    public void diario(LocalDate dia,Path archivo) throws Exception { servicio.exportar(dia,dia,archivo); }
    public void mensual(YearMonth mes,Path archivo) throws Exception { servicio.exportar(mes.atDay(1),mes.atEndOfMonth(),archivo); }
}
