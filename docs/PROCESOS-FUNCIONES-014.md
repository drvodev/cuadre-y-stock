# Procesos y funciones de Cuadre y Stock

Informe técnico de la beta 0.1.4 | 13 de noviembre de 2026 | Preparación para prueba del 14 de noviembre

La beta ya conecta Catálogo ampliado, Stock actual e histórico, Caja, ventas y separaciones, Compras, notas de devolución y cambio y respaldos manuales locales. El circuito tiene persistencia PostgreSQL y pruebas por bloque. Reportes .xls, permisos e instalación de producción y aceptación operativa completa permanecen pendientes.

### Estado y evidencia de la entrega

La documentación de pruebas registra una suite completa de 102 casos, 50 con PostgreSQL aislado, sin fallos ni omisiones. Después se amplió y repitió la prueba de recuperación. Se ejecutaron además seis comprobaciones de seguridad, cuatro en memoria y dos PostgreSQL, correctas y sin omisiones. Son ejecuciones distintas: no se presenta una suite de 108 ejecutada de una vez.

Las comprobaciones adicionales cubren entradas incorrectas, duplicados e inyección SQL. La revisión visual y la aceptación del negocio tienen alcance separado. Que una pantalla compile o que un servicio pase sus casos no demuestra por sí solo cada recorrido en el equipo Windows del trabajo.

| Versión o etapa | Interpretación |
| --- | --- |
| 0.1.2 publicada anteriormente | Paquete de referencia sin las ampliaciones actuales. No usarlo para evaluar Compras, Correcciones o Respaldos. |
| 0.1.4 | Paquete destinado a la próxima prueba; incluye aplicación y runtime Java. PostgreSQL y sus herramientas son externos. |
| Producción | No declarada. Faltan despliegue, permisos y aceptación del ciclo completo. |

### Cómo usar este informe

Cada proceso explica el problema, sus reglas y componentes, la persistencia y la evidencia que lo sostiene. La guía de usuario aporta los pasos de pantalla y resultados de demostración. Las razones históricas explícitas provienen del plan y decisiones confirmadas; la valoración de ventajas y costos técnicos es una justificación de ingeniería, no un benchmark comparativo entre tecnologías.

## Java PostgreSQL y herramientas del proyecto

### Java y continuidad de la lógica

Java fue elegido explícitamente para la aplicación de escritorio. El tipado, records inmutables, BigDecimal y APIs de fecha permiten expresar cantidades, montos y estados. Separar servicios de vistas facilita reutilizar reglas en un servicio central futuro. El pom fija release 21; un JDK posterior de construcción no equivale a certificar todas las versiones de ejecución.

### PostgreSQL desde la primera etapa

El plan sustituyó la propuesta anterior de SQLite por PostgreSQL para evitar mantener dos modelos de persistencia y cambiar de motor al expandir. Ejecutarlo localmente conserva la misma tecnología de transacciones prevista para varias sedes. El costo es instalar y administrar servidor, usuarios, herramientas y copias incluso para una beta de una computadora.

| Componente | Función y motivo |
| --- | --- |
| JavaFX controls 21.0.6 | Pantallas, tablas, formularios y tareas de interfaz. Framework gráfico actual. |
| pgJDBC 42.7.7 | Acceso SQL mediante DataSource y JDBC; parámetros y transacciones. No se usa ORM. |
| JUnit Jupiter 5.11.4 | Aserciones y ejecución repetible de reglas, contratos e integración condicionada por entorno. |
| Maven Compiler 3.13.0 y Surefire 3.5.2 | Compilación Java 21 y ejecución de pruebas. |
| Maven JAR 3.4.2 y Dependency 3.7.1 | Manifiesto con Lanzador y preparación de dependencias runtime para distribuir. |
| JavaFX Maven 0.0.8 y jpackage | Lanzamiento en desarrollo y generación de imagen Windows con runtime. |

IntelliJ es una herramienta de desarrollo, no un requisito del cajero. No se declaran Spring, Hibernate ni un framework de IA. POI HSSF continúa como propuesta para .xls: no hay exportador ni dependencia implementados. No se afirma que Java sea más rápido que C# o Python para este negocio sin una comparación equivalente.

## Arquitectura de la sesión y controles comunes

