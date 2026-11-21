# Cuadre y Stock

> **Rama de trabajo:** beta 0.1.6 con mejoras del issue 1, aún en revisión. El instalador opcional de PostgreSQL requiere prueba en una instalación limpia y las imágenes de catálogo continúan pendientes. Ver [alcance y estado de la propuesta](docs/ISSUE-1-MEJORAS.md). Estos cambios no están fusionados en `main`.

**Inventario y caja para llevar el control diario de una tienda desde una sola aplicación.** Registra productos, compras y ventas; consulta lo disponible y cierra el turno con el efectivo y los medios de pago separados.

El proyecto nace de una necesidad real en Surquillo: encontrar las operaciones sin depender de varios cuadernos y reducir el trabajo manual del cuadre. Está desarrollado para Windows con Java, JavaFX y PostgreSQL.

> **Estado: beta 0.1.6 en revisión.** El circuito principal está implementado y disponible para pruebas con datos ficticios. Aún falta la aceptación en el equipo del negocio y preparar la instalación definitiva antes de usar información real.

## ¿Qué puedes hacer?

- **Catálogo:** crear y buscar artículos por SKU, organizar categorías, marcas, unidades y presentaciones, y registrar costo y precio sugerido.
- **Stock:** consultar existencias físicas, reservadas y disponibles; registrar entradas y ajustes, revisar movimientos y detectar mínimos o productos agotados. La venta puede registrarse aunque el stock figure en cero, sin dejar cantidades negativas.
- **Ventas y separaciones:** registrar boletas, facturas y notas de uso interno; combinar medios de pago, recibir abonos y entregar una separación cuando esté pagada y haya existencias disponibles.
- **Compras:** registrar juntos el documento, la mercancía recibida y el pago, para evitar anotar la misma entrada o salida de dinero dos veces.
- **Caja:** abrir y cerrar turnos, registrar gastos y retiros, contar efectivo y revisar los medios digitales por separado. Los sueldos pagados en efectivo se registran si el saldo esperado del turno es suficiente.
- **Devoluciones y cambios:** vincular una nota a la operación original, registrar la cantidad y el importe acordado y reflejar únicamente el dinero que entra o sale.
- **Reportes y respaldos:** exportar reportes diarios o mensuales en Excel `.xls` y crear copias manuales para recuperarlas en otra base vacía.

Los comprobantes registrados son para **control interno**; la aplicación no emite comprobantes electrónicos a SUNAT. El pago de sueldos es un registro manual, no un cálculo de planilla.

## Prueba la beta

[**Descargar Cuadre y Stock 0.1.6 para Windows**](https://github.com/drvodev/cuadre-y-stock/releases/tag/beta-pruebas-0.1.6)

1. Descarga `Cuadre-y-Stock-Beta-Pruebas-0.1.6.zip` y extrae **todo** su contenido.
2. Abre `CuadreYStockBeta.exe` conservando las carpetas `app` y `runtime` junto al ejecutable. Java está incluido.
3. Conecta con PostgreSQL instalado en el equipo y usa un esquema nuevo para la prueba. Lee el archivo `LEEME-PRIMERO.txt` incluido.
4. Prueba el recorrido con productos, pagos y clientes ficticios. Guarda las incidencias con los pasos que las producen.

La aplicación se está preparando para un equipo local. Se debe comprobar la beta en el Windows del negocio, incluidos sus permisos, pantallas y proceso de recuperación. Antes de actualizar una base que ya contenga datos, crea una copia y comprueba su restauración.

## Calidad y límites conocidos

La suite del **19 de noviembre de 2026** ejecutó **117 pruebas sin fallos ni omisiones**, con PostgreSQL 17 aislado. Cubrió reglas de caja y stock, operaciones, compras, correcciones, reportes, concurrencia y respaldo/recuperación. El ejecutable 0.1.5 abrió hasta la pantalla de conexión; las pantallas nuevas y el recorrido completo todavía requieren aceptación manual en el equipo de trabajo.

Para la rama 0.1.6, la suite definitiva ejecutó 127 pruebas sin fallos ni omisiones con PostgreSQL aislado. El agente de pruebas verificó además de forma independiente los reintentos y la migración de SKU. El ejecutable final 0.1.6 abrió hasta la pantalla de conexión; se requiere revisión de los formularios nuevos.

Los reportes calculan el stock al final del período elegido. El historial de estado y abonos se registra desde la migración 13; para fechas anteriores sin ese historial el reporte indica **«no disponible»**. Las copias y los reportes contienen datos del negocio y la aplicación no los cifra.

## ¿Qué queda?

1. **Validar la beta en el negocio:** recorrer catálogo, stock, ventas, separaciones, compras, caja, sueldos, devoluciones, reportes y reinicio con datos ficticios. Revisar diseño y facilidad de uso con el encargado.
2. **Preparar el uso real:** comprobar Windows y PostgreSQL del equipo, configurar una cuenta con permisos adecuados y ensayar copia, restauración y actualización en ese entorno. Una cuenta limitada ya se probó en laboratorio; falta verificar la recuperación completa con ella.
3. **Cerrar las mejoras funcionales pendientes:** definir y desarrollar el manejo de gastos con documento y la consulta comercial general paginada; decidir cómo corregir compras confirmadas sin perder su historial.
4. **Revisar capacidad y evolución:** medir el comportamiento con el volumen real de artículos y operaciones. El acceso entre sedes, usuarios y conexión segura en red queda para una etapa posterior.

El seguimiento detallado está en [plan actual](docs/PLAN-ACTUAL.md) y [condiciones de aceptación](docs/BETA-ACEPTACION.md).

## Para desarrollo

El proyecto usa **Java 21 o superior, JavaFX, Maven y PostgreSQL**. Abre la carpeta o `pom.xml` en IntelliJ IDEA y carga el proyecto Maven.

```bash
mvn test
```

Las pruebas de integración se omiten si no se configura `STOCK_TEST_DB=true`; usan una base desechable y crean y eliminan esquemas de prueba. La prueba de recuperación también necesita `STOCK_TEST_PGBIN`. Consulta [estado y operación](docs/ESTADO-Y-OPERACION.md) antes de ejecutarlas y conserva las credenciales fuera del repositorio.

Para empaquetar después de ejecutar las pruebas, usa `tools/Construir-Beta.ps1` con Maven y un JDK que incluya `jpackage`. El empaquetador no repite las pruebas.

## Documentación

- [Reportes y pago de sueldos](docs/REPORTES-Y-SUELDOS.md)
- [Preparación de PostgreSQL y cuenta limitada](docs/PREPARACION-POSTGRESQL.md)
- [Copias manuales y recuperación](docs/RESPALDOS.md)
- [Catálogo](docs/CATALOGO.md) · [Stock](docs/STOCK-CONSULTAS.md) · [Compras](docs/COMPRAS.md) · [Devoluciones y cambios](docs/DEVOLUCIONES-Y-CAMBIOS.md)
- [Clases y responsabilidades](docs/CLASES.md) · [Pruebas y seguridad](README-PRUEBAS-SEGURIDAD.md)
- [Guía de usuario 0.1.4](docs/GUIA-USUARIO-014.md): útil para el circuito anterior; los reportes y sueldos de 0.1.5 se explican en su documento específico.
