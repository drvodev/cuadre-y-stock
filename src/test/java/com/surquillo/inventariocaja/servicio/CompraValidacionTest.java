package com.surquillo.inventariocaja.servicio;
import com.surquillo.inventariocaja.modelo.caja.*;
import com.surquillo.inventariocaja.excepcion.ValidacionException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CompraValidacionTest {
    BigDecimal n(String v){return new BigDecimal(v);}
    Compra compra(BigDecimal total,List<DetalleCompra> items,Map<MedioPago,BigDecimal> pagos) {
        return new Compra(UUID.randomUUID(),0,UUID.randomUUID(),Instant.EPOCH,"Proveedor ficticio",new Documento(Documento.Tipo.FACTURA,"F1",true),MovimientoCaja.Fuente.CAJA,total,items,pagos);
    }
    @Test void costoDesconocidoSeMantieneDistintoDeCeroYSeExigePagoCompleto() {
        var item=new DetalleCompra("","","Servicio","unidad",n("1"),null);
        assertNull(compra(n("20"),List.of(item),Map.of(MedioPago.EFECTIVO,n("20"))).items().getFirst().costoUnitario());
        assertThrows(ValidacionException.class,()->compra(n("20"),List.of(item),Map.of(MedioPago.EFECTIVO,n("10"))));
        assertThrows(ValidacionException.class,()->compra(n("20"),List.of(item),Map.of(MedioPago.EFECTIVO,n("20.001"))));
    }
    @Test void costosConocidosCoincidenConElDocumentoYUsanRedondeoExacto() {
        var item=new DetalleCompra("","","Material","metro",n("1.2500"),n("19.90"));
        assertEquals(n("24.88"),item.totalConocido());
        assertDoesNotThrow(()->compra(n("24.88"),List.of(item),Map.of(MedioPago.EFECTIVO,n("24.88"))));
        assertThrows(ValidacionException.class,()->compra(n("25"),List.of(item),Map.of(MedioPago.EFECTIVO,n("25"))));
    }
    @Test void rechazaCantidadesCostosYDocumentosInvalidos() {
        assertThrows(ValidacionException.class,()->new DetalleCompra("","","X","unidad",n("0"),null));
        assertThrows(ValidacionException.class,()->new DetalleCompra("","","X","unidad",n("1.0001"),null));
        assertThrows(ValidacionException.class,()->new DetalleCompra("A","","X","unidad",n("1"),null));
        assertThrows(ValidacionException.class,()->new DetalleCompra("","","X","unidad",n("1"),n("-1")));
        assertThrows(ValidacionException.class,()->compra(n("1"),List.of(),Map.of(MedioPago.EFECTIVO,n("1"))));
    }
}
