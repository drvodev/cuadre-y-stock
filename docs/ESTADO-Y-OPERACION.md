# Estado de Stock y Catálogo

Documento actualizado el 10 de noviembre de 2026. Referencia funcional: beta 3.1 y decisiones posteriores del chat. Las pantallas quedan para la última fase.

Caja tiene apertura, cobros, egresos, cierre manual y conciliación digital con persistencia PostgreSQL. La unidad de trabajo compartida está probada con efectos coordinados de Stock y Caja; aún falta integrarla con documentos y ventas. Ver CAJA.md y BETA-ACEPTACION.md para el alcance completo.

## Implementado y comprobado

- Stock físico, reservado y disponible por SKU y almacén; cantidades enteras o de hasta tres decimales.
- Entradas, ajustes justificados, venta con límite cero e historial de cambios físicos.
- Reservas por separación: propuesta con faltante, aceptación explícita, ampliación tras reposición, cancelación y despacho único. La autorización de pago se conecta mediante una interfaz; en las pruebas es simulada.
- Reintentos identificados por UUID y transacciones de saldo, reserva y movimiento. La implementación PostgreSQL serializa las escrituras de Stock mediante un bloqueo del módulo; su rendimiento debe medirse antes de una expansión.
- Mínimo persistente por SKU/almacén y comprobación de stock bajo.
- Catálogo con SKU normalizado y único, descripción, categoría, marca, presentación, tipo, unidad, origen, contenido descriptivo, precio y costo opcionales.
- Actualización por versión, desactivación, búsqueda paginada, consulta de listas existentes e historial de versiones. Las listas consultadas proceden de los artículos: aún no son maestros editables independientes.
- Configuración PostgreSQL por entorno y migraciones transaccionales con comprobación del contenido aplicado.

## Configuración de PostgreSQL

`ConexionPostgres.desdeEntorno` lee `APP_DB_URL`, `APP_DB_USER` y `APP_DB_PASSWORD`. La contraseña debe configurarse fuera del repositorio. Usar una cuenta de aplicación con los permisos necesarios, distinta de la cuenta de administración.

El administrador ejecuta explícitamente `MigracionesPostgres.aplicar` sobre un esquema nuevo y una conexión con permiso para crear tablas, funciones y disparadores. La aplicación no ejecuta migraciones automáticamente. Las versiones aplicadas se registran en `app_migracion` y una modificación de su contenido provoca un error. Si ya se ejecutaron los archivos SQL manualmente, debe prepararse una migración de adopción específica; no borrar tablas ni intentar aplicar el esquema inicial sobre ellas.

## Pruebas

`mvn test` ejecuta las pruebas habituales y omite la integración PostgreSQL si no se habilita. Para integración, configurar `STOCK_TEST_DB=true`, `STOCK_TEST_URL`, `STOCK_TEST_USER` y `STOCK_TEST_PASSWORD`, exclusivamente para una base desechable. La suite crea esquemas aleatorios con prefijo `prueba_` y elimina solo esos esquemas al terminar. La cuenta de prueba requiere permiso de creación de esquemas.

La última ejecución completa terminó con 47 pruebas exitosas y ninguna omitida. Incluyó nueve pruebas contra PostgreSQL 17: persistencia, reservas, concurrencia, rollback real, búsqueda, versiones, mínimos, auditoría, migraciones y coordinación de Caja/Stock. La cifra incluye una prueba de 1.000 operaciones aleatorias reproducibles. No demuestra resistencia a cortes de energía ni rendimiento en grandes volúmenes.

## Pendientes que impiden declarar el producto terminado

1. Coordinar Caja, pago y despacho en una transacción compartida; completar compras y devoluciones vinculadas al documento original.
2. Implementar reconstrucción de existencias y reservas al corte histórico para reportes. El historial actual de movimientos físicos no representa por sí solo todos los cambios de reserva.
3. Completar catálogos maestros editables, atributos flexibles y filtros específicos; preparar precios por sede cuando se aborde la expansión.
4. Implementar y probar respaldo y restauración con una política definida de destino y retención.
5. Conectar la configuración a la aplicación, preparar instalación/migración de una base real y comprobar los permisos del usuario de aplicación.
6. Medir consultas grandes, paginar el historial de Stock y revisar las pantallas al final.

Estos pendientes conservan el alcance del plan original. Stock y Catálogo tienen una base lógica y persistencia comprobadas, pero aún no se declaran completos para operar con datos reales.