Aplicacion prepara la conexión y compone SesionBeta con servicios, repositorios, reloj y turno seleccionado. Lanzador sirve de entrada al paquete. PreparacionBeta valida el nombre de esquema, crea uno si corresponde, configura currentSchema y aplica migraciones. No borra tablas para adoptar una instalación antigua incompatible.

La interfaz presenta ocho pestañas: Cierre de caja, Stock, Catálogo, Nueva operación, Compras, Devoluciones y cambios, Operaciones y Respaldos. Se actualizan consultas al cambiar de ciertas pestañas. Ui, PagosForm y BarraVentana comparten controles, importes, mensajes y acciones de ventana. El menú DEMO carga cinco artículos sin sobrescribir saldos existentes.

### Atomicidad e identificadores

UnidadTrabajoPostgres comparte una conexión por hilo de operación. Los commits internos de repositorios se posponen; un rollback interno obliga a revertir la unidad completa. VentaService, CompraService y CorreccionVentaService coordinan sus efectos dentro de esa frontera. No se debe sustituir una transacción por llamadas independientes desde una vista.

UUID y huella de datos identifican una confirmación y permiten devolver el resultado previo tras un reintento equivalente. Una solicitud confirmada con otro contenido se rechaza. Los formularios conservan sus identificadores después de un error, pero no sobreviven al cierre de la aplicación: tras resultado incierto o reinicio se consulta el historial antes de crear otra solicitud.

### Exactitud y concurrencia

BigDecimal y NUMERIC evitan coma flotante para montos y cantidades. Cantidades admiten hasta tres decimales si la unidad lo permite; importes se validan a dos. El importe de cada ítem usa HALF_UP y se suman líneas redondeadas. La versión optimista evita sobrescribir una edición obsoleta.

Las escrituras de Stock y Caja usan bloqueos advisory globales por módulo; la unidad de trabajo también serializa operaciones coordinadas. Simplifican carreras y primeros registros, pero limitan concurrencia incluso en claves independientes. Antes de aumentar puestos, medir esperas, volumen y latencia y definir orden y granularidad de bloqueo. Las pruebas de integridad no son pruebas de capacidad.

## Catálogo ampliado y listas independientes

### Problema y decisiones

Un catálogo variado necesita categorías, marcas, unidades y presentaciones antes de tener artículos y filtros que se combinen. Las opciones ahora tienen identidad UUID, nombre, estado y versión propios. Normalizar nombres evita duplicados por mayúsculas o espacios repetidos; un nombre inactivo permanece reservado para reactivar la misma opción.

Los artículos conservan textos propios y sus históricos. Renombrar una opción no reasigna artículos ni cambia comprobantes anteriores. Un texto nuevo escrito en un artículo se registra como sugerencia, sin reactivar una opción deshabilitada. La unidad y precisión de Stock siguen protegidas al editar para no reinterpretar saldos existentes.

Atributos descriptivos son hasta 50 pares, con nombres de hasta 60 caracteres y valores de hasta 500, normalizados y sin duplicados. No convierten unidades, no incorporan imágenes y no cambian precios ni cantidades. Costo desconocido continúa null, distinto de cero.

### Componentes y funciones

| Componente | Responsabilidad |
| --- | --- |
| ListaCatalogo y OpcionCatalogo | Definir listas e identidad, nombre, estado, versión y clave normalizada. |
| AtributosCatalogo y FiltroCatalogo | Validar mapa inmutable y expresar condiciones combinadas con AND. |
| Variante y CambioOpcionCatalogo | Conservar datos/versiones e historial de artículos u opciones. |
| CatalogoService y ProductoRepository | Validar páginas/cursores y exponer artículos, listas, filtros e historial. |
| ProductoPostgresRepository y ListasCatalogoPostgres | Persistir JSONB, opciones y versiones con consultas parametrizadas. |
| CatalogoView ListasCatalogoView y AtributosForm | Búsqueda por descripción/categoría, edición manual, gestión y validación de pares. |

El contrato compartido se ejecuta en memoria y PostgreSQL. Comprueba duplicados, versiones, desactivación, histórico, filtros y paginación; hay pruebas de edición concurrente y actualización de un esquema previo. Las páginas se limitan a 100 y los filtros tratan %, _ y comillas como texto, no como SQL del usuario.

