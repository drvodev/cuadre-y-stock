# Guía de uso de Cuadre y Stock

Beta 0.1.4 | Preparación para la prueba del 14 de noviembre de 2026 | Surquillo

Esta versión permite probar productos, existencias, compras, ventas, separaciones, devoluciones, cambios, cierre de caja y copias manuales. El propósito es registrar cada operación una vez y encontrarla después sin revisar varios cuadernos. La prueba debe hacerse con datos ficticios antes de usar dinero o información real.

La beta todavía requiere aceptación en el equipo del trabajo. Los reportes Excel .xls y la preparación para producción siguen pendientes. La versión pública anterior 0.1.2 no contiene estas ampliaciones: para este recorrido utiliza el paquete identificado como 0.1.4.

### Qué resuelve cada pantalla

| Pantalla | Para qué sirve |
| --- | --- |
| Cierre de caja | Abrir un turno, ver movimientos, anotar ganancia manual, contar y cerrar. |
| Stock | Consultar disponibilidad, costo o precio sugerido, mínimos, movimientos y saldo de una fecha. |
| Catálogo | Crear y editar productos, categorías, marcas, unidades, presentaciones y características. |
| Nueva operación | Registrar venta o separación, ítems, pagos e indicaciones. |
| Compras | Guardar documento, productos recibidos y pago completo juntos. |
| Devoluciones y cambios | Registrar una nota vinculada al original y solo el dinero que realmente entra o sale. |
| Operaciones | Consultar comprobantes, abonar y entregar separaciones pagadas. |
| Respaldos | Crear una copia manual y recuperarla en otra base local vacía. |

El programa calcula y conserva registros, pero no cuenta productos físicos ni confirma pagos ante bancos, Yape o Plin. El encargado sigue verificando la mercancía y el dinero. No necesitas conocimientos de programación o inteligencia artificial para seguir esta guía.

## Preparar y abrir la beta

### El paquete y el equipo

Extrae todo el ZIP en una carpeta. Conserva juntas app, runtime y CuadreYStockBeta.exe; no copies únicamente el .exe. Abre ese archivo. Java viene incluido y el usuario no necesita IntelliJ ni Maven. PostgreSQL debe estar instalado y en ejecución en el equipo.

El objetivo recomendado es Windows 11 de 64 bits en equipo Intel o AMD. El funcionamiento en el Windows 10 del negocio debe comprobarse allí. No hay una garantía propia de compatibilidad de la aplicación en todas las versiones de Windows. Como referencia práctica para el piloto se proponen 8 GB de memoria, SSD y 10 GB libres iniciales, más crecimiento y copias; estos valores no son mínimos medidos.

Si Windows muestra que una directiva bloqueó el archivo, conserva el mensaje y consulta al responsable del equipo. La entrega anterior tuvo ese bloqueo en una revisión. No cambies las políticas de seguridad para seguir el recorrido.

### Conectar sin mezclar los datos reales

En la ventana de conexión revisa servidor 127.0.0.1, puerto 5432, base postgres, usuario PostgreSQL y tu contraseña. Los valores deben coincidir con la instalación local. La contraseña se escribe únicamente en el programa y se usa en la sesión; no la envíes al reportar incidencias.

Para comenzar desde cero, escribe un esquema nuevo, por ejemplo prueba_20261114. Un esquema separa las tablas de esta prueba dentro de la base. Usa minúsculas, números y guion bajo. Pulsa Conectar e iniciar pruebas. La cuenta de pruebas necesita permiso para crear y preparar ese esquema.

Al volver a abrir el programa, elige la misma base y el mismo esquema para recuperar tus registros. Otro nombre comienza una prueba independiente. El programa prepara sus tablas y verifica las actualizaciones; no adopta automáticamente tablas antiguas incompatibles.

### Cargar demostración y manejar la ventana

En el menú Pruebas elige Cargar datos de demostración y confirma. Se crean cinco artículos DEMO en Surquillo. Repetir la carga no repone ni sobrescribe sus existencias. Minimizar, Maximizar o Restaurar y Cerrar controlan la ventana. Cerrar el programa no cierra el turno: para eso se usa Cierre de caja.

