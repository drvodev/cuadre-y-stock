package com.surquillo.inventariocaja.repositorio;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductoPostgresSqlTest {
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> tipo, java.lang.reflect.InvocationHandler h) {
        return (T) Proxy.newProxyInstance(tipo.getClassLoader(),new Class<?>[]{tipo},h);
    }
    @Test void busquedaEscapaComodinesYUsaParametros() {
        List<String> consultas=new ArrayList<>(); Map<Integer,Object> parametros=new HashMap<>();
        DataSource ds=proxy(DataSource.class,(a,b,c)->proxy(Connection.class,(d,e,f)-> {
            if(e.getName().equals("close")) return null;
            if(!e.getName().equals("prepareStatement")) throw new UnsupportedOperationException(e.getName());
            consultas.add((String)f[0]);
            return proxy(PreparedStatement.class,(g,h,i)-> {
                if(h.getName().startsWith("set")) { parametros.put((Integer)i[0],i[1]); return null; }
                if(h.getName().equals("close")) return null;
                if(h.getName().equals("executeQuery")) return proxy(ResultSet.class,(j,k,l)-> {
                    if(k.getName().equals("next")) return false;
                    if(k.getName().equals("close")) return null;
                    throw new UnsupportedOperationException(k.getName());
                });
                throw new UnsupportedOperationException(h.getName());
            });
        }));
        String carga="x%' OR 1=1 --_!";
        assertTrue(new ProductoPostgresRepository(ds).buscar(carga,"",10,false).isEmpty());
        assertFalse(consultas.get(0).contains(carga));
        assertTrue(consultas.get(0).contains("ESCAPE '!'"));
        assertEquals("%x!%' OR 1=1 --!_!!%",parametros.get(4));
        assertEquals(10,parametros.get(5));
    }
}