## Stock disponible histórico y movimientos

La lista principal presenta cinco columnas: SKU, producto, unidad, disponible y costo o precio sugerido. ListaStockRepository consulta una página por almacén y SKU con una fila adicional para detectar continuidad. ListaStockView conserva filtros aplicados y cursor y evita anexar resultados de otra búsqueda. Editar costo/precio comprueba versión y conserva atributos.

### Estados e invariantes

SaldoStock valida físico no negativo y reservado entre cero y físico. Disponible = físico - reservado. Sin saldo inicial es distinto de agotado y de precio cero; bajo significa disponible menor o igual al mínimo. Los ajustes requieren motivo y no pueden reducir físico por debajo de reservado.

InventarioService coordina movimientos físicos y de reserva; ReservaService prepara, crea, amplía, cancela y despacha con autorización. Los repositorios guardan saldo, reserva y movimiento juntos y deduplican solicitudes. MovimientoPaginado asocia cada movimiento con una secuencia estable y permite recorrer sin duplicados.

### Consulta al corte

Consultar al cierre del día usa el inicio del día siguiente en America/Lima como límite exclusivo. Se recuperan físico y reservado posteriores al último movimiento previo a ese instante; disponible se deriva. No se utiliza el estado actual de la reserva para reconstruir el pasado. La secuencia ordena registros de fecha coincidente.

Las instantáneas ya almacenadas y el índice de migración 10 hacen posible consultar sin reescribir historia. Un reintento no agrega movimiento ni cambia instantánea. El mínimo mostrado es el vigente y no un mínimo histórico. Esta consulta no permite insertar movimientos retroactivos ni equivale a un reporte mensual exportado.

### Venta sin saldo y regreso de mercancía

StockService descuenta min(disponible, solicitada); VentaService permite Stock cero y líneas libres. Es una conducta actual documentada que evita negativos, pero exige distinguir registro comercial de existencia física. En el piloto deben comprobarse lo entregado y lo devuelto, especialmente después de ventas no inventariadas o con faltantes.

Se prueban recorridos de inicialización, reserva, compra, cancelación y reintento, límites temporales, claves aisladas, paginación, agotados y sin inicializar, tanto en memoria como en PostgreSQL. Los 2.500 artículos de una prueba de catálogo son una comprobación funcional de páginas, no un ensayo de rendimiento de producción.

## Caja ventas separaciones y ganancia manual

CajaService controla apertura, pagos combinados, efectivo recibido y vuelto, fuentes de dinero, cierre y conciliación. TurnoPostgresRepository persiste turnos, movimientos, líneas, solicitudes y verificaciones. El índice de un turno abierto por caja y la versión de cierre evitan aperturas duplicadas y aceptar un conteo anterior a un movimiento nuevo.

La fecha operativa pertenece al turno. El efectivo esperado suma fondo e ingresos de efectivo y resta egresos de efectivo de CAJA; EXTERNA no se descuenta. Cobros, devoluciones, egresos y otros ingresos digitales se verifican separados. Una observación explica diferencias, sin modificar cobros para forzar cero. Firma y encargado son texto, no autenticación ni firma digital.

### Funciones comerciales

| Función | Acción y propósito |
| --- | --- |
| VentaService.registrar | Guardar operación, ítems históricos, documento, cobro y Stock juntos; venta exige pago completo y separación abono positivo. |
| abonar | Limitar el pago al saldo y registrarlo en el turno de recepción sin duplicar por reintento. |
| entregar | Exigir separación pagada, completar reservas y descontar una vez; documento final no recobra. |
| anular | Anulación completa con reverso en turno actual; se bloquea si hay notas de corrección previas. |
| consultar y buscarDocumento | Recuperar operación original, ítems y estado; consulta exacta disponible aunque la lista esté limitada. |
| DocumentoService y sugerirNumero | Conservar prefijos/ceros y sugerir correlativo; la unicidad SQL decide conflictos, no la sugerencia. |

### Libro y ganancia

LibroCajaRepository vincula movimientos con comprobantes e ítems históricos y reconoce notas de devolución. La vista muestra cuatro columnas. guardarGanancia registra una cifra manual por documento y comprueba el valor previo para no sobrescribir otra edición; la migración 9 audita cambios. Editar ganancia no modifica pagos ni cierres.

