param([string]$MavenCommand = 'mvn', [string]$MavenRepository = '', [string]$Destino = '', [switch]$SinConexion)
$ErrorActionPreference='Stop'
$proyecto=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location -LiteralPath $proyecto
if([string]::IsNullOrWhiteSpace($Destino)) { $Destino=Join-Path $proyecto 'distribucion' }
if(Test-Path -LiteralPath (Join-Path $Destino 'CuadreYStockBeta')) { throw 'Ya existe una beta en ese destino. Elige otro con -Destino para conservar la anterior.' }
$marca=Get-Date -Format 'yyyyMMdd-HHmmss'
$preparacion=Join-Path $proyecto "target\beta-$marca"
$bibliotecas=Join-Path $preparacion 'lib'
$argumentos=@('-B','-DskipTests','package','dependency:copy-dependencies','-DincludeScope=runtime',"-DoutputDirectory=$bibliotecas")
if($MavenRepository) { $argumentos += "-Dmaven.repo.local=$MavenRepository" }
if($SinConexion) { $argumentos += '-o' }
& $MavenCommand @argumentos
if($LASTEXITCODE -ne 0) { throw 'Falló la compilación o la descarga de dependencias.' }
Copy-Item -LiteralPath (Join-Path $proyecto 'target\inventario-caja-0.1.0-SNAPSHOT.jar') -Destination $preparacion
$jpackage=if($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\jpackage.exe'))) { Join-Path $env:JAVA_HOME 'bin\jpackage.exe' } else { (Get-Command jpackage.exe -ErrorAction Stop).Source }
& $jpackage --type app-image --input $preparacion --dest $Destino --name CuadreYStockBeta --app-version 0.1.0 --main-jar inventario-caja-0.1.0-SNAPSHOT.jar --main-class com.surquillo.inventariocaja.Lanzador --vendor Drvo --description 'Cuadre y Stock - beta de pruebas' --java-options '--enable-native-access=ALL-UNNAMED' --add-modules java.sql,java.naming,java.desktop,java.logging,jdk.unsupported,java.xml,java.net.http,java.management,jdk.crypto.ec
if($LASTEXITCODE -ne 0) { throw 'No se pudo crear el paquete de pruebas.' }
Write-Output "Beta generada: $(Join-Path $Destino 'CuadreYStockBeta\CuadreYStockBeta.exe')"
