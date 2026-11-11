# Cuadre y Stock · beta de pruebas 0.1.1

Esta versión conecta pantallas con las funciones ya implementadas. Es una beta para probar con datos de demostración: todavía no es la entrega completa del plan.

La versión 0.1.1 incorpora un tema oscuro de tonos suaves en formularios, tablas, pestañas y mensajes. En Catálogo, escribe el texto y pulsa **Enter** o **Buscar**: ambos reinician la paginación y ejecutan la misma búsqueda.

## Abrir el programa

1. Extrae todo el ZIP. Conserva juntas las carpetas `app` y `runtime` y el archivo `CuadreYStockBeta.exe`.
2. Abre `CuadreYStockBeta.exe`. El paquete incluye el entorno Java; no necesita IntelliJ ni Maven para funcionar. PostgreSQL debe estar instalado y en ejecución.
3. Completa la conexión: servidor `127.0.0.1`, puerto `5432`, base `postgres`, usuario `postgres` y la contraseña que elegiste en el instalador. Escríbela solo en el programa; no se guarda en el paquete ni se necesita enviarla por chat.
4. Conserva el esquema `cuadre_pruebas`, o elige otro nombre nuevo con minúsculas, números y guion bajo. Se crea y prepara ese esquema. No se borran esquemas ni se mezclan automáticamente tablas antiguas. La cuenta usada para esta preparación requiere permiso de crear esquemas.
5. Pulsa **Conectar e iniciar pruebas**. Las siguientes veces, usa el mismo esquema para recuperar tus datos. Para una prueba independiente, elige un esquema nuevo.

Para estas pruebas se usa tu cuenta de administración de PostgreSQL. La configuración de un usuario limitado y el arranque de producción siguen pendientes. No uses datos reales.

## Recorrido recomendado y resultados esperados

1. Pulsa **Cargar datos de demostración**. Se añaden cinco artículos DEMO y stock en el almacén `Surquillo`. Repetir la carga no repone ni sobrescribe sus existencias.
2. En **Catálogo**, busca `DEMO-RIN-GI`: precio 20. En **Stock**, consulta ese SKU y almacén `Surquillo`: físico 10, reservado 0, disponible 10. Puedes probar la creación de otro artículo y registrar después su stock inicial.
3. En **Caja y cierre**, escribe tu nombre de pruebas, fecha del día y fondo inicial 100; pulsa **Abrir turno**. El turno queda seleccionado para las operaciones siguientes.
4. En **Nueva operación**, elige Venta y Boleta, número `B3847`. Busca el SKU `DEMO-RIN-GI`, cantidad 2, precio 20 y añade el ítem. En pagos escribe Efectivo 40 y Efectivo recibido 50. Confirma: total 40, vuelto 10. El stock físico quedará en 8 y el efectivo esperado en 140.
5. Registra una Separación con Nota de Venta `N100` y nombre de cliente ficticio. Busca `DEMO-LAV-100`, cantidad 1, precio 500. Registra un abono en efectivo de 200. El lavadero queda físico 4, reservado 1 y disponible 3; saldo de la separación 300. El efectivo esperado será 340.
6. En **Operaciones**, selecciona `N100`, abre **Registrar abono** y registra Yape 300. El saldo queda cero. En **Entregar separación pagada**, puedes indicar Boleta `B3848` y confirmar. El stock del lavadero queda físico 3, reservado 0 y disponible 3. La entrega no vuelve a cobrar.
7. En Caja registra un gasto de 20 en efectivo y fuente CAJA. El efectivo esperado queda en 320. Para cerrar, escribe contado 320 y tu firma. Verifica YAPE con Cobros 300 y el resto cero. Los demás medios sin actividad pueden quedar vacíos. Confirma el cierre.
8. Cierra y vuelve a abrir el programa, usando el mismo esquema. Puedes consultar operaciones y seleccionar el turno cerrado en Caja. Sus movimientos y cierre deben conservarse.
9. Para probar una anulación posterior, abre otro turno con fondo 320. En Operaciones busca `B3847`, elige devolución completa de 40 en efectivo, escribe motivo y marca el reingreso si ambas bolsas se devolvieron en condición de venta. El turno nuevo queda con esperado 280; el cierre anterior conserva sus importes.

## Pruebas adicionales

- Venta sin SKU: escribe descripción, cantidad y precio. Guarda el cobro sin modificar inventario.
- Venta con stock cero: se permite y el stock permanece en cero.
- Separación con stock insuficiente: se muestra solicitado, disponible y faltante. Puedes confirmar reservar solo lo disponible. Para entregar necesitarás ingresar lo faltante y completar el pago.
- Pago incompleto de Venta: se rechaza; usa Separación si hay un abono.
- Sobrepago digital o número de documento ya utilizado: se rechaza y no debe duplicar dinero.
- Cierre con diferencia: escribe una observación. Si llegó un movimiento después de mostrar el conteo, actualiza y revisa los valores antes de confirmar otra vez.
- Catálogo: el SKU, la unidad y la precisión de cantidades se conservan al editar. El costo vacío significa desconocido.

## Precauciones prácticas de esta versión

Los formularios de confirmación conservan su identificador cuando aparece un error. Reintenta con los mismos datos. Si no sabes si la respuesta se perdió, consulta Operaciones o Caja antes de crear otra operación. El identificador del formulario no se conserva al cerrar la aplicación; comprueba el historial después de un fallo o reinicio.

Las consultas están limitadas a 100 resultados. Catálogo permite buscar y avanzar por páginas; Operaciones muestra las últimas 100 y permite buscar un documento exacto. Las acciones de base de datos pueden pausar brevemente la pantalla local mientras responden.

## Alcance pendiente

No están disponibles todavía compras con su documento y entrada coordinada, cambios de producto, devoluciones parciales, reportes Excel, reconstrucción histórica completa, respaldos y restauración, ni administración independiente de listas. La exportación, impresión y facturación electrónica no forman parte de estas pantallas; la impresión y la emisión electrónica ya estaban fuera de esta beta inicial.

## Verificación de entrega

En la versión 0.1.1 se ejecutaron 40 pruebas Maven con éxito; las 14 de integración se omitieron al no habilitar la base de pruebas en esa ejecución. Se generó y abrió el nuevo paquete Windows. Se revisaron el tema oscuro del catálogo y un mensaje, y la búsqueda real con Enter: de cinco artículos DEMO, buscar `Rinok` mostró únicamente sus tres coincidencias. Este cambio no modifica reglas ni tablas de la base de datos.

El proyecto compila y la suite ejecutó 54 pruebas exitosas, 14 de integración con PostgreSQL aislado. El paquete Windows se generó con Java incluido y se abrió en este equipo. Se revisaron las cinco pestañas, se cargaron los cinco artículos DEMO, se consultó stock inicial 10, se abrió un turno con fondo 0 y se confirmó la venta ficticia QA-B1 de una bolsa por S/ 20 con S/ 50 recibidos. La pantalla mostró vuelto S/ 30; Operaciones mostró entregada y saldo 0; Caja mostró esperado S/ 20 y Stock pasó a 9 al actualizar la consulta. No se ha completado manualmente todo el recorrido de separación, devolución y cierre ni se ha validado en el equipo Windows 10 del negocio.

La ventana utilizada para esa revisión tiene un esquema de laboratorio con esa venta de ejemplo. Para seguir el recorrido anterior desde cero, abre el ejecutable y elige un esquema nuevo en tu PostgreSQL habitual.

Si encuentras un problema, anota pestaña, acción, datos ficticios utilizados, resultado esperado y mensaje mostrado. No incluyas contraseñas ni información real de clientes al reportarlo.