## Productos y existencias

### Catálogo y listas

Catálogo muestra descripciones agrupadas por categoría. Escribe una búsqueda y pulsa Enter o Buscar; ambos vuelven a la primera página. Más filtros permite combinar categoría, marca, unidad, presentación, origen, estado y características. Mostrar más o avanzar de página permite consultar el resto sin cargar todo a la vez.

Añadir artículo abre el formulario. Registra SKU, descripción, categoría, unidad y los demás datos que correspondan. El SKU identifica de forma estable una presentación: una bolsa de 25 kg cuenta como una bolsa. El contenido descriptivo no convierte automáticamente bolsas en kilos ni cajas en metros cuadrados.

Costo y precio sugerido son valores manuales e independientes. Vacío significa desconocido o por definir; no significa cero. Las características, como color o medida, describen el artículo y no cambian sus cantidades. En Categorías y listas puedes crear, renombrar, desactivar o reactivar opciones antes de tener productos. Renombrar una opción no mueve automáticamente los artículos anteriores.

### Leer Stock correctamente

| Término | Significado |
| --- | --- |
| Físico | Cantidad registrada en el almacén. |
| Reservado | Parte comprometida con separaciones. |
| Disponible | Físico menos reservado. |
| Agotado | Existe un saldo y su disponible es cero. |
| Sin stock inicial | Todavía no hay saldo para ese producto y almacén. |
| Bajo | Disponible menor o igual al mínimo configurado. |

Stock presenta SKU, producto, unidad, disponibilidad y costo o precio sugerido. Selecciona el valor que deseas ver; Editar precio cambia ese dato manual del artículo y conserva el historial. No actualiza por sí mismo una compra antigua ni el precio ya vendido.

Abre Movimientos y ajustes de stock para consultar un SKU, registrar existencias iniciales, fabricación o ajustes justificados y configurar mínimos. Más movimientos carga otra página. Consultar al cierre del día muestra físico, reservado y disponible al final de la fecha elegida en horario de Lima.

Una compra registrada en Compras ya agrega sus productos: no repitas después una Entrada por compra manual. Un ajuste sirve para explicar una diferencia real de conteo; no debe usarse para ocultar una venta o devolución.

## Abrir caja vender y separar

### Abrir y reconocer el turno

En Cierre de caja abre Abrir un turno. Indica caja, fecha operativa, encargado y fondo inicial; confirma Abrir turno. Solo puede existir un turno abierto por caja. Al seleccionar uno, las operaciones siguientes se vinculan a ese turno. Un movimiento después de medianoche mantiene la fecha operativa con la que se abrió.

El libro muestra comprobante, descripción, pago y ganancia. Anotar ganancia registra una cifra manual por documento con historial; no cambia cobros, vuelto ni cierres. La aplicación no deduce ganancia de un costo vacío ni del importe que se decidió no devolver a un cliente.

### Venta directa

En Nueva operación elige Venta, tipo de comprobante y número manual o sugerido. Añade cada ítem con descripción, cantidad, precio e indicaciones si corresponden. El SKU vincula el ítem con inventario; una línea libre no mueve existencias. Antes de confirmar revisa productos y total.

Completa los medios por el total exacto: efectivo, Visa, transferencia, depósito, Yape o Plin. Efectivo recibido indica los billetes recibidos para la parte pagada en efectivo. Por ejemplo, venta 40, efectivo aplicado 40 y recibido 50 produce vuelto 10; a la caja ingresan 40.

La beta permite registrar ventas con stock cero sin generar cantidades negativas. Esto no demuestra que el producto haya sido entregado físicamente. Durante el piloto contrasta lo vendido con lo entregado y explica cualquier diferencia. Los comprobantes son internos; no se emiten electrónicamente a SUNAT.

### Separación abonos y entrega

