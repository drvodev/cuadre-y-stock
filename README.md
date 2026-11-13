# Cuadre y Stock

Aplicación Java para inventario y control interno de caja en Surquillo. Proyecto Maven para IntelliJ IDEA, con JavaFX y PostgreSQL. El plan contempla futuras sedes. Se priorizó una interfaz de pruebas para que el usuario pueda revisar las funciones existentes.

## Estado actual

Stock, Catálogo y Caja disponen de lógica, repositorios en memoria para pruebas y persistencia PostgreSQL comprobada. Caja conserva turnos, pagos, cierres y conciliación digital. VentaService coordina documentos, ventas, separaciones, abonos, entrega y anulación completa con Stock y Caja en una unidad de trabajo. Faltan otras funciones del plan y el programa aún no está terminado para uso con datos reales.

Stock incluye cantidades exactas, entradas, ajustes, descuentos sin negativos, reservas con confirmación de faltantes, ampliaciones, cancelación, despacho único, mínimos por almacén e historial. VentaService coordina pago y despacho; las pruebas aisladas de ReservaService también utilizan una autorización simulada.

Catálogo incluye artículos por SKU, unidades y presentaciones, costo opcional, búsqueda paginada, desactivación, control de versiones e historial. Ahora permite administrar categorías, marcas, unidades y presentaciones sin crear artículos, combinar filtros y guardar características descriptivas con historial. Una caja con metraje descriptivo descuenta una caja; no hay conversión automática a metros cuadrados. Renombrar una opción de lista no reasigna artículos ni modifica documentos históricos.

El desarrollo continúa después de la beta 0.1.2. Catálogo presenta una lista de Descripción por categoría y edición manual; Stock muestra cinco columnas, filtros por disponibilidad y movimientos paginados, y consulta el saldo al cierre de una fecha. Compras registra documento, recepción y pago completo en una transacción, con costos desconocidos y pagos externos. Devoluciones y cambios admite notas, cantidades parciales e importe reconocido manual; los cambios mueven únicamente la diferencia real. Las imágenes quedan para después. Ver [Catálogo](docs/CATALOGO.md), [Stock](docs/STOCK-CONSULTAS.md), [Compras](docs/COMPRAS.md), [Devoluciones y cambios](docs/DEVOLUCIONES-Y-CAMBIOS.md) y [orden vigente](docs/PLAN-ACTUAL.md). No se ha publicado un ejecutable nuevo con estas ampliaciones.

## Abrir y probar

Abrir la carpeta o el archivo pom.xml en IntelliJ y cargar como proyecto Maven. Configurar JDK 21 o superior. Ejecutar mvn test desde el panel Maven o desde una terminal con Maven instalado.

La última suite completa pasó 102 pruebas, 50 de integración real con PostgreSQL. Incluye Catálogo, Stock, Compras, devoluciones, cambios, Caja y respaldo/recuperación. Después se amplió y repitió la prueba de recuperación con venta, reserva, cierre y validación de migraciones. Las pruebas de integración se omiten si no se configura STOCK_TEST_DB=true. Para la prueba de copia, configurar también STOCK_TEST_PGBIN con la carpeta de herramientas PostgreSQL. Ver docs/ESTADO-Y-OPERACION.md antes de ejecutarlas: crean y eliminan esquemas y una base temporal desechables.

## Configuración y migraciones

ConexionPostgres lee APP_DB_URL, APP_DB_USER y APP_DB_PASSWORD desde el entorno. Las credenciales se configuran fuera del repositorio. MigracionesPostgres aplica versiones explícitamente y verifica las ya registradas; no migra bases antiguas manualmente creadas ni modifica la instalación principal automáticamente.

La aplicación dispone de conexión local y preparación de un esquema de pruebas, pestañas de Catálogo, Stock, Caja, Nueva operación y Operaciones. La carga DEMO es opcional y no sobrescribe existencias. El paquete Windows incluye Java. Se probó visualmente carga DEMO, consulta, apertura y venta con vuelto; la prueba manual completa de todos los recorridos sigue pendiente. Ver GUIA-PRUEBAS.md antes de usarlo.

Para construir un paquete desde las fuentes: ejecutar primero las pruebas y después `tools/Construir-Beta.ps1`. El empaquetador requiere Maven y un JDK con jpackage, y omite la repetición de pruebas durante el empaquetado. El paquete generado no contiene bases de datos ni contraseñas.

La beta de pruebas 0.1.1 usa un tema oscuro de tonos suaves, también en los mensajes. En Catálogo se busca con Enter o con el botón Buscar; ambas acciones vuelven a la primera página. Se verificó esta búsqueda en el ejecutable Windows con los datos DEMO. La versión del paquete puede indicarse con `-Version` al construirlo.

La beta 0.1.2 añade una barra con Minimizar, Maximizar/Restaurar y Cerrar en conexión y pantalla principal. Conserva los controles nativos de Windows. Cerrar la ventana no cierra el turno de Caja. Pasaron 40 pruebas Maven y se omitieron 14 integraciones. El paquete se generó, pero Control de aplicaciones de Windows bloqueó su apertura en este equipo; los nuevos controles aún requieren validación visual. Ver la guía para el alcance de cada entrega.

## Documentación

- [Estado, decisiones, operación y pendientes](docs/ESTADO-Y-OPERACION.md).
- [Catálogo: listas, filtros, características y actualización](docs/CATALOGO.md).
- [Stock: consultas, mínimos, movimientos y corte histórico](docs/STOCK-CONSULTAS.md).
- [Compras: recepción, documento y pago coordinados](docs/COMPRAS.md).
- [Notas de devolución y cambios con importe manual](docs/DEVOLUCIONES-Y-CAMBIOS.md).
- [Copias manuales y recuperación en base vacía](docs/RESPALDOS.md).
- [Orden de trabajo acordado](docs/PLAN-ACTUAL.md).
- [Pruebas y alcance de la revisión de seguridad](README-PRUEBAS-SEGURIDAD.md).
- [Reglas, pruebas y pendientes de Caja](docs/CAJA.md).
- [Integración de ventas, separaciones y anulaciones completas](docs/VENTAS.md).
- [Responsabilidades y funciones por clase](docs/CLASES.md).
- [Condiciones de aceptación de la beta completa](docs/BETA-ACEPTACION.md).
- [Abrir la beta de pruebas y recorrido con resultados esperados](docs/GUIA-PRUEBAS.md).
- Referencia funcional: especificación beta 3.1 y aclaraciones posteriores del chat.

Se implementaron copias manuales y recuperación en una base distinta y vacía; ver RESPALDOS.md. Faltan reportes .xls, configuración de producción y la aceptación operativa de las pantallas actuales. El paquete 0.1.2 sigue disponible como referencia y no incluye estas mejoras.
