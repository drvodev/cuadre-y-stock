package com.surquillo.inventariocaja.servicio;

import com.surquillo.inventariocaja.configuracion.UnidadTrabajoPostgres;
import com.surquillo.inventariocaja.modelo.stock.*;
import com.surquillo.inventariocaja.repositorio.*;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

public class DatosDemostracionService {
    private final UnidadTrabajoPostgres unidad;
    private final Clock reloj;
    public DatosDemostracionService(DataSource ds,Clock reloj){unidad=new UnidadTrabajoPostgres(ds);this.reloj=reloj;}
    public void cargar() throws Exception {
        unidad.ejecutar(()->{
            var catalogo=new CatalogoService(new ProductoPostgresRepository(unidad));var stock=new InventarioService(new StockPostgresRepository(unidad),reloj);
            String[][] datos={{"DEMO-RIN-GI","Pegamento Rinok gris interiores","bolsa","25 kg","20","10"},{"DEMO-RIN-BE","Pegamento Rinok blanco extrafuerte","bolsa","25 kg","35","10"},{"DEMO-RIN-BF","Pegamento Rinok blanco flexible","bolsa","25 kg","45","10"},{"DEMO-LAV-100","Lavadero 100 cm","unidad","100 cm","500","4"},{"DEMO-CAJA","Caja de mayólica 45x45","caja","1,5 m²","50","5"}};
            for(var d:datos){
                if(catalogo.buscarSku(d[0]).isEmpty())catalogo.crear(new Variante(d[0],d[1],"Demostración",d[0].contains("RIN")?"Rinok":"",d[3],"",d[2],false,Variante.Origen.COMPRADO,null,"",new BigDecimal(d[4]),null,true,0));
                var clave=new ClaveStock(d[0],"Surquillo");if(stock.consultar(clave).isEmpty())stock.iniciar(UUID.randomUUID(),clave,false,new BigDecimal(d[5]),"Datos de demostración");
            }
            return null;
        });
    }
}
