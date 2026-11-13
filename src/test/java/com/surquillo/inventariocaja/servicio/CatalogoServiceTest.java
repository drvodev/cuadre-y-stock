package com.surquillo.inventariocaja.servicio;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.Variante;
import com.surquillo.inventariocaja.repositorio.ProductoMemoriaRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CatalogoServiceTest {
    final CatalogoService servicio=new CatalogoService(new ProductoMemoriaRepository());
    BigDecimal n(String s) { return new BigDecimal(s); }
    Variante item(String sku,String nombre,String unidad,BigDecimal contenido,String unidadContenido,BigDecimal precio,BigDecimal costo,boolean activo,long version) {
        return new Variante(sku,nombre,"Materiales","Marca","Caja","Blanco",unidad,false,Variante.Origen.COMPRADO,contenido,unidadContenido,precio,costo,activo,version);
    }
    @Test void guardaUnidadesDiferentesSinConvertirContenidoEnCantidad() {
        var caja=servicio.crear(item(" caja-1 ","Mayólica","caja",n("1.5"),"m²",n("50.00"),null,true,0));
        servicio.crear(item("mueble","Mueble","unidad",null,"",null,n("0"),true,0));
        servicio.crear(item("inodoro","Inodoro","unidad",null,"",n("220"),null,true,0));
        servicio.crear(item("espejo","Espejo","unidad",null,"",n("40"),null,true,0));
        servicio.crear(item("bolsa","Pegamento","bolsa",n("25"),"kg",n("20"),null,true,0));
        assertEquals("CAJA-1",caja.sku()); assertEquals("caja",caja.unidad()); assertEquals(n("1.5"),caja.contenido());
        assertNull(servicio.buscarSku(" mueble ").orElseThrow().precioVenta());
        assertNull(servicio.buscarSku("caja-1").orElseThrow().costo());
        assertEquals(5,servicio.buscar("","",20,false).size());
    }
    @Test void validaPreciosYEvitaSkuDuplicadoInclusoInactivo() {
        servicio.crear(item("a","A","unidad",null,"",null,null,false,0));
        assertThrows(ValidacionException.class,()->servicio.crear(item(" A ","B","unidad",null,"",n("1"),null,true,0)));
        assertThrows(ValidacionException.class,()->item("b","B","unidad",null,"",n("0"),null,true,0));
        assertThrows(ValidacionException.class,()->item("b","B","unidad",n("2"),"",n("1"),null,true,0));
        assertThrows(ValidacionException.class,()->item("b","B","unidad",null,"",n("1"),n("-1"),true,0));
    }
    @Test void controlaCambiosSimultaneosYDesactivacion() {
        var original=servicio.crear(item("a","A","unidad",null,"",n("2"),null,true,0));
        var editado=servicio.actualizar(item("a","Nuevo","unidad",null,"",n("3"),null,false,original.version()));
        assertEquals(1,editado.version()); assertThrows(ValidacionException.class,()->servicio.actualizar(original));
        assertTrue(servicio.buscar("","",10,false).isEmpty()); assertEquals(1,servicio.buscar("nuevo","",10,true).size());
    }
    @Test void noCambiaAutomaticamenteUnidadDeStock() {
        servicio.crear(item("a","A","bolsa",null,"",n("2"),null,true,0));
        assertThrows(ValidacionException.class,()->servicio.actualizar(item("a","A","caja",null,"",n("2"),null,true,0)));
        assertEquals("bolsa",servicio.buscarSku("a").orElseThrow().unidad());
        assertEquals(1,servicio.historial("a",0,10).size());
        assertEquals(java.util.List.of("bolsa"),servicio.opciones(com.surquillo.inventariocaja.modelo.stock.ListaCatalogo.UNIDAD,10));
    }
    @Test void paginaUnCatalogoGrandeSinRepetirArticulos() {
        for (int i=0;i<2500;i++) servicio.crear(item(String.format("SKU-%04d",i),"Producto", "unidad",null,"",n("1"),null,true,0));
        String cursor=""; int total=0;
        while (true) { var pagina=servicio.buscar("producto",cursor,73,false); if(pagina.isEmpty()) break; total+=pagina.size(); cursor=pagina.get(pagina.size()-1).sku(); }
        assertEquals(2500,total); assertThrows(ValidacionException.class,()->servicio.buscar("","",101,false));
    }
}
