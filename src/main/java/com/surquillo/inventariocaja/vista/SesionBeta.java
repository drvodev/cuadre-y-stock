package com.surquillo.inventariocaja.vista;

import com.surquillo.inventariocaja.servicio.*;
import com.surquillo.inventariocaja.repositorio.*;
import com.surquillo.inventariocaja.controlador.StockController;
import javafx.beans.property.*;
import javax.sql.DataSource;
import java.time.*;
import java.util.UUID;

public class SesionBeta {
    public final DataSource ds;
    public final CatalogoService catalogo;
    public final InventarioService inventario;
    public final CajaService caja;
    public final VentaService ventas;
    public final TurnoPostgresRepository turnos;
    public final StockController stock;
    public final ObjectProperty<UUID> turno=new SimpleObjectProperty<>();
    public final Clock reloj=Clock.system(ZoneId.of("America/Lima"));
    public SesionBeta(DataSource ds) {
        this.ds=ds; catalogo=new CatalogoService(new ProductoPostgresRepository(ds)); inventario=new InventarioService(new StockPostgresRepository(ds),reloj);
        turnos=new TurnoPostgresRepository(ds); caja=new CajaService(turnos,reloj); ventas=new VentaService(ds,reloj); stock=new StockController(catalogo,inventario);
        turnos.abierto("Surquillo").ifPresent(e->turno.set(e.turno().id()));
    }
    public UUID exigirTurno() {
        if(turno.get()==null||caja.consultar(turno.get()).turno().cerrado()) throw new IllegalArgumentException("Abre o selecciona un turno en Caja antes de continuar");
        return turno.get();
    }
}
