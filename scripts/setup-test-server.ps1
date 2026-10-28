#Requires -Version 5.1
<#
    Prepare un serveur de test AntWars pret a lancer (Paper 1.21.8 + la map
    antwars + l'arene de test deja configuree).

    Usage :
        powershell -ExecutionPolicy Bypass -File scripts\setup-test-server.ps1

    Le dossier server/ est cree a la racine du projet et ignore par git.
    Relancer le script ne detruit ni le monde ni les stats : il ne remplace
    que le jar du plugin et les fichiers de configuration.
#>
param(
    [string]$ServerDir = (Join-Path $PSScriptRoot "..\server"),
    [string]$PaperVersion = "1.21.8",
    [int]$PaperBuild = 60
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $root

function Get-LocalJdk {
    $candidates = Get-ChildItem (Join-Path $root "tools\jdk") -Directory -ErrorAction SilentlyContinue
    if (-not $candidates) { return $null }
    return ($candidates | Select-Object -First 1).FullName
}

function Get-LocalMaven {
    $candidates = Get-ChildItem (Join-Path $root "tools\mvn") -Directory -ErrorAction SilentlyContinue
    if (-not $candidates) { return $null }
    return ($candidates | Select-Object -First 1).FullName
}

$jdk = Get-LocalJdk
if (-not $jdk) {
    throw "JDK local introuvable dans tools\jdk. Lance le telechargement du JDK 21 (voir README)."
}
$mvn = Get-LocalMaven
if (-not $mvn) {
    throw "Maven local introuvable dans tools\mvn. Lance le telechargement de Maven (voir README)."
}

$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;" + $env:Path

Write-Host "== Build du plugin =="
& "$mvn\bin\mvn.cmd" -B -ntp clean package
if ($LASTEXITCODE -ne 0) { throw "Le build Maven a echoue." }

$ServerDir = [System.IO.Path]::GetFullPath($ServerDir)
New-Item -ItemType Directory -Force -Path $ServerDir | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $ServerDir "plugins\AntWars") | Out-Null

$paper = Join-Path $ServerDir "paper.jar"
if (-not (Test-Path $paper)) {
    Write-Host "== Telechargement de Paper $PaperVersion build $PaperBuild =="
    $hash = "8de7c52c3b02403503d16fac58003f1efef7dd7a0256786843927fa92ee57f1e"
    $url = "https://fill-data.papermc.io/v1/objects/$hash/paper-$PaperVersion-$PaperBuild.jar"
    $ProgressPreference = "SilentlyContinue"
    Invoke-WebRequest -Uri $url -OutFile $paper
}

Write-Host "== Copie du jar AntWars =="
Copy-Item (Join-Path $root "target\AntWars-1.0.0.jar") (Join-Path $ServerDir "plugins\AntWars-1.0.0.jar") -Force

if (-not (Test-Path (Join-Path $ServerDir "world\level.dat"))) {
    Write-Host "== Copie de la map antwars =="
    New-Item -ItemType Directory -Force -Path (Join-Path $ServerDir "world") | Out-Null
    Copy-Item (Join-Path $root "antwars-map\*") (Join-Path $ServerDir "world") -Recurse -Force
}

Set-Content -Path (Join-Path $ServerDir "eula.txt") -Value "eula=true" -Encoding ASCII
Copy-Item (Join-Path $root "test-server\server.properties") (Join-Path $ServerDir "server.properties") -Force

# Ne pas ecraser une config deja modifiee par l'utilisateur : on ne cree les
# fichiers AntWars que s'ils n'existent pas encore.
$arenaFile = Join-Path $ServerDir "plugins\AntWars\arenes.yml"
if (-not (Test-Path $arenaFile)) {
    Copy-Item (Join-Path $root "test-server\arenas.yml") $arenaFile
}
$configFile = Join-Path $ServerDir "plugins\AntWars\config.yml"
if (-not (Test-Path $configFile)) {
    Copy-Item (Join-Path $root "src\main\resources\config.yml") $configFile
    & py (Join-Path $root "scripts\patch_config.py") $configFile
    if ($LASTEXITCODE -ne 0) { throw "Le patch de config.yml a echoue (Python requis)." }
}

$startScript = @'
$root = $PSScriptRoot
$jdk = Join-Path $root "..\tools\jdk"
$java = Get-ChildItem $jdk -Directory -ErrorAction SilentlyContinue |
    Select-Object -First 1 |
    ForEach-Object { Join-Path $_.FullName "bin\java.exe" }
if (-not $java -or -not (Test-Path $java)) { $java = "java" }
Set-Location $root
& $java -Xms1G -Xmx2G -jar (Join-Path $root "paper.jar") --nogui
'@
Set-Content -Path (Join-Path $ServerDir "start.ps1") -Value $startScript -Encoding UTF8

Write-Host ""
Write-Host "Serveur pret : $ServerDir"
Write-Host "Lance-le avec : powershell -ExecutionPolicy Bypass -File `"$ServerDir\start.ps1`""
Write-Host "Puis connecte-toi sur localhost:25565 (mode hors-ligne)."
Write-Host "En jeu : /aw join antwars"