Elige Separación y Nota de venta, cliente ficticio y primer abono positivo. La reserva reduce el disponible, sin sacar todavía el producto físico. Si falta cantidad, se muestra la propuesta y debes aceptar explícitamente lo que se puede reservar.

En Operaciones busca el documento y usa Registrar abono para los siguientes pagos; cada uno pertenece al turno en que se recibe. Solo entrega una separación pagada y con unidades disponibles para completar su reserva. Entregar separación pagada descuenta físico y reservado una sola vez; un comprobante final no debe generar otro cobro.

## Compras gastos y cierre diario

### Compra recibida y pagada

Con turno abierto, entra a Compras y Registrar compra. Completa proveedor, tipo y número del documento, fuente del dinero y total. Añade las cantidades realmente recibidas; Consultar SKU recupera descripción y unidad. Deja costo unitario vacío si no lo conoces. Una línea libre, como un flete, no crea inventario.

Completa el pago íntegro del documento. Esta beta no administra compras a crédito. Si todos los costos son conocidos, sus subtotales deben coincidir con el total; si alguno se desconoce, escribe el total manualmente y los costos conocidos no pueden superarlo. Confirma recepción y pago una sola vez.

CAJA afecta el medio del turno. EXTERNA conserva el pago sin restarlo del efectivo o medios de la caja; igualmente requiere turno abierto. El costo de la compra queda en su detalle histórico y no reemplaza automáticamente el costo manual del catálogo. En Historial puedes avanzar y Ver detalle. No se editan ni anulan compras confirmadas desde esta pantalla.

### Gastos y otros movimientos

En Cierre de caja se registran gastos de tienda, sueldos, comisiones, retiros y otros ingresos con importe, medio, origen del dinero y motivo. Dividir entre varios medios permite un pago combinado. Una compra registrada en Compras no debe repetirse como gasto o compra manual de caja.

### Contar y cerrar

En Contar y cerrar caja escribe el efectivo contado, firma y observación si hay diferencias. Verifica por separado cobros, devoluciones, egresos y otros ingresos de cada medio digital con actividad. Los totales digitales no son el saldo completo del banco.

Efectivo esperado = fondo inicial + ingresos en efectivo de caja - egresos en efectivo de caja. Diferencia = contado - esperado. Si hubo un movimiento nuevo desde el conteo, actualiza y revisa antes de cerrar. Confirmar cierre guarda los valores y bloquea nuevos movimientos en ese turno. Las correcciones posteriores van al turno actual y no reescriben el cierre antiguo.

Si aparece un error o no sabes si quedó guardado, consulta el historial antes de crear otra operación. El formulario conserva su identificador tras errores, pero no después de cerrar el programa. Reintenta con los mismos datos solo cuando corresponda.

## Devoluciones y cambios con importe manual

### Registrar una nota

Abre Devoluciones y cambios con un turno abierto. Busca la operación original por tipo y número. En las líneas pendientes escribe la cantidad que vuelve y marca Reingresar vendible solo si el producto realmente se recibe y puede venderse otra vez. Devolver dinero y reingresar productos son decisiones separadas; los ítems libres no crean Stock automáticamente.

Elige Devolución o Cambio. Escribe número de nota único, motivo e importe reconocido. Ese importe es el dinero acordado que se reintegra, o el crédito que se aplica a los reemplazos. Debe estar entre cero y el valor original de la cantidad devuelta. Calcular importes muestra el resultado antes de confirmar.

| Ejemplo | Qué se registra |
| --- | --- |
| Original 100 y devolución reconocida 90 | Salen 90 en el turno actual; 10 quedan como importe no devuelto. |
| Crédito reconocido 90 y reemplazo 120 | Entran únicamente 30 adicionales. |
| Crédito reconocido 90 y reemplazo 60 | Salen únicamente 30 de reintegro. |
| Crédito reconocido 90 y reemplazo 90 | Se guardan nota y productos, sin movimiento de dinero. |

Los S/ 10 no devueltos del primer ejemplo no se registran automáticamente como ganancia. La columna de ganancia sigue siendo manual. Las notas conservan valor original, reconocido, diferencia no devuelta, cantidades y vínculo con el reemplazo.

