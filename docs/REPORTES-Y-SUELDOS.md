# Reportes diarios y mensuales y pago de sueldos

## Uso del reporte

En Reportes selecciona un día o mes, pulsa Guardar reporte Excel y elige un archivo nuevo `.xls`. Los archivos existentes no se sobrescriben. El documento incluye información sensible del negocio; guárdalo en una carpeta protegida. La exportación no altera operaciones ni stock y consulta la base en una instantánea coherente.

Las hojas contienen información, resumen por tipo, fuente y medio, cuadres, operaciones, cobros, egresos, movimientos y saldos de Stock, y anulaciones o cambios. El fondo inicial aparece en Cuadres y no se suma como venta o ingreso. El flujo neto es cobros y otros ingresos menos egresos, separado por fuente y medio. Los pagos externos no se confunden con efectivo de caja.

Los cobros y egresos usan la fecha operativa del turno; los movimientos de stock y las operaciones usan la fecha real en Lima. Un turno abierto puede recibir más movimientos con su misma fecha operativa, por lo que un reporte generado mientras sigue abierto puede variar en otra exportación. La hoja Cuadres muestra un cierre únicamente si ocurrió antes del final del periodo. La hoja Saldos Stock refleja físico, reservado y disponible al final del periodo, no el saldo actual. Los movimientos se dividen en hojas adicionales al alcanzar el límite del formato `.xls`. Los SKU y números de documento se guardan como texto. Los importes con más de 15 cifras significativas se guardan como texto para no perder precisión en Excel.

La migración 13 incorpora historial de estado y monto abonado de las operaciones. Desde su aplicación se registra cada cambio y se utiliza para mostrar el abonado, saldo y estado al cierre del periodo. Para operaciones anteriores solo existe una instantánea desde la migración: si la fecha solicitada es anterior y no hay historial, se indica `NO DISPONIBLE` y se dejan vacíos los importes que no pueden reconstruirse. No se reemplazan por el estado actual.

## Sueldos

En Cierre de caja, abre Pagos al personal y Pago de sueldo en efectivo. Indica trabajador, importe y, si lo deseas, periodo o referencia. Al confirmar, se registra un gasto en efectivo del turno seleccionado, visible en el libro y en los reportes. El importe es manual: no calcula nómina, impuestos ni beneficios.

El sistema compara el importe con el **efectivo esperado** de ese turno al guardar. Si no alcanza, rechaza el pago y muestra el saldo esperado; no registra una parte automáticamente. Esta cifra es contable: comprueba también que los billetes estén físicamente en caja. Se exige un turno abierto y la solicitud es segura ante reintentos. El historial anterior de gastos de sueldo no se modifica.

## Revisión de la entrega

La instalación de la migración requiere permisos para crear y modificar objetos en el esquema de la aplicación. No se modifica una base de negocio automáticamente desde las pruebas. La aceptación en el Windows del trabajo y la definición de permisos y operaciones de producción requieren comprobación en ese equipo. Un respaldo debe realizarse y probarse antes de actualizar una base que ya contenga datos.
