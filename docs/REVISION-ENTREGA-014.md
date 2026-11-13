# Revisión de entrega 0.1.4
13 de noviembre de 2026. Prueba del usuario prevista para el 14 de noviembre.

El plan completo todavía no está terminado. La entrega es una beta para comprobar los procesos implementados.

| Área | Evidencia disponible | Pendiente |
|---|---|---|
| Catálogo y listas | Persistencia, filtros, atributos y pruebas; vista agrupada revisada | Aceptación operativa y medición de grandes volúmenes |
| Stock | Reservas, mínimos, cortes históricos y paginación probados; lista revisada | Aceptación con el almacén real |
| Caja, ventas y separaciones | Pruebas transaccionales, reintentos y cierres; libro revisado | Recorrido completo de aceptación del usuario |
| Compras | Recepción/documento/pago coordinados y rollback probados | Prueba operativa de recepción y pago |
| Devoluciones y cambios | Cantidades y valores manuales, diferencias y cierres protegidos; vista revisada | Aceptación de las notas internas en el negocio |
| Respaldos | Copia y restauración reales en base nueva; rechazo de destinos ocupados/corruptos; presentación revisada | Cuenta limitada y recuperación en entorno definitivo |
| Reportes .xls | Requisito identificado | Proceso y exportaciones diarias/mensuales, aún sin implementar |
| Instalación/producción | Java incluido y PostgreSQL externo; ejecutable abierto en este equipo | Comprobar Windows y permisos del equipo del trabajo |

Validación automatizada: suite completa de 102 pruebas, 50 PostgreSQL, y después seis comprobaciones adicionales del agente de seguridad sobre esta versión (dos PostgreSQL). No se ejecutó una suite conjunta de 108 en una única corrida. Las seis cubren datos incorrectos, duplicados, reintentos e inyección en la lista de Stock.

La revisión del ejecutable inicial 0.1.4 confirmó apertura y ventana con respuesta. Después se ajustaron únicamente dos anchos de campos de Respaldos y se reempaquetó. No se distribuyen datos ni contraseñas de laboratorio.

El ZIP debe extraerse completo. Abrir CuadreYStockBeta.exe manteniendo las carpetas app y runtime. El equipo necesita PostgreSQL instalado y herramientas compatibles para las copias. Usar datos ficticios y un esquema nuevo; las versiones antiguas no deben abrir un esquema ya actualizado.

Se conservan sin marcar los criterios de aceptación final que requieren un programa completo y la prueba del usuario. La existencia de clases, pruebas o capturas no acredita el plan entero.
