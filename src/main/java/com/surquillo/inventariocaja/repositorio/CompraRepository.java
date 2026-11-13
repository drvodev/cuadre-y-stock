package com.surquillo.inventariocaja.repositorio;
import com.surquillo.inventariocaja.modelo.caja.*;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;

/** Compra confirmada y su solicitud durable; no modifica documentos ya recibidos. */
public interface CompraRepository {
    record Solicitud(String huella,UUID compra) {}
    record Resumen(long secuencia,UUID id,Instant fecha,String proveedor,Documento documento,BigDecimal total,MovimientoCaja.Fuente fuente) {}
    Optional<Solicitud> buscarSolicitud(UUID solicitud);
    Compra guardar(Compra compra,UUID solicitud,String huella);
    Optional<Compra> buscar(UUID id);
    List<Resumen> listar(long despues,int limite);
}
