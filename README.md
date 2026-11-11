# Cuadre y Stock

Aplicación Java para inventario y control interno de caja en Surquillo. Proyecto Maven para IntelliJ IDEA, con JavaFX y PostgreSQL. El plan contempla futuras sedes; las pantallas se completarán al final de la lógica.

## Estado actual

Stock, Catálogo y Caja disponen de lógica, repositorios en memoria para pruebas y persistencia PostgreSQL comprobada. Caja conserva turnos, pagos, cierres y conciliación digital. Una unidad de trabajo permite coordinar repositorios de Stock y Caja; todavía falta el flujo comercial completo que la utiliza. Los módulos aún no están terminados para uso con datos reales.

Stock incluye cantidades exactas, entradas, ajustes, descuentos sin negativos, reservas con confirmación de faltantes, ampliaciones, cancelación, despacho único, mínimos por almacén e historial. La autorización de pago de Caja todavía se simula en las pruebas.

Catálogo incluye artículos por SKU, unidades y presentaciones, costo opcional, búsqueda paginada, desactivación, control de versiones e historial. Las listas de marcas, categorías, unidades y presentaciones consultan los valores existentes en los artículos; su administración independiente está pendiente. Una caja con metraje descriptivo descuenta una caja; no hay conversión automática a metros cuadrados.

## Abrir y probar

Abrir la carpeta o el archivo pom.xml en IntelliJ y cargar como proyecto Maven. Configurar JDK 21 o superior. Ejecutar mvn test desde el panel Maven o desde una terminal con Maven instalado.

La última suite completa habilitada con PostgreSQL de prueba pasó 47 pruebas, nueve de integración real. Las pruebas de integración se omiten si no se configura STOCK_TEST_DB=true. Consultar docs/ESTADO-Y-OPERACION.md antes de ejecutarlas: crean y eliminan esquemas en una base desechable.

## Configuración y migraciones

ConexionPostgres lee APP_DB_URL, APP_DB_USER y APP_DB_PASSWORD desde el entorno. Las credenciales se configuran fuera del repositorio. MigracionesPostgres aplica versiones explícitamente y verifica las ya registradas; no migra bases antiguas manualmente creadas ni modifica la instalación principal automáticamente.

La ventana principal sigue siendo provisional. StockView y ConfirmacionReservaView están preparadas como componentes independientes, sin validación visual ni conexión a una sesión real de la aplicación.

## Documentación

- [Estado, decisiones, operación y pendientes](docs/ESTADO-Y-OPERACION.md).
- [Pruebas y alcance de la revisión de seguridad](README-PRUEBAS-SEGURIDAD.md).
- [Reglas, pruebas y pendientes de Caja](docs/CAJA.md).
- [Responsabilidades y funciones por clase](docs/CLASES.md).
- [Condiciones de aceptación de la beta completa](docs/BETA-ACEPTACION.md).
- Referencia funcional: especificación beta 3.1 y aclaraciones posteriores del chat.

Falta la transacción compartida con Caja, la reconstrucción al corte histórico, respaldos y restauración, configuración de producción y partes del catálogo. No se considera terminado un requisito por tener únicamente una clase o una prueba parcial.
