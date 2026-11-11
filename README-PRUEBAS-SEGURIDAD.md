# Pruebas de seguridad y resultados

## Primera lógica de Caja

Se añadieron cinco pruebas de Caja. La ejecución Maven posterior al cambio encontró 43 pruebas: 37 exitosas, cero fallos y seis de integración PostgreSQL omitidas al no habilitar la base de prueba. La lógica de Caja usa un repositorio en memoria; los resultados no acreditan persistencia, recuperación, integración comercial ni uso de dinero real. Ver docs/CAJA.md.

## Estado actual, 10 de noviembre de 2026

La última suite Maven completa pasó 38 pruebas, con seis pruebas de integración en PostgreSQL 17 aislado. Se comprobaron transacciones de Stock, reservas, ventas concurrentes, rollback real, consultas parametrizadas, control de versiones de artículos, mínimos persistentes, historial de catálogo y migraciones repetidas o alteradas. Las pruebas anteriores detalladas más abajo conservan su alcance histórico; la indicación de que no se probó PostgreSQL corresponde a esas ejecuciones anteriores.

Las credenciales temporales de integración se suministran por entorno, fuera del repositorio. La instancia de prueba está separada de la instalación principal. Persisten pendientes de seguridad y operación: permisos de una cuenta de aplicación real, recuperación de respaldos y coordinación de Caja con Stock. Estas pruebas no certifican la seguridad global del producto. Ver docs/ESTADO-Y-OPERACION.md para reproducibilidad y límites.

## Alcance

Se revisó `StockPostgresRepository` y se añadieron pruebas en `src/test/java/com/surquillo/inventariocaja/repositorio/StockPostgresSqlInjectionTest.java`. La base de datos es un doble simulado de JDBC: registra las consultas preparadas y sus parámetros, devuelve resultados vacíos y simula escrituras exitosas. No se conecta a PostgreSQL ni necesita datos reales.

## Procedimiento

1. Las pruebas envían cadenas con comillas, condiciones `OR`, `UNION` y un intento de `DROP TABLE` en SKU y almacén.
2. Comprueban que las búsquedas de saldos e historial mantienen sus marcadores de parámetros y que los valores llegan por separado.
3. Registran un stock inicial con una carga similar en SKU, almacén y referencia, y comprueban los parámetros de las inserciones en saldo y movimientos.
4. Se revisan las consultas de la implementación: las entradas se suministran mediante `PreparedStatement` y no se observó concatenación de entradas en las sentencias revisadas.

Para ejecutar la suite con las dependencias del proyecto disponibles, desde esta carpeta:

```shell
mvn test
```

## Resultado de la revisión

El 10 de noviembre de 2026 se compilaron las clases no gráficas y las pruebas desde las fuentes actuales con JDK 25 y JUnit 6.0.3 disponible localmente. Se ejecutaron **13 pruebas, 13 exitosas**, incluidas las dos pruebas nuevas de inyección con simulación. Esta ejecución alternativa no valida la ventana JavaFX ni reproduce exactamente las versiones del `pom.xml`.

Se intentó `mvn -o test`, pero Maven no pudo resolver sin conexión `maven-surefire-plugin:3.5.2`. Para validar el ciclo oficial se requiere disponer de las dependencias declaradas y ejecutar `mvn test`.

## Límites y pasos pendientes

- La simulación verifica la separación entre SQL y parámetros en los recorridos probados; no interpreta SQL ni demuestra cómo se comportan PostgreSQL y su controlador.
- No se probaron los flujos de reserva, reintentos con registros existentes ni todos los campos de escritura con cargas de inyección. Tampoco se ha hecho una prueba de penetración integral.
- En una base PostgreSQL aislada y desechable, aplicar las migraciones correspondientes y repetir las pruebas con datos de prueba, verificando resultados y tablas intactas. Añadir casos para reservas, recuperación, concurrencia, rollback y reintentos. No usar una base de producción para estas pruebas.
- La aplicación sigue en desarrollo; los resultados no certifican la seguridad global ni validan datos reales.

## Pruebas de resistencia adicionales

En una ejecución posterior del 10 de noviembre de 2026 se compilaron los modelos, servicios, repositorios, excepciones y pruebas con JDK 25 y JUnit 6.0.3. Resultado: **26 pruebas ejecutadas y 26 exitosas**. Esta ejecución excluyó la interfaz gráfica y no sustituyó el ciclo Maven del proyecto.

Se añadieron pruebas con 60 reintentos concurrentes de una misma entrada, 40 intentos concurrentes de despacho con solicitudes diferentes, tres rechazos repetidos de autorización y una falla simulada durante la inserción del movimiento. Se comprobó que en memoria no se duplica la entrada ni el despacho, que el rechazo conserva el saldo y el historial, y que el repositorio PostgreSQL solicita `rollback` y no `commit` ante la excepción simulada.

Las pruebas concurrentes usan únicamente el repositorio en memoria. La prueba del fallo de PostgreSQL simula JDBC: confirma las llamadas realizadas, no que un servidor haya revertido realmente los datos. Los límites y pasos pendientes anteriores siguen vigentes.

## Verificación adicional de invariantes

Se incorporó una prueba reproducible de 1.000 operaciones de entrada, venta y ajuste con cantidades válidas y negativas o cero. En cada paso contrasta el saldo con un cálculo independiente, verifica que no sea negativo y que las solicitudes rechazadas no agreguen movimientos. Al final comprueba que la suma de cambios y la continuidad del historial coincidan con el saldo.

En la siguiente ejecución, modelos, controladores, servicios, repositorios, excepciones y pruebas se compilaron con JDK 25 y JUnit 6.0.3: **30 pruebas ejecutadas y 30 exitosas**. La prueba de invariantes usa almacenamiento en memoria y no cubre JavaFX, Maven, el comportamiento real de PostgreSQL ni los módulos que aún no están implementados.

## Revisión posterior a las migraciones

Tras la entrega `e1bed30`, se ejecutó el ciclo Maven del proyecto con las dependencias declaradas, usando la caché local: **32 pruebas pasaron y 6 de integración con PostgreSQL se omitieron**. Maven compiló también los componentes gráficos; no se verificó visualmente su funcionamiento. La suite ejecutada incluye las pruebas de SQL simulado, fallos, concurrencia en memoria y la secuencia de 1.000 operaciones.

Se intentó preparar una instancia PostgreSQL aislada para repetir las seis pruebas de integración, pero la inicialización quedó sin terminar y se interrumpió; no quedó un servidor iniciado. Por ello esta revisión no las ejecutó de forma independiente. El agente que implementó el cambio informó de 38 pruebas exitosas, incluidas esas seis con PostgreSQL 17; ese resultado está descrito en `docs/ESTADO-Y-OPERACION.md`.

La configuración de conexión acepta una URL JDBC sin exigir cifrado ni verificación del servidor. Antes de conectar equipos por red, definir y comprobar una configuración TLS con verificación del servidor y los permisos de la cuenta; las pruebas existentes no lo validan. La integración de Caja y pago con el despacho sigue pendiente, por lo que no debe interpretarse la autorización simulada como una garantía de pago real.
