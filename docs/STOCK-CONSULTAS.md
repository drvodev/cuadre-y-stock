# Stock: consultas, disponibilidad e historial

Actualización: 12 de noviembre de 2026.

## Funciones

La lista principal muestra SKU, producto, unidad, disponibilidad y una columna intercambiable entre costo y precio sugerido. Son valores manuales del artículo; su edición comprueba la versión y conserva las características. Sin stock inicial no equivale a agotado ni a costo cero.

La consulta se pagina por SKU y almacén, hasta 100 filas visibles por carga y una fila adicional para detectar si hay más. Buscar y Enter reinician la consulta. Los filtros distinguen todos, bajos/agotados, agotados y sin inicializar. Bajo significa disponible menor o igual al mínimo configurado; agotado significa disponible cero con un saldo existente.

Movimientos y ajustes de stock conserva las entradas, el ajuste justificado y los mínimos existentes. Ahora carga movimientos en páginas de hasta 100 mediante una secuencia estable.

Consultar al cierre del día usa el inicio del día siguiente en America/Lima como límite exclusivo. Recupera físico y reservado del último movimiento registrado anterior a ese límite; disponible se calcula como físico menos reservado. Las reservas, ampliaciones, cancelaciones y despachos también tienen movimientos y saldo reservado posterior, por lo que no se reconstruye el pasado utilizando la reserva actual.

## Persistencia

StockPostgresRepository consulta saldos posteriores que ya existían en stock_movimiento. StockMemoriaRepository conserva las mismas instantáneas para las pruebas. Los reintentos no agregan movimientos ni sustituyen esas instantáneas.

La migración 10 añade un índice para consultas al corte. No inventa ni reescribe movimientos anteriores. El corte se basa en la fecha guardada en cada movimiento y el orden de registro; no es una función para insertar movimientos retroactivos. El mínimo que muestra la lista es el vigente, no un mínimo histórico.

## Responsabilidades

| Componente | Acciones |
|---|---|
| ListaStockRepository | Consulta artículos y disponibilidad por almacén, filtros de estado y precios opcionales con una sola consulta paginada. |
| ListaStockView | Presenta las cinco columnas, carga páginas, permite editar costo/precio y abre gestión de movimientos. |
| MovimientoPaginado | Asocia un movimiento con la secuencia usada como cursor. |
| StockRepository | Contrato de consultas actuales, históricas y paginadas, además de escrituras atómicas. |
| StockPostgresRepository | Consulta páginas y el último saldo posterior anterior al corte, sin modificar la información. |
| StockMemoriaRepository | Conserva y consulta instantáneas de operaciones efectivamente confirmadas. |
| InventarioService | Valida tamaños de página y cursores; expone la consulta al corte. |
| StockController | Comprueba el artículo y conecta las consultas con su SKU y almacén. |
| StockView | Consulta y modifica el stock; presenta movimientos por páginas y saldos históricos. |

## Alcance de prueba

Se ejecuta un recorrido con inicialización, reserva, compra, cancelación y reintento en memoria y PostgreSQL. Se contrastan los saldos a distintos cortes, el límite exclusivo, los cursores, la separación de claves y la ausencia de duplicados. Otra prueba comprueba mínimos, agotados y artículos sin saldo. La integración visual se revisa por separado con el agente de diseño.

La exportación de reportes y la recuperación no forman parte de esta ampliación. Las copias futuras serán manuales y el usuario elegirá la carpeta, según su decisión del 12 de noviembre.
