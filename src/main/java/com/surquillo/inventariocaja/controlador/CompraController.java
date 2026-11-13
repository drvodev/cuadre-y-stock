package com.surquillo.inventariocaja.controlador;
import com.surquillo.inventariocaja.modelo.caja.*;
import com.surquillo.inventariocaja.repositorio.CompraRepository;
import com.surquillo.inventariocaja.servicio.CompraService;
import java.math.BigDecimal;
import java.util.*;

/** Entrada funcional de Compras; las validaciones y transacciones residen en el servicio. */
public class CompraController {
    private final CompraService servicio;
    public CompraController(CompraService servicio){this.servicio=Objects.requireNonNull(servicio);}
    public Compra registrar(UUID solicitud,UUID compra,UUID turno,String proveedor,Documento documento,MovimientoCaja.Fuente fuente,
                            BigDecimal total,List<DetalleCompra> items,Map<MedioPago,BigDecimal> pagos) {
        return servicio.registrar(solicitud,compra,turno,proveedor,documento,fuente,total,items,pagos);
    }
    public Compra consultar(UUID id){return servicio.consultar(id);}
    public List<CompraRepository.Resumen> listar(long despues,int limite){return servicio.listar(despues,limite);}
}
