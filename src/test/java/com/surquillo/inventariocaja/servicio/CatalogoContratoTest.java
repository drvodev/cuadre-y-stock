package com.surquillo.inventariocaja.servicio;

import com.surquillo.inventariocaja.modelo.stock.*;
import com.surquillo.inventariocaja.repositorio.*;
import com.surquillo.inventariocaja.excepcion.ValidacionException;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Mismo contrato contra memoria y PostgreSQL; los filtros se comprueban por resultados observables. */
abstract class CatalogoContratoTest {
    protected CatalogoService servicio;
    protected abstract ProductoRepository nuevoRepositorio() throws Exception;
    @BeforeEach void preparar() throws Exception { servicio=new CatalogoService(nuevoRepositorio()); }
    Variante articulo(String sku,String marca,boolean activo,Variante.Origen origen,Map<String,String> atributos) {
        return new Variante(sku,"Lavadero 50%_!","Baños",marca,"Unidad","Doble poza","unidad",false,
            origen,null,"",new BigDecimal("150"),null,activo,0,atributos);
    }
    @Test void listasSeCreanSinArticulosRenombranYConservanAuditoria() {
        var o=servicio.crearOpcion(ListaCatalogo.CATEGORIA,"  Cerámicos   y Porcelanatos ");
        assertEquals(List.of("Cerámicos y Porcelanatos"),servicio.opciones(ListaCatalogo.CATEGORIA,20));
        assertThrows(ValidacionException.class,()->servicio.crearOpcion(ListaCatalogo.CATEGORIA,"cerámicos y porcelanatos"));
        var renombrada=servicio.actualizarOpcion(new OpcionCatalogo(o.id(),o.lista(),"Acabados",true,o.version()));
        var inactiva=servicio.actualizarOpcion(new OpcionCatalogo(o.id(),o.lista(),renombrada.nombre(),false,renombrada.version()));
        assertTrue(servicio.opciones(o.lista(),20).isEmpty());
        assertEquals(inactiva,servicio.buscarOpcion(o.id()).orElseThrow());
        assertEquals(3,servicio.historialOpcion(o.id(),0,20).size());
        assertThrows(ValidacionException.class,()->servicio.crearOpcion(o.lista()," ACABADOS "));
        assertEquals(1,servicio.listarOpciones(o.lista(),"acab","",10,true).size());
    }
    @Test void edicionesObsoletasYTrasladoDeListaNoModificanLaOpcion() {
        var o=servicio.crearOpcion(ListaCatalogo.MARCA,"Marca A");
        var nueva=servicio.actualizarOpcion(new OpcionCatalogo(o.id(),o.lista(),"Marca B",true,0));
        assertThrows(ValidacionException.class,()->servicio.actualizarOpcion(o));
        assertThrows(ValidacionException.class,()->servicio.actualizarOpcion(new OpcionCatalogo(o.id(),ListaCatalogo.UNIDAD,"Marca B",true,1)));
        assertEquals(nueva,servicio.buscarOpcion(o.id()).orElseThrow());
        assertEquals(2,servicio.historialOpcion(o.id(),0,100).size());
    }
    @Test void renombrarListaNoReescribeArticuloNiRecreaOpcionAlEditarPrecio() {
        var v=servicio.crear(articulo("L-1","Marca Original",true,Variante.Origen.COMPRADO,Map.of()));
        var opcion=servicio.listarOpciones(ListaCatalogo.MARCA,"","",10,true).getFirst();
        servicio.actualizarOpcion(new OpcionCatalogo(opcion.id(),opcion.lista(),"Nombre nuevo",false,opcion.version()));
        servicio.actualizar(v);
        assertEquals("Marca Original",servicio.buscarSku("L-1").orElseThrow().marca());
        assertTrue(servicio.opciones(ListaCatalogo.MARCA,10).isEmpty());
        assertEquals(1,servicio.listarOpciones(ListaCatalogo.MARCA,"","",10,true).size());
        servicio.crear(articulo("L-2","marca original",true,Variante.Origen.COMPRADO,Map.of()));
        assertEquals(List.of("marca original"),servicio.opciones(ListaCatalogo.MARCA,10));
    }
    @Test void filtrosSeCombinanPorEstadoOrigenListasYCaracteristicas() {
        servicio.crear(articulo("A","Rinok",true,Variante.Origen.COMPRADO,Map.of("Color","Blanco","Ancho","60 cm")));
        servicio.crear(articulo("B","Rinok",true,Variante.Origen.COMPRADO,Map.of("color","Azul")));
        servicio.crear(articulo("C","Rinok",false,Variante.Origen.COMPRADO,Map.of("color","Blanco","ancho","60 cm")));
        servicio.crear(articulo("D","Otra",true,Variante.Origen.FABRICADO,Map.of("color","Blanco")));
        var filtro=new FiltroCatalogo("50%_!","baños"," RINOK ","unidad","unidad","doble poza",Variante.Origen.COMPRADO,
            FiltroCatalogo.Estado.ACTIVOS,Map.of(" COLOR ","blanco","ancho","60 CM"));
        assertEquals(List.of("A"),servicio.buscar(filtro,"",20).stream().map(Variante::sku).toList());
        assertEquals(List.of("C"),servicio.buscar(new FiltroCatalogo("","","","","","",null,FiltroCatalogo.Estado.INACTIVOS,Map.of()),"",20).stream().map(Variante::sku).toList());
        assertEquals(3,servicio.buscar("blanco","",20,true).size());
        assertTrue(servicio.buscar(new FiltroCatalogo("","","x' OR 1=1 --","","","",null,null,Map.of("color","' OR 1=1 --")),"",20).isEmpty());
        assertEquals(4,servicio.buscar("","",20,true).size());
    }
    @Test void caracteristicasSonInmutablesSeRecuperanYConservanVersionHistorica() {
        var entrada=new HashMap<String,String>();entrada.put(" COLOR ","Blanco");entrada.put("detalle","Dos pozas: 50%_! ' \" \\ y ñ");
        var original=servicio.crear(articulo("H","Marca",true,Variante.Origen.COMPRADO,entrada));
        entrada.put(" COLOR ","Azul");
        assertEquals("Blanco",original.atributos().get("color"));
        assertThrows(UnsupportedOperationException.class,()->original.atributos().put("x","y"));
        var editado=new Variante(original.sku(),original.descripcion(),original.categoria(),original.marca(),original.presentacion(),original.tipo(),
            original.unidad(),original.permiteFraccion(),original.origen(),original.contenido(),original.unidadContenido(),original.precioVenta(),original.costo(),
            original.activo(),original.version(),Map.of("color","Azul"));
        servicio.actualizar(editado);
        assertEquals(Map.of("color","Azul"),servicio.buscarSku("H").orElseThrow().atributos());
        var historia=servicio.historial("H",0,10);
        assertEquals(2,historia.size());assertEquals(original.atributos(),historia.getFirst().variante().atributos());
        assertEquals("Azul",historia.getLast().variante().atributos().get("color"));
    }
    @Test void validaAtributosYLimitesSinEscriturasParciales() {
        assertThrows(ValidacionException.class,()->AtributosCatalogo.normalizar(Map.of("Color","Azul"," color ","Blanco")));
        assertThrows(ValidacionException.class,()->AtributosCatalogo.normalizar(Map.of("","dato")));
        assertThrows(ValidacionException.class,()->AtributosCatalogo.normalizar(Map.of("color"," ")));
        assertThrows(ValidacionException.class,()->AtributosCatalogo.normalizar(Map.of("color","a".repeat(501))));
        assertThrows(ValidacionException.class,()->servicio.crearOpcion(ListaCatalogo.MARCA," "));
        assertThrows(ValidacionException.class,()->servicio.listarOpciones(ListaCatalogo.UNIDAD,"","",101,true));
        assertThrows(ValidacionException.class,()->servicio.buscar(FiltroCatalogo.texto("",true),"",0));
        assertTrue(servicio.buscar("","",10,true).isEmpty());
    }
    @Test void paginaListasYArticulosFiltradosSinDuplicados() {
        for(int i=0;i<240;i++) {
            servicio.crearOpcion(ListaCatalogo.PRESENTACION,String.format(Locale.ROOT,"Presentación %04d",i));
            servicio.crear(articulo(String.format(Locale.ROOT,"SKU-%04d",i),i%2==0?"Elegida":"Otra",true,Variante.Origen.COMPRADO,Map.of("color","Blanco")));
        }
        String cursor="";var ids=new HashSet<UUID>();
        while(true) {
            var pagina=servicio.listarOpciones(ListaCatalogo.PRESENTACION,"Presentación",cursor,37,true);
            if(pagina.isEmpty())break;for(var opcion:pagina)assertTrue(ids.add(opcion.id()));cursor=pagina.getLast().clave();
        }
        assertEquals(240,ids.size());
        var filtro=new FiltroCatalogo("","baños","Elegida","","","",null,null,Map.of("color","Blanco"));
        cursor="";var skus=new HashSet<String>();
        while(true) {
            var pagina=servicio.buscar(filtro,cursor,29);if(pagina.isEmpty())break;
            for(var v:pagina)assertTrue(skus.add(v.sku()));cursor=pagina.getLast().sku();
        }
        assertEquals(120,skus.size());
    }
    @Test void listasEscapanComodinesYSeparanTipos() {
        servicio.crearOpcion(ListaCatalogo.MARCA,"50%_! ' ñ");
        servicio.crearOpcion(ListaCatalogo.CATEGORIA,"50%_! ' ñ");
        assertEquals(1,servicio.listarOpciones(ListaCatalogo.MARCA,"50%_!","",20,true).size());
        assertTrue(servicio.listarOpciones(ListaCatalogo.MARCA,"' OR 1=1 --","",20,true).isEmpty());
        assertEquals(1,servicio.listarOpciones(ListaCatalogo.CATEGORIA,"","",20,true).size());
    }
}
