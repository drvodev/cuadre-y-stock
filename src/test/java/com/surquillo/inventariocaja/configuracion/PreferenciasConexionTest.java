package com.surquillo.inventariocaja.configuracion;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreferenciasConexionTest {
    private static final class Memoria extends AbstractPreferences {
        final Map<String,String> datos=new HashMap<>();
        Memoria(){super(null,"");}
        protected void putSpi(String k,String v){datos.put(k,v);} protected String getSpi(String k){return datos.get(k);}
        protected void removeSpi(String k){datos.remove(k);} protected void removeNodeSpi(){datos.clear();}
        protected String[] keysSpi(){return datos.keySet().toArray(String[]::new);} protected String[] childrenNamesSpi(){return new String[0];}
        protected AbstractPreferences childSpi(String nombre){throw new UnsupportedOperationException();}
        protected void syncSpi(){} protected void flushSpi(){}
    }
    @Test void recuerdaConexionSinGuardarContrasenas() throws Exception {
        var memoria=new Memoria();var preferencias=new PreferenciasConexion(memoria);
        var datos=new PreferenciasConexion.Datos("localhost","5433","tienda","operador","mi_esquema");
        preferencias.guardar(datos);assertEquals(datos,new PreferenciasConexion(memoria).cargar());
        assertEquals(java.util.Set.of("servidor","puerto","base","usuario","esquema"),memoria.datos.keySet());
    }
}
