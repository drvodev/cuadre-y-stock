# Caja: primera etapa de lógica

Actualizado el 10 de noviembre de 2026. Implementación inicial, con almacenamiento en memoria para pruebas. No usar para registrar dinero real.

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

El flujo digital por medio es un neto de movimientos, no el saldo de una cuenta bancaria. Todavía falta la conciliación separada de cobros, devoluciones y egresos digitales.

## Alcance de la API

`CajaService.cobrar` valida el importe que recibe del módulo comercial. Todavía no consulta una venta, su saldo, precio o reserva. La venta pagada y los abonos de separaciones deberán suministrarse mediante eventos vinculados a sus documentos y confirmarse junto con sus efectos de Stock en una transacción común.

`TurnoMemoriaRepository` comprueba atomicidad y concurrencia dentro de un proceso. Sus datos y los identificadores usados se pierden al cerrar la aplicación. Todavía no hay repositorio PostgreSQL para Caja ni recuperación después de reiniciar. El efectivo recibido y el vuelto no forman parte del libro de movimientos: este guarda únicamente importes aplicados; su incorporación al recibo comercial queda pendiente.

## Pruebas y continuación

Cinco pruebas específicas verifican el cuadre del plan, vuelto y fuentes externas, validación de importes, reintentos, rechazo de cierres obsoletos y apertura concurrente. La suite habitual ejecutó 37 pruebas exitosas y omitió las seis de integración PostgreSQL, al no habilitar esa base en esta ejecución.

Pendiente: persistencia y migración de Caja; documentos y numeración; ventas y abonos vinculados; conciliación digital; integración atómica con Stock y Compras; anulaciones y cambios aplicados al turno actual; reportes y respaldos. Las pantallas quedan para la última fase.