El efectivo recibido integra la huella del cobro para impedir que un reintento cambie el vuelto. El libro conserva importes aplicados; no debe confundirse con un recibo completo de billetes recibidos. Las unitarias simulan autorización de reservas; VentaService la enlaza al estado y saldo reales en integración.

## Compras transaccionales y costos opcionales

### Proceso confirmado

CompraService coordina documento, recepción y pago completo con Caja, Catálogo e Inventario en UnidadTrabajoPostgres. CompraController expone registrar, consultar y listar. CompraView conserva UUID tras error y ofrece historial paginado. El problema que resuelve es una compra anotada varias veces o un egreso guardado sin su entrada.

Compra valida proveedor, documento, fuente, total y pagos; DetalleCompra valida cantidad y costo opcional y calcula subtotal exacto. Si se conocen todos los costos, sus subtotales deben coincidir con el total; con costos desconocidos, los conocidos no lo superan. El total se escribe y se paga íntegro: no hay crédito a proveedores.

Los SKU controlados deben existir, estar activos y coincidir en unidad y precisión. Si falta saldo, se inicia en cero y se agrega la entrada dentro de la transacción. La línea libre conserva concepto y costo sin crear existencias. El costo histórico conocido no sustituye automáticamente costo/precio manual del catálogo.

### Documento fuentes y reintentos

CompraPostgresRepository guarda cabecera, detalles, pagos, solicitud y secuencia. El documento es único por proveedor normalizado, tipo y número. El proveedor sigue siendo un texto manual; no existe maestro ni validación de RUC externa. Proveedores distintos pueden compartir número, por lo que su identificación consistente importa.

CAJA afecta el medio del turno; EXTERNA conserva pago sin reducir sus importes. Ambas requieren turno abierto y usan fecha de confirmación, no recepción retroactiva. No repetir después la compra como movimiento manual de Stock o Caja.

La huella incluye los datos que afectan el resultado y normaliza decimales equivalentes. Un reintento recupera la compra aun después del cierre; datos distintos se rechazan. Documento duplicado, error de una línea o fallo SQL tardío revierten egreso y entradas. Las confirmaciones concurrentes de la misma solicitud conservan una compra.

### Evidencia y límites

Tres pruebas de modelo y ocho PostgreSQL cubren desconocidos, redondeo, inválidos, pagos internos/externos/mixtos, línea libre, inicialización, duplicados, rollback, concurrencia, páginas y reintento tras cierre. Compras confirmadas no se editan ni anulan en la pantalla actual. Gastos documentados completos y crédito quedan fuera del proceso implementado.

## Notas de devolución y cambios con liquidación real

La regla confirmada es 100 de valor original, 90 reconocidos y 10 no devueltos. CorreccionVenta conserva los tres importes y el vínculo con la operación. NoDevuelto = valorOriginal - reconocido. Esa diferencia no se transforma automáticamente en ganancia; esta permanece manual.

### Cantidades precio histórico y Stock

DevolucionItem valida posición, cantidad y reingreso solicitado. PendienteDevolucion muestra las unidades restantes y datos del ítem original. CorreccionVentaService impide devolver acumuladamente más de lo vendido y duplicar la misma línea en una nota. El máximo se calcula con precio histórico y redondeo acumulado por línea para no crear céntimos en devoluciones fraccionadas.

Dinero y mercancía se deciden por separado: reingresar solo material recibido y vendible. Un libre no mueve Stock automáticamente. Un SKU válido sin saldo previo puede inicializarse y recibir el retorno dentro de la transacción. Los estados distinguen devolución/cambio parcial y total; lo no reconocido no crea deuda nueva.

### Cambio y movimiento de dinero

| Crédito reconocido | Reemplazos | Flujo real |
| --- | --- | --- |
| 90 | 120 | Cobro adicional 30. |
| 90 | 60 | Reintegro 30. |
| 90 | 90 | Sin movimiento monetario. |

El reemplazo es una operación vinculada, cubierta por crédito más o menos diferencia. No se simula un cobro íntegro ni una devolución que no ocurrió. La corrección necesita turno abierto aunque no haya diferencia. Conserva cierre y operación anteriores; el movimiento nuevo afecta al turno actual.