### Confirmar sin duplicar dinero

Para un cambio añade reemplazos y su documento nuevo. Registra solo los medios y montos que efectivamente entran o salen. Si no hay diferencia, deja los pagos vacíos. Efectivo recibido se usa únicamente cuando cobras un adicional en efectivo, para calcular su vuelto.

Confirma la nota y consulta Historial de notas o Ver nota. La aplicación controla que no se devuelva más cantidad que la original. Si ya existen notas, las unidades restantes se gestionan aquí y no por otra anulación completa. Una corrección parcial mantiene pendiente el resto; una corrección total cambia el estado correspondiente sin crear deuda por lo no reintegrado.

Una nota con diferencia cero también necesita turno abierto. Las notas son comprobantes internos consultables: esta pantalla no imprime ni emite documentos electrónicos. Ante una respuesta dudosa consulta el historial antes de pulsar Nueva nota o reiniciar.

## Crear una copia y recuperarla sin reemplazar la base

### Copia manual

En Respaldos comprueba la carpeta bin de PostgreSQL, por ejemplo C:\Program Files\PostgreSQL\17\bin. Elegir herramientas permite cambiarla por una instalación compatible. Conecta primero al esquema que deseas copiar. Pulsa Crear copia manual, elige una carpeta existente y espera el mensaje de resultado.

Se genera un archivo .backup con nombre y fecha únicos. Incluye el esquema seleccionado y sus datos, funciones y versiones. No copia otros esquemas, roles ni toda la configuración del servidor. La aplicación no programa copias automáticas ni elimina copias antiguas; tú eliges cuándo y dónde guardarlas.

La copia contiene los datos del negocio y la aplicación no la cifra. Elige una carpeta protegida y conserva otra copia fuera del equipo si quieres protegerte de su pérdida. Que el archivo exista no sustituye una recuperación de prueba.

### Recuperación en otra base vacía

Primero un responsable debe crear otra base local vacía en el mismo servidor, por ejemplo recuperacion_20261114. La aplicación no la crea. Cierra las conexiones de otros usuarios a ese destino. No escribas el nombre de la base original.

Pulsa Elegir copia y selecciona un archivo de procedencia confiable. Escribe el nombre de la base nueva y usa Restaurar en base vacía. Confirma y espera el resultado. Se rechazan la base original, bases plantilla y destinos ocupados; no se usan opciones para borrar su contenido.

Después abre una sesión en la base recuperada e indica el nombre del esquema que tenía la copia. Revisa productos, físico y reservado, operaciones y cierres. La base original se conserva. Si la recuperación falla, no declares la copia válida ni cambies la base de trabajo hasta investigar.

Esta primera versión solo realiza copias y recuperación en localhost o 127.0.0.1, sin opciones especiales de conexión. Necesita pg_dump y pg_restore compatibles con el servidor y el archivo. Un respaldo puede contener instrucciones ejecutables; no restaures archivos recibidos de fuentes desconocidas.

### Comprobación ya realizada

La prueba automatizada recuperó catálogo, Stock físico y reservado, una reserva, una venta y un cierre en una base temporal, y comprobó migraciones y rechazos de destinos inválidos. Falta repetir la aceptación completa en el equipo del negocio con su instalación y permisos.

## Recorrido de prueba con resultados esperados

Para el 14 de noviembre utiliza un esquema nuevo, carga DEMO una sola vez y sigue este orden sin añadir otras operaciones. Los números son ficticios y los resultados suponen que se comienza desde cero.

