# Beta 0.1.6: preparación y alcance

Esta beta reúne las mejoras prioritarias del issue 1: SKU automático, selección y ajustes de stock, búsqueda de productos en ventas, preferencias de conexión sin contraseña, ingresos del turno por medio de pago y preparación opcional de PostgreSQL. Los comprobantes siguen siendo de uso interno. El detalle del proceso y el asistente está en [ISSUE-1-MEJORAS.md](ISSUE-1-MEJORAS.md).

Se corrigió el riesgo de seleccionar o ajustar un artículo con un almacén diferente al de la lista mostrada: si se modifica el campo, hay que volver a buscar. La identificación de la versión se actualizó a 0.1.6 en Maven, la ventana y el empaquetador. La selección de herramientas PostgreSQL prioriza por número de versión en lugar de orden alfabético.

El agente de pruebas reprodujo la duplicación de un artículo cuando se perdía la respuesta al confirmar un SKU automático. La migración 15 y el formulario conservan una solicitud para recuperar el mismo artículo al reintentar con los mismos datos durante la sesión. Si se cierra la aplicación ante un resultado incierto, consulta primero el catálogo antes de volver a crearlo.

La imagen opcional de catálogo sigue pendiente, como prioridad baja. Las pantallas nuevas, el instalador gráfico en un equipo limpio y la recuperación con una cuenta limitada requieren aceptación manual. Antes de actualizar una base existente, se debe crear una copia y probar su recuperación en una base diferente. La migración 14 conserva los códigos conocidos y no renumera artículos; las versiones anteriores no deben conectarse al esquema migrado.

El código se mantiene en la rama del PR 2; no se fusiona automáticamente con `main`. La beta es para probar con datos ficticios, no para producción.

La suite definitiva ejecutó 127 pruebas sin fallos ni omisiones con PostgreSQL 17 aislado. El agente de pruebas verificó independientemente cinco escenarios de SKU, incluido el reintento después de perder la respuesta, antes de que se añadiera el bloqueo de tabla en la migración; la suite definitiva sí incluye ese bloqueo. El ejecutable final 0.1.6 abrió hasta la pantalla de conexión. La recuperación de una copia y la instalación interactiva con la cuenta y equipo definitivos siguen pendientes.
