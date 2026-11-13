# Compras recibidas y pagadas

Actualización: 12 de noviembre de 2026.

## Proceso

1. Abrir o seleccionar un turno de Caja.
2. Indicar proveedor, tipo y número del documento, fuente del dinero y total.
3. Añadir los productos recibidos, sus cantidades, unidad y costo unitario si se conoce. Consultar SKU completa descripción y unidad; no inventa un costo.
4. Completar los medios de pago por el total. La beta no registra compras a crédito.
5. Confirmar una sola vez: se guardan documento, recepción y pago en la misma transacción.

Si el producto no tiene control de inventario, se deja SKU vacío. La línea libre conserva descripción y costo sin crear existencias. Flete u otros conceptos del mismo documento pueden registrarse como líneas libres.

Los SKU controlados deben existir y estar activos; su unidad y precisión deben coincidir. Si falta el saldo inicial del almacén, se inicializa en cero y se registra la entrada por compra, dentro de esa misma transacción.

## Costos, dinero e historial

Costo desconocido permanece vacío, no se convierte en cero. Si todos los costos son conocidos, sus subtotales redondeados deben coincidir con el total; si hay costos desconocidos, los conocidos no pueden superar el total. El total del documento siempre se indica y se paga completo.

El costo real conocido queda en el detalle histórico de la compra. No sustituye automáticamente el costo o precio sugerido que el usuario mantiene manualmente en Catálogo/Stock.

CAJA afecta el medio de pago del turno; EXTERNA conserva el pago para consulta sin reducir los importes de la caja. Ambas requieren un turno abierto para registrar la operación. La fecha guardada es la fecha de confirmación, no una entrada retroactiva.

No se deben repetir luego el ingreso de stock o el egreso de dinero en sus formularios manuales: Compras ya guarda ambos efectos.

## Integridad y reintentos

La solicitud UUID conserva una huella de todos los datos que afectan la compra. Repetirla con los mismos datos recupera el resultado incluso tras cerrar el turno; cambiar sus datos se rechaza. Las cantidades e importes equivalentes con distinta cantidad de ceros se consideran iguales.

El documento es único por proveedor normalizado, tipo y número. El mismo número puede existir en proveedores distintos. El proveedor se introduce manualmente; debe identificarse consistentemente. No se ha añadido un maestro de proveedores ni consulta de RUC externa.

Un error en una línea, un documento duplicado o un fallo SQL tardío revierte también el egreso y cualquier entrada ya intentada. Las confirmaciones concurrentes de la misma solicitud conservan una sola compra.

La pantalla conserva los identificadores del formulario tras un error. Tras cerrar la aplicación se consulta Historial antes de crear otra compra; el documento único también impide registrar una segunda compra idéntica con nuevos identificadores.

## Clases, propósito y acciones

| Clase | Para qué y funciones |
|---|---|
| Compra | Representa la confirmación inmutable; valida proveedor, pago completo y concordancia de costos/total. |
| DetalleCompra | Valida cantidad y costo opcional y calcula subtotales exactos con HALF_UP. |
| CompraRepository | Contrato de guardar, recuperar solicitud, consultar y paginar resúmenes. |
| CompraPostgresRepository | Persiste cabecera, líneas y pagos; conserva huella y secuencia; recupera compras sin modificar confirmaciones. |
| CompraService | Coordina Caja, Catálogo e Inventario en UnidadTrabajoPostgres; recupera reintentos y revierte fallos. |
| CompraController | Entrada de las pantallas a registrar, consultar y listar compras. |
| CompraView | Recoge documento, recepción y pagos; conserva UUID en errores y permite consultar el historial. |
| SesionBeta | Compone CompraService con la misma base de la sesión. |
| Migración 11 | Añade compras, detalles y pagos, con documento único por proveedor y referencias al movimiento del turno. |

## Pruebas y alcance

Tres pruebas de modelo y ocho contra PostgreSQL verifican costo desconocido, redondeo, cantidades inválidas, recepción/pago, pago externo, documento duplicado, línea libre, inicialización, errores tardíos, concurrencia, pagos mixtos, paginación y recuperación de reintentos tras cerrar.

Las compras confirmadas no se editan ni anulan desde esta pantalla. Las devoluciones y cambios de ventas ya tienen su módulo separado; ver DEVOLUCIONES-Y-CAMBIOS.md. No se ocultan ni corrigen movimientos históricos. La aceptación operativa del usuario sigue pendiente.