### Integridad e interfaz

La migración 12 agrega notas, cantidades y pagos con referencias. Nota única y documento de reemplazo único impiden duplicados. Cantidades, documento, dinero, Stock y estados confirman o revierten juntos. Reintentos equivalentes se recuperan incluso tras cierre; se rechaza contenido distinto. Una anulación completa previa no puede saltarse las notas existentes.

CorreccionesView reúne búsqueda, líneas retornables, cálculo, confirmación e historial; PagosForm muestra recibido solo para adicional en efectivo. Se conservan solicitud y turno de confirmación tras error. Trece pruebas PostgreSQL cubren 100/90, parciales, no vendibles, diferencias positiva/negativa/cero, rollback tardío, duplicados, concurrencia, redondeo y separación entregada. La nota es interna consultable, sin impresión ni emisión electrónica.

## Respaldo manual y recuperación en base distinta

### Decisión y alcance

El usuario eligió copias solo manuales, carpeta elegida por él y restauración en otra base vacía. RespaldoService restringe a localhost o 127.0.0.1; RespaldoView rechaza opciones especiales en URL. No hay copia de red implementada, retención automática ni reemplazo de la base original.

crear valida carpeta existente, escribe un temporal, ejecuta pg_dump custom del esquema con no-owner/no-acl, comprueba lectura mediante pg_restore --list y mueve al nombre final único. El esquema incluye tablas, datos, funciones y migraciones, no roles ni configuración global. Un archivo legible no demuestra por sí solo recuperación válida.

### Restaurar y preservar el origen

restaurar exige archivo no vacío y base ya existente distinta. Consulta si es plantilla o contiene esquemas u objetos de usuario. pg_restore usa exit-on-error, single-transaction, no-owner y no-acl; no se emplean opciones de limpieza. Debe impedirse que otros usuarios escriban en el destino durante la comprobación y recuperación.

Las herramientas deben ser compatibles con servidor y archivo. Se resuelven bajo la carpeta bin indicada y se ejecutan sin shell, con argumentos separados. La contraseña va por entorno PGPASSWORD y no por línea de comandos. Hay plazo de conexión de 10 segundos y de proceso de 120; salida y error se descartan y el mensaje al usuario es genérico.

La copia no está cifrada por la aplicación ni autentica su origen; un dump PostgreSQL puede ejecutar instrucciones. Usar archivos confiables y ubicaciones protegidas. La cuenta de laboratorio tiene más permisos que una cuenta operativa futura; no trasladar esa configuración a producción sin revisar privilegios.

### Interfaz y prueba

RespaldoView ofrece carpeta de herramientas, copia, archivo y base destino; ejecuta tareas fuera del hilo de interfaz. Desactiva botones de proceso hasta terminar. Después de restaurar, una nueva conexión debe usar base destino y el nombre original del esquema.

La prueba ampliada recuperó catálogo, físico, reservado, reserva, venta y cierre en otra base temporal y verificó migraciones. Rechazó base original, destino ocupado, plantilla, corrupto y servidor remoto. Se repitió después de la suite completa; no es certificación del hardware, políticas o permisos del negocio. Se revisó además la presentación del formulario y se ajustaron dos anchos.

## Migraciones pruebas y lectura correcta de resultados

| Versiones | Propósito |
| --- | --- |
| 1 a 3 | Catálogo base, Stock, mínimos e historial. |
| 4 y 5 | Turnos, movimientos, cierre y conciliación digital. |
| 6 y 7 | Operaciones, ítems, documentos, solicitudes y anulación completa. |
| 8 | Listas independientes, características JSONB e historial de opciones. |
| 9 | Ganancia manual por documento y auditoría. |
| 10 | Índice y consultas de Stock al corte. |
| 11 | Compras, detalles y pagos transaccionales. |
| 12 | Notas de devolución/cambio, cantidades y pagos vinculados. |

MigracionesPostgres aplica versiones transaccionalmente y verifica hashes. Acepta variantes LF/CRLF del mismo SQL para compatibilidad con Git; una modificación real sigue siendo error. No sustituye hashes registrados. PreparacionBeta lo ejecuta al conectar el esquema de pruebas; no borrar tablas ni editar una migración aplicada para resolver incompatibilidades.

