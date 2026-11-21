# Mejoras del issue 1: estado de la propuesta

Esta rama está en revisión y no modifica la beta 0.1.5 publicada. Antes de usarla con una base existente, generar una copia y probar la actualización y la recuperación sobre otra base.

## Incluido

- Catálogo: si se deja vacío el SKU al crear, PostgreSQL asigna un número consecutivo de al menos tres cifras, por ejemplo 001 y 1000. Los códigos manuales siguen disponibles; se conserva el historial de códigos usados y los artículos existentes no se renumeran. Los valores obtenidos de una secuencia pueden tener saltos tras errores o concurrencia. La migración 14 inicia el contador después del mayor SKU numérico histórico de hasta 18 cifras y protege los códigos conocidos frente a reutilización. La migración 15 guarda la solicitud de creación automática junto con el artículo para recuperar el resultado ante una respuesta perdida; el formulario conserva esa solicitud durante la sesión. Los códigos numéricos manuales superiores también adelantan la secuencia.
- Stock: el ajuste manual sigue indicando el nuevo stock físico y ahora lo explica. La edición rápida de disponibilidad suma o resta la cantidad indicada, exige motivo y confirmación, preserva las reservas y detecta cambios simultáneos. En la lista de stock, el doble clic selecciona el SKU y almacén en movimientos. Se conservan la búsqueda por nombre/SKU y las validaciones de cantidades enteras o fraccionadas.
- Ventas: búsqueda de hasta 100 productos activos por nombre o SKU para elegirlos desde una lista con precio sugerido y disponibilidad. Si hay más resultados, es necesario precisar la búsqueda. Las líneas y precios manuales se mantienen.
- Conexión: al conectar correctamente se recuerdan servidor, puerto, base, usuario y esquema en las preferencias del usuario de Windows; todos se pueden cambiar. La contraseña no se guarda en esas preferencias.
- Preparación opcional: `tools/Preparar-PostgreSQL.ps1` detecta herramientas locales, permite abrir interactivamente un instalador EDB con firma válida cuando no se detectan, y ofrece crear una base local sin borrar ni modificar una base existente. El modo remoto omite la instalación. Las migraciones se aplican al conectar desde la aplicación. La preparación completa con instalación y una cuenta limitada aún debe probarse en un equipo de ensayo.
- Caja: los medios se conservan de forma individual para respetar el historial. El resumen presenta Efectivo, Visa, Transferencia/Depósito y Yape/Plin, además del total de ingresos brutos, separados del fondo y los egresos. Para turnos cerrados se reconstruye a partir de los movimientos persistidos del propio turno.

### Preparar PostgreSQL en Windows

En PowerShell, desde la carpeta del proyecto o de la aplicación empaquetada:

```powershell
./Preparar-PostgreSQL.ps1 -Modo Local
./Preparar-PostgreSQL.ps1 -Modo Local -Instalador 'C:\ruta\instalador-oficial.exe'
./Preparar-PostgreSQL.ps1 -Modo Local -CrearBase -Base cuadre_stock -Usuario postgres -Puerto 5432
./Preparar-PostgreSQL.ps1 -Modo Remoto
```

En el repositorio el archivo está en `tools/`. El instalador es opcional y solo se ejecuta si no se detectan herramientas; debe proceder de la [descarga oficial PostgreSQL para Windows](https://www.postgresql.org/download/windows/) y tener una firma válida de EnterpriseDB. Se abre su asistente interactivo y el usuario decide sus opciones. `-Binario` permite elegir otra carpeta `bin`; `-CrearBase` solicita la contraseña sin mostrarla y crea la base solo si no existe. Esto requiere un usuario PostgreSQL con permiso de creación. En una base existente no se crea, sustituye ni borra nada. Tras esto, se conecta desde la aplicación con un esquema nuevo para aplicar las migraciones. El modo remoto no ejecuta instalación ni crea bases.

## Pendiente

- Prueba manual del instalador gráfico de PostgreSQL en una instalación limpia y de la recuperación con permisos limitados. El instalador debe ser descargado por el usuario desde la página oficial de PostgreSQL; la aplicación no incorpora ni descarga un binario de instalación. En un servidor PostgreSQL aislado se verificaron la creación de una base nueva y la conservación de datos al repetir el asistente; el modo remoto se verificó sin cambios locales.
- Imágenes de artículos, señaladas como prioridad baja en el issue.
- Revisión manual de las pantallas y aceptación en el equipo de trabajo. Las pruebas automatizadas no sustituyen esa revisión.

No se deben fusionar estos cambios hasta revisar los resultados y el comportamiento de la migración. La copia manual y la recuperación en una base diferente siguen siendo los procedimientos previos a cualquier actualización con datos reales.

La suite definitiva de Maven para 0.1.6 ejecutó 127 pruebas sin fallos ni omisiones con PostgreSQL aislado. Los resultados no acreditan el funcionamiento visual ni la ejecución del instalador interactivo en el equipo del negocio. La revisión independiente del agente de pruebas y sus límites están descritos en BETA-0.1.6.md.
