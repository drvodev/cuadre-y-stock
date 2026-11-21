[CmdletBinding()]
param(
    [ValidateSet('Local','Remoto')][string]$Modo = 'Local',
    [string]$Instalador = '',
    [string]$Binario = '',
    [string]$Base = 'cuadre_stock',
    [string]$Usuario = 'postgres',
    [ValidateRange(1,65535)][int]$Puerto = 5432,
    [switch]$CrearBase,
    [Security.SecureString]$Clave
)
$ErrorActionPreference = 'Stop'
if ($Modo -eq 'Remoto') {
    Write-Output 'Modo remoto: no se instala ni modifica PostgreSQL. Configura servidor y credenciales en la aplicación.'
    return
}
if ($Base -cnotmatch '^[a-z][a-z0-9_]{0,62}$') { throw 'El nombre de base debe usar minúsculas, números y guion bajo.' }
if ([string]::IsNullOrWhiteSpace($Usuario)) { throw 'Indica un usuario PostgreSQL.' }

function Buscar-Binario {
    if ($Binario) {
        $carpeta = (Resolve-Path -LiteralPath $Binario -ErrorAction Stop).Path
        if (-not (Test-Path -LiteralPath (Join-Path $carpeta 'psql.exe') -PathType Leaf)) { throw 'La carpeta indicada no contiene psql.exe.' }
        return $carpeta
    }
    $posibles = @()
    $enPath = Get-Command psql.exe -ErrorAction SilentlyContinue
    if ($enPath) { $posibles += Split-Path -Parent $enPath.Source }
    $raiz = Join-Path $env:ProgramFiles 'PostgreSQL'
    if (Test-Path -LiteralPath $raiz) {
        $posibles += @(Get-ChildItem -LiteralPath $raiz -Directory | Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'bin' })
    }
    foreach ($carpeta in $posibles) {
        if (Test-Path -LiteralPath (Join-Path $carpeta 'psql.exe') -PathType Leaf) { return $carpeta }
    }
    return $null
}

$herramientas = Buscar-Binario
if ($herramientas) {
    Write-Output "Herramientas PostgreSQL detectadas: $herramientas"
    if ($Instalador) { Write-Output 'Ya se detectó una instalación; no se ejecutará el instalador.' }
} elseif (-not $Instalador) {
    Write-Output 'No se detectaron herramientas PostgreSQL. Descarga el instalador oficial y vuelve a ejecutar con -Instalador, o indica -Binario si ya lo tienes.'
    Write-Output 'Descarga oficial: https://www.postgresql.org/download/windows/'
    return
} else {
    $archivo = (Resolve-Path -LiteralPath $Instalador -ErrorAction Stop).Path
    if ([System.IO.Path]::GetExtension($archivo) -ine '.exe') { throw 'Selecciona un instalador .exe de PostgreSQL para Windows.' }
    $firma = Get-AuthenticodeSignature -LiteralPath $archivo
    if ($firma.Status -ne 'Valid' -or $firma.SignerCertificate.Subject -notmatch '(?i)EnterpriseDB') {
        throw 'La firma del instalador no pudo validarse como EnterpriseDB. Descarga el instalador oficial y comprueba su firma.'
    }
    Write-Output 'Se abrirá el instalador interactivo. Elige sus opciones y contraseña; esta herramienta no las guarda.'
    $proceso = Start-Process -FilePath $archivo -Wait -PassThru -Verb RunAs
    if ($proceso.ExitCode -ne 0) { throw "El instalador terminó con código $($proceso.ExitCode)." }
    $herramientas = Buscar-Binario
    if (-not $herramientas) { throw 'Finalizó el instalador, pero no se localizaron las herramientas. Indica -Binario y vuelve a intentar.' }
}
if (-not $CrearBase) {
    Write-Output 'No se creó ninguna base. Para crear una base local nueva, vuelve a ejecutar con -CrearBase; también puedes usar una base existente.'
    return
}
$psql = Join-Path $herramientas 'psql.exe'
$createdb = Join-Path $herramientas 'createdb.exe'
if (-not (Test-Path -LiteralPath $createdb -PathType Leaf)) { throw 'No se encontró createdb.exe en la carpeta de herramientas.' }
$claveSegura = if ($Clave) { $Clave } else { Read-Host 'Contraseña PostgreSQL (no se muestra)' -AsSecureString }
$puntero = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($claveSegura)
$previa = [Environment]::GetEnvironmentVariable('PGPASSWORD','Process')
try {
    $env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($puntero)
    $existe = & $psql -X -w -h 127.0.0.1 -p $Puerto -U $Usuario -d postgres -v ON_ERROR_STOP=1 -tAc "SELECT 1 FROM pg_database WHERE datname='$Base'"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo consultar PostgreSQL local. Comprueba el servicio, usuario, puerto y contraseña.' }
    if (($existe | Out-String).Trim() -eq '1') {
        Write-Output "La base $Base ya existe. Se conserva sin cambios."
    } else {
        & $createdb -w -h 127.0.0.1 -p $Puerto -U $Usuario -- $Base
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la base. El usuario requiere el permiso correspondiente.' }
        Write-Output "Base $Base creada."
    }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($puntero)
    [Environment]::SetEnvironmentVariable('PGPASSWORD',$previa,'Process')
}
Write-Output 'Abre la aplicación y conecta a esta base con el usuario y un esquema nuevo. La aplicación aplicará las migraciones al conectar.'