### Por qué se prueba de esta manera

Unitarias: comprueban cálculos exactos, validaciones, saldos, cierre, reintentos y errores rápidos sin servidor. Contratos: repiten expectativas de Catálogo en memoria y PostgreSQL. Integración: verifica SQL real, restricciones, rollback tardío, concurrencia, versiones, documentos y flujos cruzados. Recuperación: demuestra relectura en otra base. Las seis pruebas de seguridad adicionales complementan esos casos concretos.

La secuencia reproducible de 1.000 operaciones comprueba invariantes e historial con cálculo independiente. Reintentos concurrentes y despacho competitivo detectan duplicaciones. Los dobles JDBC inspeccionan parámetros pero no ejecutan PostgreSQL. Por eso no sustituyen la integración real ni se interpretan como prueba de penetración global.

### Reproducibilidad y límites

mvn test usa las versiones del pom. Para integración se habilita STOCK_TEST_DB=true y variables STOCK_TEST_URL, STOCK_TEST_USER y STOCK_TEST_PASSWORD de una base desechable. STOCK_TEST_PGBIN apunta al binario PostgreSQL para copia/recuperación. Los casos crean y eliminan esquemas y una base temporal: no ejecutar contra el negocio.

No sumar recuentos históricos ni confundir una prueba con 1.000 pasos con 1.000 casos. El empaquetador usa skipTests para no repetir lo ya ejecutado, por lo que construir un ZIP no acredita la suite. No hay cifras de cobertura o throughput de producción que permitan afirmar capacidad o seguridad total.

## Windows instalación local y expansión

### Paquete y requisitos

tools/Construir-Beta.ps1 prepara JAR y dependencias, usa Lanzador y jpackage app-image y genera CuadreYStockBeta con .exe, app y runtime. El pom conserva 0.1.0-SNAPSHOT como versión de artefacto; el parámetro Version identifica el paquete 0.1.4. El usuario final no necesita JDK de desarrollo ni Maven, pero sí PostgreSQL y pg_dump/pg_restore para las copias.

Windows 11 x64 es el objetivo recomendado. La matriz Oracle JDK 21 lo incluye y sitúa Windows 10 x64 como sistema ya no soportado por el proveedor. Eso no determina por sí solo si el paquete con su runtime abrirá en Windows 10: debe ensayarse la entrega exacta en el equipo del negocio. No se declara compatibilidad con 32 bits o Windows ARM. [W1]

El instalador PostgreSQL 17 publica como plataformas probadas Windows Server 2019 y 2022 y espera funcionamiento en versiones de escritorio comparables. Confirmar la versión local y sus permisos. Como base práctica no medida se proponen CPU x64 de dos núcleos, 8 GB RAM, SSD, 10 GB libres más crecimiento y pantalla suficiente para los formularios; las revisiones de presentación se hicieron a 1200 por 790. [W2]

### Por qué local y qué falta para red

Una computadora permite validar Surquillo con menos dependencias y conserva el motor PostgreSQL previsto para expansión. Datos en memoria solo son de pruebas y se pierden al terminar; PostgreSQL local es persistente. Local no protege por sí mismo contra pérdida del equipo: copias y restauración son controles distintos.

La arquitectura futura propuesta es cliente JavaFX, servicio central Java y PostgreSQL compartido con identidad y permisos por sede. Hoy no hay API servidor ni aislamiento completo multisede. Cambiar APP_DB_URL no resuelve esas responsabilidades. El respaldo actual rechaza servidor remoto: ese flujo deberá ampliarse por separado.

Antes de conectar locales hacen falta protección y verificación de conexiones, cuentas restringidas, auditoría, monitoreo, traslados y pruebas de fallos de red. La aplicación no tiene sincronización offline. Ante respuesta incierta hay que consultar por identificador; no asumir confirmación ni recobrar. Medir bloqueos globales y consultas de turnos extensos antes de optimizar.

## Utilidad medida y plan de aceptación

### Comparación con el cuaderno

La línea base confirmada por el usuario es 20 minutos o más por día/cierre para ventas, gastos e ingresos y dificultad de búsqueda en cuadernos. Es estimación autodeclarada, sin muestra cronometrada ni volumen asociado. No hay un tiempo equivalente con la 0.1.4 que permita afirmar ahorro real.

