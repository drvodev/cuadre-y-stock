# Inventario y caja — base inicial

Proyecto Maven para abrir en IntelliJ IDEA: **File > Open**, elegir esta carpeta o `pom.xml` y cargar como proyecto Maven. Configurar un JDK 21 o superior. Para comprobar la compilación y las pruebas, usar el ciclo Maven `test`; para ver la ventana provisional, ejecutar `javafx:run` desde Maven.

## Estado del proyecto

El objetivo es una aplicación Java de escritorio para controlar inventario y cuadre de caja en Surquillo, con PostgreSQL y una arquitectura preparada para futuras sedes. Los comprobantes son registros internos; la aplicación no emite documentos electrónicos. Se trabajan primero las reglas y la persistencia y se completarán las pantallas al final.

En una terminal con Maven instalado, ejecuta `mvn test`. En IntelliJ se puede ejecutar el mismo ciclo desde el panel Maven. La última ejecución comprobada de Maven terminó con 29 pruebas exitosas; no incluyó pruebas contra una base PostgreSQL real ni validación visual. Los repositorios en memoria son únicamente para pruebas y no conservan datos.

La aplicación está en desarrollo y no es una beta lista para datos reales. Algunas clases de Caja siguen vacías. Hay lógica de Stock, repositorios y esquemas SQL iniciales, pero falta configurar y verificar una conexión real, así como implementar migraciones y respaldo.

El paquete `modelo.stock` separa el inventario de `modelo.caja`; hay repositorios tanto en memoria como preparados para PostgreSQL. `excepcion` contiene excepciones básicas y `src/test` las pruebas. La ventana principal todavía solo confirma el arranque de JavaFX.

Stock: `SaldoStock` y `StockService` calculan movimientos con cantidades exactas; el descuento usa únicamente el disponible. `ReservaService` vincula cada reserva a una separación, informa cuánto hay disponible y cuánto falta, y registra la cantidad aceptada, ampliaciones, cancelaciones y despacho. Puede ampliarse después de un ingreso; exige tener la cantidad completa reservada antes de despachar. `StockController` consulta el catálogo y respeta la precisión del SKU. `StockView` prepara operaciones manuales, y `ConfirmacionReservaView` prepara el aviso de confirmación, pero todavía no están conectadas a la ventana principal ni probadas visualmente.

`InventarioService` registra stock inicial, entradas, ventas y ajustes con identificadores de solicitud; guarda el cambio real y mantiene el historial. `StockMemoriaRepository` sirve para pruebas y pierde los datos al cerrar. Se preparó `StockPostgresRepository` y el esquema `src/main/resources/db/stock-v1.sql`, con transacciones, consultas parametrizadas y deduplicación. **La implementación PostgreSQL aún no se ha probado contra un servidor real ni está conectada a las pantallas; no utilizar con datos reales.** La aplicación todavía no configura credenciales ni aplica el esquema automáticamente.

Pendiente: integrar el aviso en la pantalla de separación, conectar el autorizador de despacho a Caja y garantizar que la validación del pago y el despacho sean atómicos en una transacción compartida; conectar compras, definir mínimos por almacén y completar migraciones y pruebas PostgreSQL con concurrencia, recuperación y reintentos. El autorizador usado en pruebas es simulado; no existe uno de producción. Los cálculos de Stock no autorizan por sí solos una entrega ni confirman pagos. No usar el esquema con datos reales antes de probarlo y versionar su migración.

## Inicio del catálogo

`CatalogoService` permite crear, actualizar, consultar y buscar variantes con SKU, descripción, categoría, marca, presentación, unidad de venta, origen, precio y costo opcionales. El contenido de una presentación y su unidad son descriptivos: por ejemplo, una caja puede indicar 1,5 m², pero vender una caja descuenta una caja, sin conversiones automáticas. Los SKU permanecen únicos incluso al desactivar un artículo y las actualizaciones verifican su versión. La búsqueda usa páginas de hasta 100 resultados.

`ProductoMemoriaRepository` sirve solo para pruebas; `ProductoPostgresRepository` y `db/catalogo-v1.sql` preparan PostgreSQL. Aún faltan la interfaz, integración con el saldo de Stock, catálogos editables de categorías y marcas, listas de precios por sede, historial de cambios y migraciones seguras. No se han validado las consultas ni el rendimiento con una base PostgreSQL real; la paginación evita cargar toda la lista en la aplicación, pero la búsqueda por texto puede requerir índices adicionales al crecer. No cargar datos reales hasta completar estas pruebas y protecciones.

La regla acordada para una separación con stock insuficiente consiste en informar al vendedor de la cantidad disponible y la faltante y pedir confirmación para reservar solo lo disponible. La reposición permite ampliar la reserva; el despacho requiere la cantidad completa y la autorización de Caja. Esa autorización todavía no está integrada con el pago real.

## PostgreSQL y seguridad

PostgreSQL se eligió por sus transacciones y soporte para el acceso de varios equipos, previstos en la expansión a otras sedes. El controlador JDBC está declarado en Maven. Los archivos `db/catalogo-v1.sql` y `db/stock-v1.sql` son esquemas iniciales, no un sistema de migración de producción; requieren revisión y pruebas antes de aplicarse a una base con datos reales. La instalación local sigue pendiente de comprobación y no hay conexión configurada desde la aplicación.

No guardar contraseñas, copias de bases de datos ni datos reales dentro del repositorio. Las búsquedas SQL preparadas tienen pruebas con JDBC simulado; esas pruebas no sustituyen una prueba real de PostgreSQL ni una auditoría completa de seguridad. El detalle de las pruebas está en `README-PRUEBAS-SEGURIDAD.md`.

## Próximas etapas

1. Completar y verificar PostgreSQL con una base de prueba aislada; validar consultas, concurrencia, reintentos y recuperación.
2. Terminar Catálogo y sus pruebas, incluidos historial e integración de persistencia con Stock.
3. Desarrollar Caja y Compras y coordinar sus transacciones con Stock.
4. Completar migraciones y respaldos y probar la restauración antes del uso real.
5. Conectar y revisar las pantallas al final del desarrollo de la lógica.

Referencia funcional: especificación beta 3.1 del 5 de noviembre de 2026 y aclaración posterior sobre faltantes en separaciones.
