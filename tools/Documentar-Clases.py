from pathlib import Path
import re

root=Path(__file__).resolve().parents[1]
motivos={
'BarraVentana':'Presenta Minimizar, Maximizar/Restaurar y Cerrar; mantiene el texto sincronizado con el estado de la ventana y solicita el cierre normal. Compartida por conexión y pantalla principal; validación visual pendiente en 0.1.2.',
'Lanzador':'Punto de entrada del paquete Windows; inicia JavaFX sin depender del lanzador especial de Application.',
'PreparacionBeta':'Valida el nombre del esquema de pruebas, lo crea sin borrar tablas y aplica las migraciones antes de abrir la sesión.',
'DatosDemostracionService':'Carga cinco artículos DEMO y existencias únicamente cuando no existen, dentro de una transacción compartida.',
'SesionBeta':'Compone servicios y repositorios PostgreSQL y conserva el turno seleccionado durante la sesión visual.',
'CatalogoView':'Permite crear y editar artículos, buscar y avanzar por páginas. Conserva unidad y precisión al editar.',
'CajaView':'Presenta apertura, selección de turnos, movimientos manuales, conteo y verificación digital; confirma cierres mediante el servicio.',
'VentaView':'Construye ítems y pagos, solicita confirmación de faltantes y registra ventas o separaciones con identificadores de reintento conservados.',
'OperacionesView':'Consulta documentos y últimas operaciones; permite abonos, entrega y anulación completa con los servicios existentes.',
'PagosForm':'Recoge importes positivos por medio de pago y efectivo recibido; no persiste ni confirma dinero por sí solo.',
'Ui':'Reúne formularios, tablas, avisos y conversión de números para las pantallas de prueba.',
'VentaService':'Coordina documentos, cobros, ítems, reservas y stock dentro de una unidad de trabajo. Registra ventas, abonos, entrega y anulación completa, y consulta operaciones y correlativos.',
'DocumentoService':'Sugiere el siguiente número conservando prefijo y ceros; no obliga a completar secuencias ni reemplaza el número manual.',
'Documento':'Representa un comprobante interno, su tipo, número normalizado y origen manual o sugerido.',
'ItemOperacion':'Conserva la descripción, precio, unidad e indicaciones históricos del ítem; valida cantidad y calcula el total redondeado.',
'Operacion':'Representa una venta o separación con sus ítems, pagos acumulados y estado; expone el saldo vigente.',
'Aplicacion':'Presenta conexión PostgreSQL y compone las cinco pestañas de la beta de pruebas. Prepara el esquema y permite carga DEMO opcional.',
'CajaService':'Aplica apertura, cobros, vuelto, egresos, cuadre y conciliación digital. Valida importes y versiones antes de cerrar.',
'TurnoPostgresRepository':'Guarda turnos, movimientos, líneas y cierres en PostgreSQL. Reconstruye el resultado original de solicitudes repetidas y revierte escrituras fallidas.',
'TurnoMemoriaRepository':'Prueba las reglas de Caja con transacciones sincronizadas en un proceso; no conserva datos al reiniciar.',
'UnidadTrabajoPostgres':'Comparte una conexión durante una operación para confirmar o revertir varios repositorios juntos. No es por sí sola un flujo comercial completo.',
'ConexionPostgres':'Obtiene conexiones JDBC con configuración externa, sin guardar contraseñas en el código.',
'MigracionesPostgres':'Aplica versiones SQL transaccionalmente y comprueba los archivos ya aplicados mediante SHA-256.',
'StockService':'Realiza cálculos inmutables de entradas, ventas, ajustes y reservas, con cantidades exactas y límite mínimo de cero.',
'InventarioService':'Coordina saldos y movimientos con el repositorio, incluyendo UUID de solicitud y mínimos por almacén.',
'ReservaService':'Vincula reservas a separaciones, propone cantidades disponibles y gestiona ampliación, cancelación y despacho. El pago todavía requiere integración comercial.',
'CatalogoService':'Valida solicitudes de catálogo y expone creación, actualización, consultas, páginas, opciones e historial.',
'ProductoPostgresRepository':'Persiste variantes y consulta catálogo, listas e historial. Usa parámetros, versiones y protección de la unidad de stock.',
'ProductoMemoriaRepository':'Reproduce el contrato del catálogo para pruebas sin base de datos; conserva historial dentro del proceso.',
'StockPostgresRepository':'Guarda saldo, movimiento y reserva de forma transaccional, con deduplicación, mínimos y consultas de historial.',
'StockMemoriaRepository':'Implementa transacciones e idempotencia de Stock sincronizadas para pruebas en memoria.',
'StockController':'Conecta el catálogo con operaciones manuales de Stock y respeta el estado y la precisión del artículo.',
'SeparacionController':'Separa la propuesta de reserva de su confirmación para conservar los argumentos de reintento.',
'StockView':'Pantalla conectada de consulta, entradas, ajustes y mínimos. Se comprobó visualmente la consulta de stock y su descuento tras una venta.',
'ConfirmacionReservaView':'Prepara un diálogo de faltante y confirmación de unidades disponibles; no guarda datos.',
'SaldoStock':'Mantiene físico, reservado y disponible; valida SKU, almacén y precisión de cantidades.',
'Variante':'Representa un artículo, su presentación y valores opcionales. Valida precio, costo, contenido y unidad.',
'ClaveStock':'Identifica SKU y almacén; normaliza el SKU para que los saldos no se dupliquen por mayúsculas o espacios externos.',
'Reserva':'Representa una propuesta sin efectos persistentes e informa el faltante.',
'ReservaOperacion':'Conserva la identidad, separación, cantidades y estado de una reserva.',
'MovimientoStock':'Conserva el tipo y el cambio físico real de una operación, incluso si la cantidad vendida superó el disponible.',
'CambioCatalogo':'Expone una versión histórica tipada de un artículo.',
'Turno':'Conserva la identidad de la caja, fecha operativa, encargado, fondo, versión y estado.',
'MovimientoCaja':'Representa un evento monetario con fuente, referencia e importes aplicados por medio.',
'Cierre':'Conserva los resultados del conteo y las verificaciones digitales al confirmar el cierre.',
'DetalleCierre':'Compara valores esperados y verificados de un medio digital sin confundir categorías.',
'VerificacionMedio':'Distingue cobros, devoluciones, egresos y otros ingresos, con importes no negativos y exactos.',
'MedioPago':'Define los seis medios de pago acordados en el plan.',
}
motivos.update({'ListaCatalogo': 'Define categorías, marcas, unidades y presentaciones con sus nombres de pantalla.', 'OpcionCatalogo': 'Conserva identidad, nombre normalizado, estado y versión para administrar listas independientes.', 'CambioOpcionCatalogo': 'Expone una versión histórica de una opción y la fecha de su cambio.', 'AtributosCatalogo': 'Valida, normaliza y copia características descriptivas en un mapa inmutable.', 'FiltroCatalogo': 'Agrupa condiciones combinadas, estado y características sin cargar todo el catálogo.', 'ListasCatalogoPostgres': 'Guarda y consulta opciones, comprueba ediciones por versión y recupera su auditoría.', 'ListasCatalogoView': 'Permite buscar, paginar, crear, renombrar, desactivar y reactivar opciones sin tener artículos.', 'AtributosForm': 'Recoge y valida pares descriptivos en artículos y filtros, evitando perder un par pendiente.', 'CatalogoView': 'Presenta una lista por descripción y categoría; conecta filtros, listas y edición manual con características.', 'CatalogoService': 'Valida páginas y cursores y expone artículos, filtros, listas independientes e historiales.', 'ProductoPostgresRepository': 'Persiste artículos y atributos JSONB, consulta filtros parametrizados y recupera versiones históricas.', 'ProductoMemoriaRepository': 'Aplica el contrato de artículos, listas y filtros en memoria para las pruebas.', 'MigracionesPostgres': 'Aplica versiones transaccionales, verifica SQL previo y admite exclusivamente diferencias LF/CRLF.'})
motivos.update({'Compra': 'Conserva documento, proveedor, recepción y pago completo; valida importes y costos conocidos sin convertir desconocidos en cero.', 'DetalleCompra': 'Valida cantidades y costo unitario opcional y calcula subtotales exactos con HALF_UP.', 'CompraRepository': 'Contrato para guardar la compra, recuperar su solicitud, consultar y paginar resúmenes.', 'CompraPostgresRepository': 'Guarda cabecera, ítems, pagos y solicitud en la unidad comercial y recupera la compra confirmada.', 'CompraService': 'Coordina documento, recepción y pago íntegro; recupera reintentos, inicializa stock si falta y revierte fallos.', 'CompraController': 'Expone registrar, consultar y listar compras a las pantallas mediante el servicio.', 'CompraView': 'Recoge documento, recepción y pago; conserva UUID tras errores y permite consultar el historial.', 'ListaStockRepository': 'Consulta por almacén artículos, disponibilidad, precios manuales y filtros de mínimos, agotados o sin iniciar.', 'ListaStockView': 'Muestra cinco columnas, carga páginas, edita costo/precio conservando atributos y abre movimientos.', 'MovimientoPaginado': 'Asocia un movimiento con su secuencia estable para consultar páginas.', 'InventarioService': 'Coordina saldos y movimientos; valida páginas y expone físico/reservado al corte exclusivo.', 'StockPostgresRepository': 'Persiste operaciones idempotentes y consulta saldos actuales, históricos y movimientos paginados.', 'StockMemoriaRepository': 'Aplica las reglas en memoria y conserva instantáneas posteriores para probar consultas al corte.', 'StockView': 'Consulta y modifica stock, carga movimientos por páginas y presenta saldos al cierre del día.', 'LibroCajaRepository': 'Consulta documentos históricos del turno y guarda ganancia manual con auditoría y comparación del importe previo.', 'CajaView': 'Presenta libro de cuatro columnas, abonos, gastos y sueldos; conserva apertura, conteo y cierre por servicios.'})
lineas=['# Inventario de clases y responsabilidades','',
'Documento generado desde las fuentes actuales. Se describen las clases implementadas y se identifican las estructuras pendientes. La existencia de un archivo no prueba un flujo completo ni autoriza el uso con datos reales.','',
'Cada fila indica para qué se usa la clase, la acción implementada y sus puntos de entrada principales. Las pruebas se describen como evidencia de su alcance, no como garantía total del producto.','',
'| Clase | Estado | Por qué, para qué y acciones | Funciones principales |','|---|---|---|---|']
for p in sorted((root/'src').rglob('*.java')):
    texto=p.read_text(encoding='utf-8-sig')
    nombre=p.stem
    vacia=bool(re.search(r'public\s+(?:class|interface)\s+\w+\s*\{\s*\}',texto))
    if vacia:
        estado='Pendiente'; motivo='Estructura inicial sin lógica ni contrato implementados. No está habilitada como función del producto.'
    elif '/test/' in p.as_posix():
        estado='Prueba'; motivo='Verifica los escenarios declarados en sus métodos de prueba. Su alcance queda limitado a esas condiciones y al repositorio configurado.'
    elif 'public interface ' in texto:
        estado='Contrato'; motivo='Define operaciones que los repositorios deben cumplir; los efectos y persistencia corresponden a sus implementaciones.'
    elif nombre.endswith('Exception'):
        estado='Implementada'; motivo='Propaga un error de aplicación conservando mensaje y causa, para que el flujo superior pueda mostrarlo o revertirlo.'
    else:
        estado='Implementada'; motivo=motivos.get(nombre,'Representación o componente tipado utilizado por la lógica; consultar las declaraciones y sus validaciones.')
    metodos=re.findall(r'^\s*(?:@Override\s+)?public\s+(?:(?:static|synchronized|final)\s+)*(?:[\w<>?,.\[\] ]+\s+)?(\w+)\s*\(',texto,re.M)
    metodos=[m for m in dict.fromkeys(metodos) if m!=nombre]
    if '/test/' in p.as_posix():
        metodos=re.findall(r'@Test\s+void\s+(\w+)\(',texto)
    funciones=', '.join('`'+m+'`' for m in metodos) or ('Constructores y campos tipados' if not vacia else 'Ninguna')
    ruta=p.relative_to(root).as_posix()
    lineas.append(f'| [{nombre}](../{ruta}) | {estado} | {motivo} | {funciones} |')
(root/'docs/CLASES.md').write_text('\n'.join(lineas)+'\n',encoding='utf-8')