| Escenario sobre 20 min | Ahorro diario | Reducción | En 26 días |
| --- | --- | --- | --- |
| App 15 min | 5 min | 25 % | 130 min |
| App 10 min | 10 min | 50 % | 260 min |
| App 5 min | 15 min | 75 % | 390 min |

Ahorro = Tmanual - Tapp; reducción = 100 × ahorro / Tmanual. Veintiséis jornadas y cada Tapp son supuestos. Medir al menos diez jornadas y todo el tiempo de captura y revisión, separar aprendizaje y alta inicial, registrar volumen, búsquedas fallidas, omisiones y correcciones. Comparar mediana y percentiles junto a muestra, no solo el mejor cierre.

### Pendientes y finalidad de cada siguiente acción

1. Aceptación del 14 de noviembre. Ejecutar la guía con datos ficticios en el equipo del trabajo, registrar resultados y reinicio; comprobar que los controles de ventana y los formularios se vean completos. Esto acredita el circuito empaquetado y la facilidad de uso, que una prueba de servicio no demuestra.

2. Reportes .xls. Definir el proceso diario/mensual, filtros, hojas y cortes antes de implementarlo. Conciliar totales y conservar SKU como texto, valores numéricos y formatos reales. HSSF sigue propuesta; no se debe anunciar exportación disponible.

3. Producción. Definir usuario limitado, permisos, instalación, actualización, respaldo protegido y recuperación en el entorno definitivo. El hecho de poder crear esquemas con una cuenta de laboratorio no valida privilegios operativos.

4. Coherencia documental. CAJA y GUIA-PRUEBAS conservan párrafos de etapas anteriores; los documentos de cada bloque y PLAN-ACTUAL describen el avance. Consolidar listas históricas sin borrar evidencia, y distinguir componente, revisión visual y aceptación.

5. Expansión. Implementar servicio central e identidad por sede cuando se valide el piloto. La IA puede ayudar a proponer código y escenarios, pero las reglas, diffs y resultados requieren revisión independiente. La aplicación actual no incorpora SDK ni inferencia de IA para decidir operaciones.

## Fuentes de procesos y evidencia de entrega

Fuentes del proyecto Cuadre y Stock consultadas para esta actualización: README, README-PRUEBAS-SEGURIDAD y docs/ESTADO-Y-OPERACION, PLAN-ACTUAL, CATALOGO, STOCK-CONSULTAS, COMPRAS, DEVOLUCIONES-Y-CAMBIOS, RESPALDOS, CAJA, VENTAS, CLASES, BETA-ACEPTACION y GUIA-PRUEBAS. La revisión final del plan se registra en docs/REVISION-ENTREGA-014.md.

Contraste con código: Aplicacion, PreparacionBeta, SesionBeta, servicios de Catálogo, Stock, Caja, ventas, compras, correcciones y respaldos; repositorios y vistas de esos módulos; doce migraciones SQL y herramientas de empaquetado. Las fuentes justifican los procesos vigentes y permiten distinguir reglas implementadas de textos históricos.

Los resultados de suite completa, prueba ampliada de recuperación y seis ensayos adicionales se conservan como ejecuciones separadas. La entrega informa sus alcances sin convertirlos en prueba exhaustiva de todos los fallos, seguridad o rendimiento. Los resultados manuales del 14 de noviembre deben registrarse después de ejecutarlos.

### Referencias técnicas externas

[W1] Oracle JDK 21 Certified System Configurations. https://www.oracle.com/java/technologies/javase/products-doc-jdk21certconfig.html

[W2] PostgreSQL Windows Installers. https://www.postgresql.org/download/windows/

Consultadas el 13 de noviembre de 2026. Las matrices de plataforma sustentan objetivos y límites de soporte; no certifican la aplicación ni el equipo del negocio.

### Uso de los documentos

La guía de usuario contiene acciones de pantalla y resultados esperados; este informe explica procesos, componentes, razones y pruebas. El correo de entrega debe incluir el vínculo verificado al paquete 0.1.4 y no el de la versión 0.1.2. La carpeta app y runtime debe conservarse junto al ejecutable al extraer el ZIP.
