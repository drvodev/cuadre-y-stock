# Pruebas de seguridad y resultados

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
