package com.surquillo.inventariocaja.servicio;
import com.surquillo.inventariocaja.configuracion.MigracionesPostgres;
import com.surquillo.inventariocaja.modelo.stock.*;
import com.surquillo.inventariocaja.modelo.caja.*;
import com.surquillo.inventariocaja.repositorio.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="STOCK_TEST_DB",matches="true")
class RespaldoPostgresTest {
    @Test void copiaYRecuperaEnBaseNuevaYRechazaDestinoConDatos()throws Exception{
        Assumptions.assumeTrue(System.getenv("STOCK_TEST_PGBIN")!=null&&!System.getenv("STOCK_TEST_PGBIN").isBlank(),"Configura STOCK_TEST_PGBIN para probar respaldo");
        String clave=System.getenv("STOCK_TEST_PASSWORD"),usuario=System.getenv("STOCK_TEST_USER");
        var direccion=java.net.URI.create(System.getenv("STOCK_TEST_URL").substring(5));
        String host=direccion.getHost(),puerto=Integer.toString(direccion.getPort()<0?5432:direccion.getPort()),base=direccion.getPath().substring(1);
        Assumptions.assumeTrue(host!=null&&(host.equalsIgnoreCase("localhost")||host.equals("127.0.0.1")),"La prueba requiere PostgreSQL local desechable");
        String esquema="prueba_"+UUID.randomUUID().toString().replace("-","");
        String destino="respaldo_"+UUID.randomUUID().toString().replace("-","");
        var ds=new PGSimpleDataSource();ds.setURL(System.getenv("STOCK_TEST_URL"));ds.setUser(usuario);ds.setPassword(clave);
        var carpeta=Files.createTempDirectory("prueba-respaldo-");
        try{
            try(var c=ds.getConnection();var s=c.createStatement()){s.execute("CREATE SCHEMA "+esquema);s.execute("CREATE DATABASE "+destino);}
            ds.setCurrentSchema(esquema);MigracionesPostgres.aplicar(ds);
            var cat=new CatalogoService(new ProductoPostgresRepository(ds));
            cat.crear(new Variante("DEMO","Artículo ficticio","Baños","","","","unidad",false,Variante.Origen.COMPRADO,null,"",new BigDecimal("20"),null,true,0));
            var inv=new InventarioService(new StockPostgresRepository(ds),Clock.systemUTC());
            inv.iniciar(UUID.randomUUID(),new ClaveStock("DEMO","Surquillo"),false,new BigDecimal("5"),"");
            var reserva=UUID.randomUUID();
            new ReservaService(new StockPostgresRepository(ds),Clock.systemUTC(),id->{}).crear(UUID.randomUUID(),reserva,UUID.randomUUID(),
                new ClaveStock("DEMO","Surquillo"),new BigDecimal("2"),new BigDecimal("2"));
            var caja=new CajaService(new TurnoPostgresRepository(ds),Clock.systemUTC());var turno=UUID.randomUUID();
            caja.abrir(UUID.randomUUID(),turno,"Surquillo",LocalDate.now(),"Prueba",new BigDecimal("100"));
            var ventas=new VentaService(ds,Clock.systemUTC());
            ventas.registrar(UUID.randomUUID(),UUID.randomUUID(),turno,Operacion.Modalidad.VENTA,new Documento(Documento.Tipo.BOLETA,"B-PRUEBA",true),"","",
                List.of(new ItemOperacion("DEMO","Surquillo","Artículo ficticio","unidad",new BigDecimal("1"),new BigDecimal("20"),"")),
                Map.of(MedioPago.EFECTIVO,new BigDecimal("20")),new BigDecimal("20"),Map.of());
            caja.cerrar(UUID.randomUUID(),turno,caja.consultar(turno).turno().version(),new BigDecimal("120"),"Prueba","");
            var servicio=new RespaldoService(host,puerto,base,usuario,clave,esquema,Path.of(System.getenv("STOCK_TEST_PGBIN")));
            assertThrows(IllegalArgumentException.class,()->new RespaldoService("servidor-remoto","5432","postgres",usuario,clave,esquema,Path.of(System.getenv("STOCK_TEST_PGBIN"))));
            var archivo=servicio.crear(carpeta);assertTrue(Files.size(archivo)>0);
            var otra=servicio.crear(carpeta);assertNotEquals(archivo,otra);
            assertThrows(IllegalArgumentException.class,()->servicio.restaurar(archivo,base));
            assertThrows(IllegalArgumentException.class,()->servicio.restaurar(archivo,"template1"));
            servicio.restaurar(archivo,destino);
            var copia=new PGSimpleDataSource();copia.setURL("jdbc:postgresql://"+host+":"+puerto+"/"+destino);copia.setUser(usuario);copia.setPassword(clave);copia.setCurrentSchema(esquema);
            MigracionesPostgres.aplicar(copia);
            assertEquals("Artículo ficticio",new CatalogoService(new ProductoPostgresRepository(copia)).buscarSku("DEMO").orElseThrow().descripcion());
            var saldo=new StockPostgresRepository(copia).buscar(new ClaveStock("DEMO","Surquillo")).orElseThrow();
            assertEquals(0,new BigDecimal("4").compareTo(saldo.fisico()));assertEquals(0,new BigDecimal("2").compareTo(saldo.reservado()));
            assertTrue(new StockPostgresRepository(copia).buscarReserva(reserva).isPresent());
            assertEquals("B-PRUEBA",new VentaService(copia,Clock.systemUTC()).buscarDocumento(Documento.Tipo.BOLETA,"B-PRUEBA").documento().numero());
            var cajaCopia=new CajaService(new TurnoPostgresRepository(copia),Clock.systemUTC());
            assertEquals(0,new BigDecimal("120").compareTo(cajaCopia.efectivoEsperado(turno)));assertTrue(cajaCopia.consultar(turno).turno().cerrado());
            assertThrows(IllegalArgumentException.class,()->servicio.restaurar(archivo,destino));
            var corrupto=carpeta.resolve("corrupto.backup");Files.writeString(corrupto,"datos inválidos");
            assertThrows(java.io.IOException.class,()->servicio.restaurar(corrupto,destino));
            assertTrue(cat.buscarSku("DEMO").isPresent());
        }finally{
            ds.setCurrentSchema(null);
            if(!esquema.matches("prueba_[a-f0-9]{32}")||!destino.matches("respaldo_[a-f0-9]{32}"))throw new IllegalStateException("Destino temporal inválido");
            try(var c=ds.getConnection();var s=c.createStatement()){s.execute("DROP SCHEMA IF EXISTS "+esquema+" CASCADE");s.execute("DROP DATABASE IF EXISTS "+destino);}
            try(var f=Files.list(carpeta)){for(var p:f.toList())Files.deleteIfExists(p);}Files.deleteIfExists(carpeta);
        }
    }
}
