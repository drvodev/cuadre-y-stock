package com.surquillo.inventariocaja.repositorio;

import com.surquillo.inventariocaja.excepcion.*;
import com.surquillo.inventariocaja.modelo.stock.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

/** Consultas parametrizadas, páginas limitadas y características preservadas por versión. */
public class ProductoPostgresRepository implements ProductoRepository {
    private static final String COLUMNAS="sku,descripcion,categoria,marca,presentacion,tipo,unidad,permite_fraccion,origen,contenido,unidad_contenido,precio_venta,costo,activo,version";
    private final DataSource dataSource;
    private final ListasCatalogoPostgres listas;
    public ProductoPostgresRepository(DataSource ds) { dataSource=Objects.requireNonNull(ds);listas=new ListasCatalogoPostgres(ds); }
    @Override public List<String> opciones(ListaCatalogo lista,int limite) { return listarOpciones(lista,"","",limite,false).stream().map(OpcionCatalogo::nombre).toList(); }
    @Override public OpcionCatalogo crearOpcion(OpcionCatalogo o) { return listas.crear(o); }
    @Override public OpcionCatalogo actualizarOpcion(OpcionCatalogo o) { return listas.actualizar(o); }
    @Override public Optional<OpcionCatalogo> buscarOpcion(UUID id) { return listas.buscar(id); }
    @Override public List<OpcionCatalogo> listarOpciones(ListaCatalogo lista,String texto,String despues,int limite,boolean inactivos) { return listas.listar(lista,texto,despues,limite,inactivos); }
    @Override public List<CambioOpcionCatalogo> historialOpcion(UUID id,long despues,int limite) { return listas.historial(id,despues,limite); }
    private static String seleccion(String alias) {
        String prefijo=alias.isEmpty()?"":alias+".";
        return Arrays.stream(COLUMNAS.split(",")).map(s->prefijo+s).collect(java.util.stream.Collectors.joining(","))
            +",ARRAY(SELECT ARRAY[e.key,e.value] FROM jsonb_each_text(COALESCE("+prefijo+"atributos,'{}'::jsonb)) e ORDER BY e.key)";
    }
    @Override public List<CambioCatalogo> historial(String sku,long despues,int limite) {
        String sql="SELECT "+seleccion("v")+",h.secuencia,h.fecha FROM catalogo_historial h CROSS JOIN LATERAL jsonb_populate_record(NULL::catalogo_variante,h.posterior) v WHERE h.sku=? AND h.secuencia>? ORDER BY h.secuencia LIMIT ?";
        try(var c=dataSource.getConnection();var p=c.prepareStatement(sql)) {
            p.setString(1,sku);p.setLong(2,despues);p.setInt(3,limite);
            try(var r=p.executeQuery()){var valores=new ArrayList<CambioCatalogo>();while(r.next())valores.add(new CambioCatalogo(r.getLong(17),r.getTimestamp(18).toInstant(),leer(r)));return List.copyOf(valores);}
        }catch(SQLException e){throw fallo(e);}
    }
    @Override public Variante crear(Variante v) {
        String sql="INSERT INTO catalogo_variante("+COLUMNAS+",atributos) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,jsonb_object(?))";
        try(var c=dataSource.getConnection();var p=c.prepareStatement(sql)) {
            p.setString(1,v.sku());campos(p,v,2);p.setLong(15,v.version());atributos(p,c,16,v.atributos());p.executeUpdate();return v;
        }catch(SQLException e){if("23505".equals(e.getSQLState()))throw new ValidacionException("El SKU ya existe");throw fallo(e);}
    }
    @Override public Variante actualizar(Variante v) {
        String sql="UPDATE catalogo_variante SET descripcion=?,categoria=?,marca=?,presentacion=?,tipo=?,unidad=?,permite_fraccion=?,origen=?,contenido=?,unidad_contenido=?,precio_venta=?,costo=?,activo=?,atributos=jsonb_object(?),version=version+1 WHERE sku=? AND version=? AND permite_fraccion=? AND unidad=?";
        try(var c=dataSource.getConnection();var p=c.prepareStatement(sql)) {
            campos(p,v,1);atributos(p,c,14,v.atributos());p.setString(15,v.sku());p.setLong(16,v.version());p.setBoolean(17,v.permiteFraccion());p.setString(18,v.unidad());
            if(p.executeUpdate()!=1)throw new ValidacionException("El artículo cambió, no existe o su unidad no puede modificarse automáticamente");
            return v.conVersion(v.version()+1);
        }catch(SQLException e){throw fallo(e);}
    }
    @Override public Optional<Variante> buscarSku(String sku) {
        try(var c=dataSource.getConnection();var p=c.prepareStatement("SELECT "+seleccion("")+" FROM catalogo_variante WHERE sku=?")) {
            p.setString(1,sku);try(var r=p.executeQuery()){return r.next()?Optional.of(leer(r)):Optional.empty();}
        }catch(SQLException e){throw fallo(e);}
    }
    @Override public List<Variante> buscar(String texto,String despues,int limite,boolean inactivos) {
        return buscar(FiltroCatalogo.texto(texto,inactivos),despues,limite);
    }
    @Override public List<Variante> buscar(FiltroCatalogo f,String despues,int limite) {
        var sql=new StringBuilder("SELECT "+seleccion("v")+" FROM catalogo_variante v WHERE v.sku>? AND (? OR v.activo=?) AND lower(concat_ws(' ',v.sku,v.descripcion,v.categoria,v.marca,v.presentacion,v.tipo,v.unidad,(SELECT string_agg(e.key||' '||e.value,' ' ORDER BY e.key) FROM jsonb_each_text(v.atributos) e))) LIKE lower(?) ESCAPE '!'");
        var valores=new ArrayList<String>();
        agregar(sql,valores,"categoria",f.categoria());agregar(sql,valores,"marca",f.marca());
        agregar(sql,valores,"unidad",f.unidad());agregar(sql,valores,"presentacion",f.presentacion());agregar(sql,valores,"tipo",f.tipo());
        if(f.origen()!=null){sql.append(" AND v.origen=?");valores.add(f.origen().name());}
        f.atributos().forEach((nombre,valor)->{sql.append(" AND lower(v.atributos->>?)=lower(?)");valores.add(nombre);valores.add(valor);});
        sql.append(" ORDER BY v.sku LIMIT ?");
        try(var c=dataSource.getConnection();var p=c.prepareStatement(sql.toString())) {
            p.setString(1,despues);p.setBoolean(2,f.estado()==FiltroCatalogo.Estado.TODOS);p.setBoolean(3,f.estado()==FiltroCatalogo.Estado.ACTIVOS);
            p.setString(4,ListasCatalogoPostgres.patron(f.texto()));int i=5;for(var valor:valores)p.setString(i++,valor);p.setInt(i,limite);
            try(var r=p.executeQuery()){var resultado=new ArrayList<Variante>();while(r.next())resultado.add(leer(r));return List.copyOf(resultado);}
        }catch(SQLException e){throw fallo(e);}
    }
    private void agregar(StringBuilder sql,List<String> valores,String campo,String valor) {
        if(!valor.isEmpty()){sql.append(" AND clave_lista(v.").append(campo).append(")=clave_lista(?)");valores.add(valor);}
    }
    private void atributos(PreparedStatement p,Connection c,int posicion,Map<String,String> datos)throws SQLException {
        var pares=datos.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->new String[]{e.getKey(),e.getValue()}).toArray(String[][]::new);
        p.setArray(posicion,c.createArrayOf("text",pares));
    }
    private void campos(PreparedStatement p,Variante v,int inicio)throws SQLException {
        int i=inicio;p.setString(i++,v.descripcion());p.setString(i++,v.categoria());p.setString(i++,v.marca());
        p.setString(i++,v.presentacion());p.setString(i++,v.tipo());p.setString(i++,v.unidad());p.setBoolean(i++,v.permiteFraccion());
        p.setString(i++,v.origen().name());p.setBigDecimal(i++,v.contenido());p.setString(i++,v.unidadContenido());
        p.setBigDecimal(i++,v.precioVenta());p.setBigDecimal(i++,v.costo());p.setBoolean(i,v.activo());
    }
    private Variante leer(ResultSet r)throws SQLException {
        var datos=new TreeMap<String,String>();var arreglo=r.getArray(16);
        if(arreglo!=null)try{for(var par:(Object[])arreglo.getArray()){var fila=(Object[])par;datos.put((String)fila[0],(String)fila[1]);}}finally{arreglo.free();}
        return new Variante(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),
                r.getBoolean(8),Variante.Origen.valueOf(r.getString(9)),r.getBigDecimal(10),r.getString(11),r.getBigDecimal(12),r.getBigDecimal(13),r.getBoolean(14),r.getLong(15),datos);
    }
    private PersistenciaException fallo(SQLException e) { return new PersistenciaException("No se pudo acceder al catálogo",e); }
}
