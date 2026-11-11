# Caja: primera etapa de lógica

Actualizado el 10 de noviembre de 2026. Reglas de Caja con repositorios en memoria y PostgreSQL. El flujo comercial y las pantallas aún no están completos para registrar dinero real.

## Reglas implementadas

- Apertura con fondo inicial, fecha operativa y nombre del encargado. La fecha operativa se conserva aunque los movimientos ocurran al día siguiente.
- Un turno abierto por caja; varias cajas pueden operar por separado. Los turnos anteriores permanecen consultables al abrir uno nuevo.
- Cobros combinados con efectivo, Visa, transferencia, depósito, Yape y Plin. Los importes aplicados deben sumar exactamente el importe a cobrar. El efectivo recibido puede ser mayor que el aplicado y genera vuelto; solo el aplicado se suma a la caja.
- Compras, gastos, devoluciones y retiros como egresos. Los pagos desde una fuente externa se registran pero no reducen el efectivo esperado ni el flujo de medios perteneciente a la caja.
- Importes exactos de hasta dos decimales, entradas inválidas rechazadas y referencias obligatorias.
- Reintentos con un mismo UUID y el mismo contenido devuelven el resultado previo. Reutilizar el UUID con otros datos se rechaza. La interfaz futura debe conservar ese UUID si necesita reintentar.
- Cierre manual con firma, contado y observación obligatoria cuando existe diferencia. El cierre verifica la versión del turno; si llegó otro movimiento después del conteo, debe revisarse nuevamente. No se permiten movimientos nuevos en un turno cerrado.

Efectivo esperado = fondo + ingresos en efectivo de caja − egresos en efectivo de caja.
Diferencia = contado − esperado.

El flujo digital por medio es un neto de movimientos, no el saldo de una cuenta bancaria. La conciliación distingue cobros brutos, devoluciones, egresos y otros ingresos. Si un medio tuvo actividad, debe verificarse antes de cerrar. Si hay diferencias, se exige una observación y se conservan esperado y verificado.

## Alcance de la API

`CajaService.cobrar` valida el importe que recibe del módulo comercial. Todavía no consulta una venta, su saldo, precio o reserva. La venta pagada y los abonos de separaciones deberán suministrarse mediante eventos vinculados a sus documentos y confirmarse junto con sus efectos de Stock en una transacción común.

`TurnoMemoriaRepository` comprueba atomicidad y concurrencia dentro de un proceso. Sus datos y los identificadores usados se pierden al cerrar la aplicación. `TurnoPostgresRepository` persiste turnos, movimientos, líneas, verificaciones y cierres. Las solicitudes guardan la versión del resultado original para recuperar reintentos después de movimientos o cierres posteriores. El efectivo recibido y el vuelto no forman parte del libro de movimientos: este guarda únicamente importes aplicados; su incorporación al recibo comercial queda pendiente.

`UnidadTrabajoPostgres` permite compartir una conexión entre repositorios: los commits internos se posponen hasta finalizar la operación externa y un rollback interno obliga a revertir toda la operación. Se comprobó un cobro y descuento de stock coordinados y su rollback conjunto. Todavía no existe el flujo comercial completo de documento, ítems y pago que debe utilizar esa unidad de trabajo.

## Pruebas y continuación

Seis pruebas específicas verifican el cuadre del plan, vuelto y fuentes externas, validación de importes, reintentos, cierres obsoletos, apertura concurrente y conciliación digital. La suite completa ejecutó 47 pruebas exitosas, incluidas nueve de integración PostgreSQL; no se omitieron pruebas en esa ejecución.

Pendiente: documentos y numeración; ventas y abonos vinculados; conectar los flujos comerciales con la unidad de trabajo; anulaciones y cambios aplicados al turno actual; reportes y respaldos. Las pantallas quedan para la última fase. Las migraciones 4 y 5 agregan persistencia y conciliación de Caja y no modifican las versiones anteriores.
