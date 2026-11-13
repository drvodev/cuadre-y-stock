# Catálogo: listas, filtros y características

Actualización: 12 de noviembre de 2026. La beta ejecutable 0.1.2 publicada se conserva; este bloque corresponde a las fuentes en desarrollo.

## Funciones y reglas

- Artículos con SKU único, descripción, categoría, marca, presentación, modelo/tipo, unidad, origen, contenido descriptivo, costo y precio sugerido opcionales. La unidad y la precisión de existencias siguen protegidas al editar.
- Categorías, marcas, unidades y presentaciones se administran independientemente: crear, renombrar, desactivar y reactivar. Una categoría puede existir antes de tener artículos.
- Los nombres de una misma lista no se duplican por mayúsculas ni espacios repetidos. Una opción inactiva conserva su nombre reservado; se reactiva editando esa misma opción. Cada edición comprueba la versión vigente.
- Las listas ofrecen sugerencias. Los artículos conservan sus textos: renombrar una opción no reasigna automáticamente artículos existentes ni altera documentos anteriores. Para mover un artículo a otra categoría, se edita su categoría. No se convierten bolsas en cajas ni unidades en metraje.
- Escribir una opción nueva en un artículo la registra automáticamente para futuras sugerencias. Reutilizar una opción desactivada no la reactiva. Editar un artículo sin cambiar esos textos no recrea una opción que fue renombrada.
- Características descriptivas por artículo: por ejemplo color, ancho, material o modelo. Hasta 50 pares; nombre de hasta 60 caracteres y valor de hasta 500. Nombres normalizados sin duplicados y valores no vacíos. No alteran cantidades, unidades, costos ni precios.
- Combinación de búsqueda libre, categoría, marca, unidad, presentación, tipo, origen, estado y varias características. Los filtros se combinan con AND. Campos de lista y valores de características se comparan sin distinguir mayúsculas; la búsqueda libre trata %, _ y comillas como texto.
- Artículos, opciones e historiales tienen páginas de hasta 100 registros. Las sugerencias desplegables muestran hasta 100 opciones; se puede escribir un valor manualmente y gestionar el resto desde la lista paginada. No se cargan todos los artículos al abrir la pantalla.

## Pantallas y uso

Catálogo presenta una tabla con una única columna Descripción, categorías como cabeceras desplegables y un filtro superior de categoría. Los grupos se organizan dentro de cada página, conservando el cursor por SKU para no perder artículos. Buscar o pulsar Enter vuelve a la primera página. Más filtros permite combinar condiciones y características; Limpiar filtros restablece artículos activos.

El formulario Crear o editar artículo se inicia plegado y conserva los datos necesarios para Stock. Se abre al seleccionar un artículo o pulsar Añadir artículo. Características del artículo permite añadir y quitar pares nombre/valor. Un par escrito pendiente se valida al guardar para evitar perderlo silenciosamente.

La pestaña Categorías y listas permite elegir la lista, buscar, avanzar por páginas y editar una opción seleccionada. Mostrar inactivas permite recuperar opciones desactivadas. Las altas y los datos de los artículos siguen siendo manuales.

Las imágenes quedan para una fase posterior. El SKU estable permite vincularlas mediante una entidad de archivos separada; no se guardan imágenes ni datos binarios dentro de las características.

## Persistencia y actualización

La migración 8 añade atributos JSONB, opciones con identidad UUID, índices para filtros e historial de cambios de listas. Importa las opciones de artículos existentes sin borrar ni reescribir esos artículos. El disparador de historial existente conserva también las características nuevas; las versiones previas sin ese campo se leen con características vacías.

Las migraciones 1 a 7 mantienen su SQL. El verificador de migraciones admite los hashes LF y CRLF del mismo contenido para evitar rechazos debidos a finales de línea de Git. Una modificación del SQL continúa provocando un error. Los hashes ya registrados no se sustituyen.

No se han aplicado estos cambios a una base del negocio. Se utilizan esquemas desechables propios para las comprobaciones.

## Clases y acciones principales

| Componente | Propósito y acciones |
|---|---|
| ListaCatalogo | Define las cuatro listas y sus nombres en pantalla. |
| OpcionCatalogo | Conserva identidad, nombre, estado y versión; normaliza la clave de comparación. |
| CambioOpcionCatalogo | Expone una versión histórica y su fecha. |
| AtributosCatalogo | Valida y copia las características como mapa inmutable. |
| FiltroCatalogo | Expresa condiciones combinadas y estado activo/inactivo. |
| Variante | Conserva características junto a cada versión del artículo; mantiene el constructor anterior para compatibilidad. |
| ProductoRepository | Contrato de artículos, listas, filtros e historiales. |
| ProductoMemoriaRepository | Aplica las mismas reglas en memoria para las pruebas de contrato. |
| ProductoPostgresRepository | Guarda artículos y características, consulta filtros e historial mediante parámetros JDBC. |
| ListasCatalogoPostgres | Persiste opciones, compara versiones y consulta auditoría. |
| CatalogoService | Valida páginas y cursores y expone las operaciones a las pantallas. |
| CatalogoView | Presenta artículos por descripción/categoría y conecta búsqueda y edición. |
| ListasCatalogoView | Conecta alta, edición, estado, búsqueda y paginación de listas. |
| AtributosForm | Recoge, valida y elimina características y sus filtros. |
| MigracionesPostgres | Aplica ampliaciones transaccionales y verifica contenido previo compatible con LF/CRLF. |

## Verificación y límites

El contrato compartido prueba listas sin artículos, duplicados, versiones, desactivación, históricos, filtros combinados, texto especial, atributos inmutables y paginación en memoria y PostgreSQL. Se comprueban además dos ediciones concurrentes y la actualización desde siete migraciones con hashes LF/CRLF mezclados. La suite anterior de Stock, Caja, reservas y ventas continúa formando parte de las regresiones.

La revisión gráfica y la integración del nuevo tema se coordinan con el agente de visuales. No se considera acreditada por una compilación ni por las pruebas de servicios. La paginación está probada con cientos de registros nuevos y los 2.500 artículos de la prueba anterior; no constituye un ensayo de rendimiento de producción.
