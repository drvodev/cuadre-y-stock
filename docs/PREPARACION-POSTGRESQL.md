# Preparación de PostgreSQL para una cuenta limitada

La aplicación es una beta local y todavía requiere aceptación del usuario en el equipo del negocio. En una base aislada PostgreSQL 17 se comprobó que una cuenta propietaria de su propio esquema puede preparar las migraciones y registrar un sueldo, sin permiso para crear bases. Esta prueba no sustituye la revisión de permisos de la instalación definitiva.

## Preparar una instalación nueva

Un administrador de PostgreSQL debe crear una base nueva para la aplicación, una cuenta de inicio de sesión sin privilegios de superusuario, sin creación de roles ni bases, y un esquema cuyo propietario sea esa cuenta. No publicar contraseñas, copias ni archivos de configuración con credenciales en GitHub. Establecer la contraseña con un mecanismo seguro del administrador y entregarla al encargado por un canal seguro.

Ejemplo de estructura, para adaptar y ejecutar como administrador en la base destinada a la aplicación. La cuenta debe recibir una contraseña aparte mediante la herramienta administrativa:

```sql
CREATE ROLE cuadre_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE SCHEMA cuadre_tienda AUTHORIZATION cuadre_app;
GRANT CONNECT ON DATABASE cuadre_stock TO cuadre_app;
```

El administrador crea primero `cuadre_stock` y ejecuta en ella la creación del esquema. Si se revocó el permiso CONNECT concedido a PUBLIC, el GRANT explícito permite conectar. El propietario del esquema puede modificar sus tablas y ejecutar migraciones de la aplicación: esta cuenta no equivale a una cuenta de solo lectura ni sustituye la autenticación de empleados dentro de la aplicación. No conceder propiedad de la base ni permisos globales por comodidad.

En la conexión inicial escribe la base, el usuario y el esquema creados. Para un esquema existente, la aplicación no requiere permiso CREATE sobre toda la base; la cuenta debe poder usar y modificar los objetos de su esquema. Para crear uno nuevo desde la aplicación sí se requiere CREATE en la base. Si hay tablas previas, no cambiar su propietario ni ejecutar migraciones sin un procedimiento de actualización revisado.

## Actualización y recuperación

Antes de actualizar una base existente, crear una copia manual y restaurarla en una base diferente y vacía; comparar productos, caja y cierres. Instalar la beta nueva en otra carpeta, mantener la anterior y comprobar las migraciones en la copia antes de conectar la nueva versión a los datos originales. Las versiones antiguas no deben usarse sobre un esquema actualizado.

La restauración necesita una base destino previamente creada y permisos para crear allí el esquema que contiene la copia. Coordinar con el administrador la concesión mínima temporal necesaria y retirarla después de verificar la recuperación. La prueba automatizada de recuperación existente usa una cuenta de laboratorio con privilegios amplios; aún debe comprobarse este procedimiento completo con la cuenta limitada y las políticas de la instalación real.

Las copias y archivos Excel contienen información del negocio y no están cifrados por la aplicación. La red y acceso desde otras computadoras requieren diseño y validación adicionales. No hay cuentas de usuario de la aplicación ni firma digital en esta beta.
