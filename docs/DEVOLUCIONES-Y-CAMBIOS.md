# Devoluciones y cambios con nota

Actualización: 13 de noviembre de 2026.

## Regla acordada

El usuario confirmó este ejemplo real de proceso: material vendido a S/ 100 y reintegro acordado de S/ 90. La nota conserva valor original 100, importe reconocido 90 y diferencia no devuelta 10. Esa diferencia no se registra automáticamente como ganancia.

El importe reconocido se introduce manualmente, entre cero y el valor original de las cantidades seleccionadas. En una devolución es lo efectivamente reintegrado; en un cambio es el crédito aplicado a los reemplazos.

## Uso

1. Buscar la venta o separación entregada por tipo y número de documento.
2. Indicar cantidades devueltas en las líneas pendientes. Marcar reingreso solo para material que vuelve a estar vendible. Los ítems libres no generan movimientos automáticos de inventario.
3. Elegir devolución o cambio; escribir número de nota, motivo e importe reconocido.
4. Para cambio, añadir los reemplazos y su documento nuevo. Calcular importes muestra el valor original, lo no devuelto y la diferencia real.
5. Registrar únicamente los medios y montos que efectivamente entran o salen. Si hay un adicional en efectivo, se puede indicar recibido y se informa el vuelto. Sin diferencia se dejan los pagos vacíos.
6. Confirmar. Historial de notas permite consultar comprobante, responsable, fecha, cantidades, reingreso, importes y vínculo al reemplazo.

La nota es un comprobante interno consultable; esta pantalla no imprime ni emite un comprobante electrónico.

## Efectos contables y de Stock

| Caso | Efecto |
|---|---|
| Devolver material valorado en 100 y reintegrar 90 | Una devolución de dinero de 90 en el turno actual; 10 quedan como importe no devuelto. |
| Cambio: crédito 90, reemplazos 120 | Un cobro adicional de 30. |
| Cambio: crédito 90, reemplazos 60 | Un reintegro de 30. |
| Cambio: crédito 90, reemplazos 90 | Nota y movimientos de productos, sin inventar un movimiento de dinero. |

Los reemplazos forman una operación vinculada a la original, pagada con el crédito reconocido más o menos la diferencia real. No se simula un cobro íntegro del reemplazo ni una devolución de dinero que no ocurrió. Los registros de la nota explican la compensación.

Reingresar y devolver dinero son decisiones separadas. Un producto no vendible puede tener reintegro sin aumentar el inventario. Si se reingresa un SKU válido sin saldo previo, se inicializa y registra su retorno dentro de la misma transacción.

La operación original conserva descripción, precios, importes históricos y documentos. Mientras queden unidades pendientes, se marca devolución parcial o cambio parcial; cuando se devuelve la totalidad se marca anulada o cambiada según la nota final. La corrección no crea una deuda por el importe no reintegrado. La ganancia sigue siendo manual.

Las notas afectan el turno actual. Los movimientos y cierres de turnos anteriores no se recalculan ni sobrescriben. Incluso una nota con diferencia cero requiere un turno abierto.

## Integridad

- Número de nota único entre correcciones, y documento de reemplazo sujeto a la unicidad existente de ventas.
- Cantidades acumuladas devueltas nunca mayores que las originales; no se acepta dos veces la misma línea en una nota.
- Valor máximo calculado con el precio histórico, no con el precio vigente del catálogo.
- Redondeo acumulado por línea para que varias devoluciones fraccionadas no creen ni pierdan céntimos del valor original.
- Documento, cantidades, dinero, stock y estados se guardan juntos o se revierten juntos.
- Identificadores y huella del formulario permiten recuperar un reintento equivalente incluso tras cerrar el turno. Modificar el contenido de una solicitud ya confirmada se rechaza.
- La anulación completa anterior se bloquea si ya existen notas; las cantidades restantes se gestionan por esta pantalla para impedir reintegros duplicados.

En un error se conservan identificadores y turno de confirmación. Antes de pulsar Nueva nota o reiniciar tras una respuesta dudosa, se consulta el historial para comprobar si la nota ya quedó guardada.

## Componentes y acciones

| Componente | Propósito y funciones |
|---|---|
| DevolucionItem | Valida posición, cantidad positiva y precisión; identifica el reingreso solicitado. |
| PendienteDevolucion | Expone cantidad todavía retornable y los datos históricos del ítem. |
| CorreccionVenta | Representa la nota y su vínculo; distingue valor original, reconocido, no devuelto y diferencia. |
| CorreccionVentaService | Valida límites, calcula valor original, guarda notas y reemplazos, coordina dinero/Stock, protege reintentos y consulta historial. |
| CorreccionesView | Conecta búsqueda, selección, cálculo, confirmación e historial; conserva el formulario tras errores. |
| Operacion | Expone estados explícitos de devolución y cambio parcial. |
| VentaService | Evita anular íntegramente una operación que ya tiene notas. |
| LibroCajaRepository | Muestra en el libro de Caja solo los ítems efectivamente devueltos en una nota, o los reemplazos en un cambio con dinero. |
| PagosForm | Conserva los pagos existentes y permite mostrar efectivo recibido solo cuando corresponde al cobro adicional. |
| Migración 12 | Añade notas, cantidades y pagos con referencias a la operación, turno y solicitud. |

## Pruebas y revisión

Las pruebas PostgreSQL incluyen el ejemplo 100/90, reintento después del cierre, devoluciones parciales, cantidades duplicadas, material no vendible, cambios con diferencias positiva/negativa/cero, fallos SQL tardíos, documentos duplicados, concurrencia, redondeo acumulado, cambio parcial y separación entregada.

La presentación se revisa aparte con datos ficticios a 1200×790. La validación operativa completa del usuario y la configuración de producción siguen pendientes. El ejecutable público 0.1.2 no incluye esta ampliación.
