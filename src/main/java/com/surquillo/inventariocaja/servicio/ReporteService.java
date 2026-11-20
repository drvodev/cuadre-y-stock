package com.surquillo.inventariocaja.servicio;

import javax.sql.DataSource;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.Objects;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;

/** Exportación de datos de un periodo desde una instantánea coherente de PostgreSQL. */
public final class ReporteService {
    private static final ZoneId LIMA=ZoneId.of("America/Lima");
    private final DataSource ds;
    public ReporteService(DataSource ds) { this.ds=Objects.requireNonNull(ds); }

    public void exportar(LocalDate inicio,LocalDate fin,Path destino) throws Exception {
        Objects.requireNonNull(inicio); Objects.requireNonNull(fin); Objects.requireNonNull(destino);
        if(fin.isBefore(inicio) || inicio.getYear()<1900 || fin.getYear()>9998)
            throw new IllegalArgumentException("Revisa el periodo del reporte.");
        if(!destino.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".xls"))
            throw new IllegalArgumentException("El archivo debe terminar en .xls.");
        Path archivo=destino.toAbsolutePath(); Path carpeta=archivo.getParent();
        if(Files.exists(archivo)) throw new IllegalArgumentException("El archivo ya existe; elige otro nombre.");
        Path temporal=Files.createTempFile(carpeta,".reporte-",".tmp");
        try {
            try(Connection c=ds.getConnection(); HSSFWorkbook libro=new HSSFWorkbook()) {
                c.setReadOnly(true); c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); c.setAutoCommit(false);
                var notas=libro.createSheet("Información");
                texto(notas,0,"Periodo",inicio+" a "+fin);
                texto(notas,1,"Caja","Movimientos y cuadres por fecha operativa del turno. Un turno puede seguir abierto.");
                texto(notas,2,"Stock","Movimientos por fecha real en Lima; saldos al final del último día seleccionado.");
                texto(notas,3,"Operaciones","Estado, abonado y saldo al final del periodo desde el historial. Cuando no hay historial anterior, se muestran como no disponibles.");
                texto(notas,4,"Montos","La hoja Resumen separa fuente y medio; el fondo inicial no es ingreso. Valores con más de 15 cifras se muestran como texto para evitar pérdida de precisión en Excel.");
                texto(notas,5,"Privacidad","Este archivo contiene información del negocio. Guárdalo en una ubicación protegida.");
                notas.setColumnWidth(0,24*256);notas.setColumnWidth(1,100*256);
                consulta(c,libro,"Resumen", """
                    SELECT m.tipo AS "Tipo",m.fuente AS "Fuente",l.medio AS "Medio",sum(l.importe) AS "Importe"
                    FROM caja_movimiento m JOIN caja_turno t ON t.id=m.turno JOIN caja_linea l ON l.movimiento=m.id
                    WHERE t.fecha_operativa BETWEEN ? AND ? GROUP BY m.tipo,m.fuente,l.medio
                    UNION ALL
                    SELECT 'FLUJO NETO',m.fuente,l.medio,sum(CASE WHEN m.tipo IN ('COBRO','OTRO_INGRESO') THEN l.importe ELSE -l.importe END)
                    FROM caja_movimiento m JOIN caja_turno t ON t.id=m.turno JOIN caja_linea l ON l.movimiento=m.id
                    WHERE t.fecha_operativa BETWEEN ? AND ? GROUP BY m.fuente,l.medio ORDER BY 1,2,3
                    """,inicio,fin,inicio,fin);
                consulta(c,libro,"Cuadres", """
                    WITH corte AS (SELECT ?::timestamptz AS hasta)
                    SELECT fecha_operativa AS "Fecha operativa",caja AS "Caja",encargado AS "Encargado",fondo AS "Fondo inicial",
                    COALESCE(cerrado AND cierre_fecha<corte.hasta,false) AS "Cerrado al corte",
                    CASE WHEN cerrado AND cierre_fecha<corte.hasta THEN esperado END AS "Efectivo esperado",
                    CASE WHEN cerrado AND cierre_fecha<corte.hasta THEN contado END AS "Efectivo contado",
                    CASE WHEN cerrado AND cierre_fecha<corte.hasta THEN diferencia END AS "Diferencia",
                    CASE WHEN cerrado AND cierre_fecha<corte.hasta THEN observacion END AS "Observación",
                    CASE WHEN cerrado AND cierre_fecha<corte.hasta THEN cierre_fecha END AS "Fecha de cierre"
                    FROM caja_turno CROSS JOIN corte WHERE fecha_operativa BETWEEN ? AND ? ORDER BY fecha_operativa,caja,id
                    """,fin.plusDays(1).atStartOfDay(LIMA).toInstant(),inicio,fin);
                consulta(c,libro,"Operaciones", """
                    SELECT o.fecha AS "Registrada",o.modalidad AS "Modalidad",o.tipo AS "Documento",o.numero AS "Número",o.total AS "Total",
                    h.abonado AS "Abonado al corte",CASE WHEN h.estado IN ('ANULADA','CAMBIADA') THEN 0 ELSE o.total-h.abonado END AS "Saldo al corte",
                    COALESCE(h.estado,'NO DISPONIBLE') AS "Estado al corte"
                    FROM venta_operacion o LEFT JOIN LATERAL (
                      SELECT abonado,estado FROM venta_estado_historial h WHERE h.operacion=o.id AND h.fecha<? ORDER BY h.fecha DESC,h.secuencia DESC LIMIT 1
                    ) h ON true WHERE o.fecha>=? AND o.fecha<? ORDER BY o.fecha,o.id
                    """,fin.plusDays(1).atStartOfDay(LIMA).toInstant(),inicio.atStartOfDay(LIMA).toInstant(),fin.plusDays(1).atStartOfDay(LIMA).toInstant());
                consulta(c,libro,"Cobros", """
                    SELECT t.fecha_operativa AS "Fecha operativa",m.fecha AS "Fecha real",m.referencia AS "Referencia",o.tipo AS "Documento",o.numero AS "Número",l.medio AS "Medio",l.importe AS "Importe"
                    FROM caja_movimiento m JOIN caja_turno t ON t.id=m.turno JOIN caja_linea l ON l.movimiento=m.id
                    LEFT JOIN venta_solicitud s ON s.id=m.id LEFT JOIN venta_operacion o ON o.id=s.operacion
                    WHERE t.fecha_operativa BETWEEN ? AND ? AND m.tipo='COBRO' ORDER BY t.fecha_operativa,m.fecha,m.id,l.medio
                    """,inicio,fin);
                consulta(c,libro,"Egresos", """
                    SELECT t.fecha_operativa AS "Fecha operativa",m.fecha AS "Fecha real",m.tipo AS "Tipo",m.fuente AS "Fuente",m.referencia AS "Referencia",l.medio AS "Medio",l.importe AS "Importe"
                    FROM caja_movimiento m JOIN caja_turno t ON t.id=m.turno JOIN caja_linea l ON l.movimiento=m.id
                    WHERE t.fecha_operativa BETWEEN ? AND ? AND m.tipo IN ('COMPRA','GASTO','DEVOLUCION','RETIRO') ORDER BY t.fecha_operativa,m.fecha,m.id,l.medio
                    """,inicio,fin);
                consulta(c,libro,"Stock", """
                    SELECT fecha AS "Fecha",sku AS "SKU",almacen AS "Almacén",tipo AS "Tipo",cantidad_solicitada AS "Solicitado",cambio_fisico AS "Cambio físico",fisico_posterior AS "Físico posterior",reservado_posterior AS "Reservado posterior",referencia AS "Referencia",motivo AS "Motivo"
                    FROM stock_movimiento WHERE fecha>=? AND fecha<? ORDER BY fecha,secuencia
                    """,inicio.atStartOfDay(LIMA).toInstant(),fin.plusDays(1).atStartOfDay(LIMA).toInstant());
                consulta(c,libro,"Saldos Stock", """
                    SELECT sku AS "SKU",almacen AS "Almacén",fisico_posterior AS "Físico",reservado_posterior AS "Reservado",fisico_posterior-reservado_posterior AS "Disponible"
                    FROM (SELECT DISTINCT ON (sku,almacen) sku,almacen,fisico_posterior,reservado_posterior FROM stock_movimiento
                          WHERE fecha<? ORDER BY sku,almacen,secuencia DESC) s ORDER BY sku,almacen
                    """,fin.plusDays(1).atStartOfDay(LIMA).toInstant());
                consulta(c,libro,"Anulaciones y cambios", """
                    SELECT t.fecha_operativa AS "Fecha operativa",a.fecha AS "Fecha real",'ANULACION' AS "Tipo",o.tipo AS "Documento original",o.numero AS "Número original",a.motivo AS "Motivo",a.devuelto AS "Reconocido",NULL::numeric AS "Reemplazo",NULL::numeric AS "Diferencia",NULL::text AS "Nota",NULL::text AS "Documento reemplazo",NULL::text AS "Número reemplazo"
                    FROM venta_anulacion a JOIN caja_turno t ON t.id=a.turno JOIN venta_operacion o ON o.id=a.operacion WHERE t.fecha_operativa BETWEEN ? AND ?
                    UNION ALL
                    SELECT t.fecha_operativa,x.fecha,x.tipo,o.tipo,o.numero,x.motivo,x.reconocido,x.total_reemplazo,x.diferencia,x.comprobante,reemplazo.tipo,reemplazo.numero
                    FROM venta_correccion x JOIN caja_turno t ON t.id=x.turno JOIN venta_operacion o ON o.id=x.original LEFT JOIN venta_operacion reemplazo ON reemplazo.id=x.reemplazo WHERE t.fecha_operativa BETWEEN ? AND ? ORDER BY 1,2
                    """,inicio,fin,inicio,fin);
                c.commit();
                try(OutputStream out=Files.newOutputStream(temporal,StandardOpenOption.TRUNCATE_EXISTING)) { libro.write(out); }
            }
            Files.move(temporal,archivo);
        } finally { Files.deleteIfExists(temporal); }
    }
    private static void texto(Sheet hoja,int fila,String titulo,String valor) {
        var r=hoja.createRow(fila);r.createCell(0).setCellValue(titulo);r.createCell(1).setCellValue(valor);
    }
    private void consulta(Connection c,Workbook libro,String nombre,String sql,Object... parametros) throws SQLException {
        CellStyle formato=libro.createCellStyle();formato.setDataFormat(libro.createDataFormat().getFormat("0.00########"));
        try(PreparedStatement p=c.prepareStatement(sql)) {
            for(int i=0;i<parametros.length;i++) {
                Object v=parametros[i]; if(v instanceof LocalDate d)p.setDate(i+1,java.sql.Date.valueOf(d));
                else if(v instanceof Instant t)p.setTimestamp(i+1,Timestamp.from(t)); else p.setObject(i+1,v);
            }
            try(ResultSet r=p.executeQuery()) {
                var meta=r.getMetaData(); int columnas=meta.getColumnCount(),pagina=1,fila=0;
                Sheet hoja=libro.createSheet(nombre); encabezado(hoja,meta);
                while(r.next()) {
                    if(fila==65535) { hoja=libro.createSheet(nombre+" "+(++pagina)); encabezado(hoja,meta); fila=0; }
                    Row row=hoja.createRow(++fila);
                    for(int col=1;col<=columnas;col++) {
                        Object valor=r.getObject(col); if(valor==null)continue; Cell celda=row.createCell(col-1);
                        if(valor instanceof BigDecimal numero) {
                            if(numero.precision()<=15) { celda.setCellValue(numero.doubleValue()); celda.setCellStyle(formato); }
                            else celda.setCellValue(numero.toPlainString());
                        } else if(valor instanceof Timestamp fecha) celda.setCellValue(fecha.toInstant().atZone(LIMA).toLocalDateTime().toString().replace('T',' '));
                        else if(valor instanceof Boolean b)celda.setCellValue(b);
                        else celda.setCellValue(valor.toString());
                    }
                }
            }
        }
    }
    private static void encabezado(Sheet hoja,ResultSetMetaData meta) throws SQLException {
        Row fila=hoja.createRow(0); for(int i=1;i<=meta.getColumnCount();i++) { fila.createCell(i-1).setCellValue(meta.getColumnLabel(i)); hoja.setColumnWidth(i-1,22*256); } hoja.createFreezePane(0,1);
    }
}
