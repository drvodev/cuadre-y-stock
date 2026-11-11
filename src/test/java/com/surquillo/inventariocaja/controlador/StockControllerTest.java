package com.surquillo.inventariocaja.controlador;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.*;
import com.surquillo.inventariocaja.repositorio.*;
import com.surquillo.inventariocaja.servicio.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StockControllerTest {
    final CatalogoService catalogo=new CatalogoService(new ProductoMemoriaRepository());
    final StockMemoriaRepository repo=new StockMemoriaRepository();
    final InventarioService inventario=new InventarioService(repo,Clock.systemUTC());
    final StockController stock=new StockController(catalogo,inventario);
    BigDecimal n(String s) { return new BigDecimal(s); }
    Variante item(String sku,boolean fraccion,boolean activo) {
        return new Variante(sku,"Artículo","","","","","unidad",fraccion,Variante.Origen.COMPRADO,null,"",null,null,activo,0);
    }
    @Test void usaSkuYPrecisionDelCatalogoParaIniciarStock() {
        catalogo.crear(item("bolsa",false,true)); catalogo.crear(item("metro",true,true));
        stock.iniciar(UUID.randomUUID()," bolsa ","Principal",n("1"));
        stock.iniciar(UUID.randomUUID(),"metro","Principal",n("1.250"));
        assertEquals(0,n("1.250").compareTo(stock.consultar("METRO","Principal").orElseThrow().fisico()));
        assertThrows(ValidacionException.class,()->stock.entrada(UUID.randomUUID(),"bolsa","Principal",MovimientoStock.Tipo.COMPRA,n("0.5"),""));
        assertThrows(ValidacionException.class,()->stock.iniciar(UUID.randomUUID(),"desconocido","Principal",n("1")));
    }
    @Test void articuloInactivoSePuedeConsultarPeroNoIngresar() {
        catalogo.crear(item("x",false,true)); stock.iniciar(UUID.randomUUID(),"x","A",n("1"));
        catalogo.actualizar(item("x",false,false));
        assertTrue(stock.consultar("x","A").isPresent());
        assertThrows(ValidacionException.class,()->stock.entrada(UUID.randomUUID(),"x","A",MovimientoStock.Tipo.COMPRA,n("1"),""));
        stock.ajustar(UUID.randomUUID(),"x","A",n("0"),"conteo");
        assertEquals(2,stock.historial("x","A").size());
    }
    @Test void confirmacionDePropuestaSePuedeReintentarSinDuplicar() {
        catalogo.crear(item("x",false,true)); stock.iniciar(UUID.randomUUID(),"x","A",n("2"));
        var servicio=new ReservaService(repo,Clock.systemUTC(),id->{ throw new ValidacionException("Caja aún no integrada"); });
        var controlador=new SeparacionController(servicio); var clave=new ClaveStock("x","A");
        var propuesta=controlador.proponer(clave,n("5"));
        var solicitud=UUID.randomUUID(); var reserva=UUID.randomUUID(); var separacion=UUID.randomUUID();
        var primera=controlador.confirmar(solicitud,reserva,separacion,clave,n("5"),propuesta.disponible());
        assertEquals(primera,controlador.confirmar(solicitud,reserva,separacion,clave,n("5"),propuesta.disponible()));
        assertEquals(2,stock.historial("x","A").size());
        assertThrows(ValidacionException.class,()->servicio.despachar(UUID.randomUUID(),reserva,clave));
    }
}
