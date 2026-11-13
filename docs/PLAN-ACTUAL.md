# Orden de trabajo acordado

Actualización: 12 de noviembre de 2026.

1. Catálogo: listas independientes, atributos descriptivos, filtros combinados y controles funcionales; conservar pruebas y documentación.
2. Stock: listado general simple, precio/costo manual desde el artículo, disponibilidad, mínimos/agotados y movimientos paginados. La reconstrucción histórica se coordina con la decisión sobre reportes.
3. Compras: documento, recepción y pago coordinados, sin duplicar entrada ni egreso.
4. Cambios y devoluciones: al final, después de compras.

Reportes y recuperación se revisarán al cerrar el primer bloque de Catálogo para decidir sus procesos. No se asume todavía un destino, retención ni restauración automática de respaldos.

El otro chat trabaja el tema, navegación y visual de cuadre. Se coordinan contratos de datos y migraciones para integrar y probar juntos. La beta 0.1.2 publicada se mantiene como referencia; no sustituirla durante el desarrollo ni marcar pendientes como terminados por tener solamente clases vacías.

## Criterio de avance

Cada bloque debe tener lógica, persistencia PostgreSQL, pruebas relevantes y documentación actualizada. Las pantallas compiladas, la prueba visual y la aceptación del usuario se registran por separado. Se realizan commits por bloques y se conserva el alcance de las pruebas efectivamente ejecutadas.
