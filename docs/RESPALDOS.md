# Copias manuales y recuperación

El usuario eligió crear copias solo manualmente en una carpeta elegida por él, y restaurar en una base diferente y vacía.

## Crear una copia

1. Conectar la aplicación al esquema que se desea respaldar.
2. Abrir Respaldos y comprobar la carpeta `bin` de las herramientas PostgreSQL. Puede elegirse otra instalación compatible.
3. Pulsar Crear copia manual y elegir una carpeta existente. Esperar el mensaje de resultado.

Se genera un archivo PostgreSQL de formato personalizado con nombre, fecha e identificador únicos. Incluye el esquema seleccionado, sus tablas, datos, funciones y versiones de migración. La copia se crea mediante pg_dump con una instantánea consistente; no modifica el esquema original. Se comprueba que pg_restore puede leer la lista del archivo antes de darlo por terminado.

La copia contiene información del negocio y **no está cifrada por esta aplicación**. Guárdala en una ubicación protegida y controla quién puede acceder. La contraseña de conexión se pasa al proceso por entorno y no se escribe en el archivo ni en la línea de comandos.

## Recuperar

1. Preparar en el mismo servidor otra base de datos vacía; debe existir antes de continuar. Desconectar a los demás usuarios de esa base.
2. Elegir la copia confiable, escribir el nombre de la base nueva y confirmar Restaurar en base vacía.
3. La aplicación comprueba que el destino es diferente, no es una plantilla PostgreSQL y no tiene esquemas ni objetos de usuario. No usa opciones de borrado.
4. pg_restore ejecuta la recuperación en una sola transacción y detiene la operación ante errores.
5. Al conectar a la base recuperada, indicar el nombre del esquema que tenía la copia. Revisar artículos, stock, operaciones y cierres.

Usa solo archivos de copia de procedencia confiable: el formato PostgreSQL puede contener instrucciones ejecutables y la aplicación no autentica su origen. La verificación de lectura no sustituye una restauración de prueba. La aplicación no crea automáticamente la base de destino ni sustituye la base actual.

La versión de pg_dump debe ser compatible con el servidor de origen; pg_restore debe ser compatible con el archivo y el servidor de destino. El equipo donde se ejecute necesita las herramientas PostgreSQL además de Java incluido en el paquete.

Esta primera versión admite exclusivamente el servidor local (localhost o 127.0.0.1) y una conexión sin opciones adicionales. Las conexiones de red y su configuración de cifrado requieren otra implementación. La copia no incluye roles ni permisos de PostgreSQL ni la configuración global del servidor.

## Clases y pruebas

RespaldoService valida conexiones y destinos, crea y verifica copias, y ejecuta la restauración transaccional. RespaldoView permite elegir carpetas, archivo y base y ejecuta las tareas fuera de la interfaz. RespaldoPostgresTest crea datos ficticios, copia y restaura en una base temporal y compara Catálogo, Stock, reserva, venta y cierre; también comprueba migraciones y el rechazo de base original, destino ocupado, plantilla, archivo corrupto y servidor remoto.

No hay copia automática ni política de borrado o retención. La recuperación en producción y los permisos de una cuenta limitada deben validarse en el entorno definitivo.