| Paso y acción | Resultado esperado |
| --- | --- |
| Abrir turno con fondo 100 | Efectivo esperado 100. |
| Venta QA-V1 de 2 DEMO-RIN-GI a 20; efectivo 40 y recibido 50 | Vuelto 10; físico Rinok 8; efectivo esperado 140. |
| Compra QA-C1 a proveedor ficticio: 2 del mismo SKU a costo 12, total 24 desde CAJA efectivo | Físico Rinok 10; efectivo esperado 116; una compra. |
| Separación Nota QA-N1: 1 DEMO-LAV-100 a 500, abono efectivo 200 | Lavadero físico 4, reservado 1, disponible 3; saldo 300; caja 316. |
| Abonar Yape 300 a QA-N1 | Saldo cero; efectivo sigue 316; cobros Yape 300. |
| Entregar QA-N1 con Boleta QA-V2 | Lavadero físico 3, reservado 0, disponible 3; no se vuelve a cobrar. |
| Registrar gasto 20 desde CAJA efectivo | Efectivo esperado 296. |
| Contar 296 y verificar Yape cobros 300; firmar y cerrar | Diferencia efectivo cero; turno cerrado. |
| Reabrir programa con misma base y esquema | Operaciones y cierre conservados; cargar DEMO no repone Stock. |

### Pruebas independientes de corrección y recuperación

Para no alterar los totales anteriores, prueba devoluciones y cambios en otro esquema. Crea una venta ficticia de 100 y devuelve cantidades con reconocido 90: comprueba salida 90, no devuelto 10 y ganancia sin asignación automática. Repite con crédito 90 y reemplazos 120, 60 y 90: deben entrar 30, salir 30 o no moverse dinero, respectivamente.

Crea una copia del esquema de recorrido cerrado y recupérala en otra base vacía. Compara el Rinok físico 10, lavadero físico 3, saldo de QA-N1 cero, cierre 296 y Yape 300. Anota versión, pantalla, pasos, datos ficticios, esperado, obtenido y mensaje para cada incidencia; no incluyas contraseñas ni clientes reales.

## Medir la utilidad frente al cuaderno

Tu referencia actual es aproximadamente 20 minutos o más por día o cierre para rellenar ventas, gastos e ingresos a mano. El volumen cambia con las ventas y buscar registros en cuadernos resulta problemático. Esa referencia es una estimación tuya; aún no hay tiempos comparables medidos con la beta completa.

| Si la app tomara | Ahorro al día | Reducción | En 26 jornadas |
| --- | --- | --- | --- |
| 15 minutos | 5 minutos | 25 % | 2 h 10 min |
| 10 minutos | 10 minutos | 50 % | 4 h 20 min |
| 5 minutos | 15 minutos | 75 % | 6 h 30 min |

Son escenarios calculados, no ahorro demostrado. Se usa 20 minutos como referencia conservadora y 26 días como supuesto mensual. El ahorro real se calcula restando al tiempo manual todo el tiempo usado con la aplicación, incluido capturar durante el día y revisar el cierre.

### Cómo medir durante el piloto

Registra al menos diez jornadas con actividad baja, media y alta. Anota ventas, gastos, ingresos, tiempo total de registro y correcciones. Mide aparte cuánto tardas en encontrar la misma venta, gasto o abono en cuaderno y aplicación, incluyendo búsquedas fallidas. Separa capacitación y carga inicial del catálogo del uso habitual.

La mejora buscada es tener datos localizables y evitar repetir cálculos o transcripciones. No hay evidencia para prometer aumento de ganancias. Para valorar el ahorro en soles hacen falta el valor de la hora y los costos de instalación y soporte.

### Por qué estas herramientas y por qué local

Java contiene las reglas; JavaFX construye las pantallas; PostgreSQL conserva los registros y permite guardar juntas las partes de una operación. Maven organiza la construcción y JUnit repite comprobaciones. Son herramientas del programador, no conocimientos que el cajero deba aprender.

Se comienza en una computadora para comprobar Surquillo sin depender de la conexión entre locales. PostgreSQL se eligió también por la expansión futura: mantener el mismo motor evita una migración innecesaria. Más adelante se prevé un servicio central y permisos por sede; todavía no existe esa operación multisede ni sincronización sin conexión.

Los reportes .xls diarios y mensuales, la cuenta de aplicación limitada, la instalación de producción y la aceptación completa en el equipo del trabajo siguen pendientes. La beta tiene reglas automáticas y no una IA que confirme dinero o interprete cuadernos.
