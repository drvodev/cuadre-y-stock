# Ventas y separaciones: integración actual

## Responsabilidades y propósito

`VentaService` utiliza `UnidadTrabajoPostgres` para confirmar una operación comercial junto con su documento, ítems históricos, cobro y efectos de stock. Los repositorios reciben la misma unidad de trabajo. Una excepción en cualquier parte revierte también las escrituras anteriores de la operación.

- `registrar`: valida pago completo de la venta o abono positivo de la separación; guarda el número manual o sugerido, cliente y datos históricos de los ítems; acepta líneas libres y permite vender con stock cero. Para una reserva insuficiente exige la cantidad aceptada por ítem, validada nuevamente al confirmar.
- `abonar`: registra el abono en un turno abierto y rechaza importes superiores al saldo. Cada abono usa un UUID y el reintento con el mismo contenido no vuelve a cobrar.
- `entregar`: exige una separación pagada pendiente de entrega; completa reservas si llegó la reposición y descuenta físico y reservado una sola vez. Puede registrar un documento final sin otro cobro.
- `anular`: implementa anulación completa con devolución del total abonado en el turno actual; libera reservas o reingresa los ítems entregados que el operador indicó como vendibles. Conserva la operación original, motivo y registro de anulación. No implementa aún anulación parcial ni cambio de producto.
- `consultar` y `buscarDocumento`: recuperan la operación y sus ítems originales. Una operación anulada tiene saldo vigente cero sin borrar el importe histórico abonado.
- `sugerirNumero`: consulta el último número del tipo y prefijo y delega el incremento a `DocumentoService`. Se permiten saltos manuales; un número ya utilizado se rechaza y se revierte el cobro intentado.

`ItemOperacion` valida la cantidad y precio y redondea cada total a dos decimales HALF_UP. `Documento` conserva tipo, número y origen manual. `Operacion` conserva modalidad, saldo y estado. Las migraciones 6 y 7 agregan sus tablas, solicitudes y anulaciones; no modifican versiones anteriores.

## Pruebas y límites

Las pruebas PostgreSQL cubren venta completa con línea libre y stock cero, rechazo de venta incompleta, reintentos, documentos duplicados, correlativos, confirmación de faltante, abonos, reposición, entrega única y anulación en un turno posterior sin alterar el cierre anterior.

Pendiente: cambio de producto con liquidación de diferencia, devoluciones parciales, gastos documentados, consulta paginada comercial y reportes al corte histórico. Las compras documentadas se implementan en CompraService; ver COMPRAS.md. Las pantallas y el paquete de pruebas ya existen; la configuración de producción y la aceptación del programa completo siguen pendientes.
