# Cuadre y Stock

Aplicación Java para inventario y control interno de caja en Surquillo. Proyecto Maven para IntelliJ IDEA, con JavaFX y PostgreSQL. El plan contempla futuras sedes; las pantallas se completarán al final de la lógica.

## Estado actual

Stock, Catálogo y Caja disponen de lógica, repositorios en memoria para pruebas y persistencia PostgreSQL comprobada. Caja conserva turnos, pagos, cierres y conciliación digital. VentaService coordina documentos, ventas, separaciones, abonos, entrega y anulación completa con Stock y Caja en una unidad de trabajo. Faltan otras funciones del plan y el programa aún no está terminado para uso con datos reales.

Stock incluye cantidades exactas, entradas, ajustes, descuentos sin negativos, reservas con confirmación de faltantes, ampliaciones, cancelación, despacho único, mínimos por almacén e historial. La autorización de pago de Caja todavía se simula en las pruebas.

Catálogo incluye artículos por SKU, unidades y presentaciones, costo opcional, búsqueda paginada, desactivación, control de versiones e historial. Las listas de marcas, categorías, unidades y presentaciones consultan los valores existentes en los artículos; su administración independiente está pendiente. Una caja con metraje descriptivo descuenta una caja; no hay conversión automática a metros cuadrados.

## Abrir y probar

Abrir la carpeta o el archivo pom.xml en IntelliJ y cargar como proyecto Maven. Configurar JDK 21 o superior. Ejecutar mvn test desde el panel Maven o desde una terminal con Maven instalado.

La última suite completa habilitada con PostgreSQL de prueba pasó 53 pruebas, 13 de integración real. Las pruebas de integración se omiten si no se configura STOCK_TEST_DB=true. Consultar docs/ESTADO-Y-OPERACION.md antes de ejecutarlas: crean y eliminan esquemas en una base desechable.

## Configuración y migraciones

ConexionPostgres lee APP_DB_URL, APP_DB_USER y APP_DB_PASSWORD desde el entorno. Las credenciales se configuran fuera del repositorio. MigracionesPostgres aplica versiones explícitamente y verifica las ya registradas; no migra bases antiguas manualmente creadas ni modifica la instalación principal automáticamente.

La ventana principal sigue siendo provisional. StockView y ConfirmacionReservaView están preparadas como componentes independientes, sin validación visual ni conexión a una sesión real de la aplicación.

## Documentación

- [Estado, decisiones, operación y pendientes](docs/ESTADO-Y-OPERACION.md).
- [Pruebas y alcance de la revisión de seguridad](README-PRUEBAS-SEGURIDAD.md).
- [Reglas, pruebas y pendientes de Caja](docs/CAJA.md).
- [Integración de ventas, separaciones y anulaciones completas](docs/VENTAS.md).
- [Responsabilidades y funciones por clase](docs/CLASES.md).
- [Condiciones de aceptación de la beta completa](docs/BETA-ACEPTACION.md).
- Referencia funcional: especificación beta 3.1 y aclaraciones posteriores del chat.

Faltan compras documentadas, cambios de producto, reconstrucción al corte histórico, reportes .xls, respaldos y restauración, configuración de producción, partes del catálogo, pantallas y empaquetado. No se considera terminado un requisito por tener únicamente una clase o una prueba parcial.
