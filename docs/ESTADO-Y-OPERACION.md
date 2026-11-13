# Estado y operación

Actualización: 12 de noviembre de 2026. Referencia funcional: beta 3.1 y aclaraciones posteriores. Ver PLAN-ACTUAL.md para el orden vigente y CATALOGO.md para este bloque.

## Implementado

Stock conserva físico, reservado y disponible por SKU/almacén, entradas, ajustes justificados, mínimos y movimientos. Las reservas admiten confirmación de faltantes, ampliación, cancelación y despacho único.

La lista de Stock muestra disponibilidad y costo/precio manuales, distingue bajos, agotados y sin inicializar, pagina movimientos y consulta físico/reservado históricos. Ver STOCK-CONSULTAS.md.

VentaService ya coordina documentos, cobros, separaciones, abonos, entrega y anulación completa con Stock y Caja en una transacción. La autorización simulada se utiliza en pruebas aisladas de ReservaService; no describe la integración comercial completa. Ver VENTAS.md.

Caja conserva turnos, movimientos, conteo, cierres y conciliación digital en PostgreSQL.

La integración visual del otro chat añade un libro de cuatro columnas y ganancia manual por documento con auditoría, sin alterar pagos o cierres. Compras coordina recepción, documento y pago íntegro, incluso desde fuente externa, con rollback y reintentos. Ver COMPRAS.md.

Catálogo incorpora ahora listas independientes, edición con versiones, filtros combinados, características descriptivas e historiales. La pantalla se presenta por descripción y categoría. Ver CATALOGO.md.

## PostgreSQL y migraciones

ConexionPostgres lee APP_DB_URL, APP_DB_USER y APP_DB_PASSWORD fuera del repositorio. PreparacionBeta permite elegir un esquema de pruebas y aplica migraciones durante la conexión. No borra ni adopta tablas antiguas manualmente creadas. El uso de una cuenta limitada y la instalación de producción siguen pendientes.

MigracionesPostgres aplica versiones en una transacción y verifica las registradas. Admite exclusivamente variantes LF/CRLF del mismo SQL. No modificar archivos SQL aplicados ni borrar datos para resolver una incompatibilidad.

## Pruebas

mvn test omite la integración si STOCK_TEST_DB no está habilitado. Para una base exclusivamente desechable: STOCK_TEST_DB=true y STOCK_TEST_URL, STOCK_TEST_USER y STOCK_TEST_PASSWORD. Las credenciales se suministran por entorno, nunca en el repositorio.

Las pruebas crean y eliminan únicamente sus esquemas aleatorios prueba_. La cuenta del laboratorio requiere permiso para crearlos. La beta 0.1.2 fue entregada con 40 pruebas habituales y 14 integraciones omitidas en su revisión visual; antes hubo una ejecución completa de 54. La ampliación de Catálogo añade el contrato compartido y comprobaciones de actualización y concurrencia, cuyos resultados actuales se registran en README-PRUEBAS-SEGURIDAD.md.

## Pendientes

- Cambios y devoluciones parciales, conservando cierres anteriores.
- Reportes .xls diarios/mensuales y proceso de restauración por decidir. Copias solo manuales, en carpeta elegida por el usuario.
- Permisos de producción, actualización y recuperación comprobada en un equipo del negocio.
- Integración y revisión visual de los cambios actuales con el agente responsable del diseño.
- Medición de rendimiento antes de aumentar el volumen o los puestos de trabajo; precios por sede se reservan para expansión.

La matriz BETA-ACEPTACION.md exige comprobar el programa completo y conserva pendientes de aceptación aunque sus componentes tengan pruebas.
