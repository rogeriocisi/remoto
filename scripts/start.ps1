# Sobe o ReMoto (versao nova) em modo desenvolvimento.
# Uso: .\scripts\start.ps1 [-Port 8080]
# Procura um JDK 25+ (JAVA_HOME ou pastas comuns) e roda o remoto-web com o perfil "dev"
# (usuarios de teste em application-dev.yml).
# Se o Windows bloquear scripts: powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1
param([int]$Port = 8080)

function Get-JdkMajor([string]$JdkHome) {
    if (-not $JdkHome) { return 0 }
    $release = Join-Path $JdkHome 'release'
    if (-not (Test-Path -LiteralPath $release)) { return 0 }
    $match = Select-String -LiteralPath $release -Pattern '^JAVA_VERSION="(\d+)' | Select-Object -First 1
    if ($match) { return [int]$match.Matches[0].Groups[1].Value }
    return 0
}

$jdk = $null
if ((Get-JdkMajor $env:JAVA_HOME) -ge 25) { $jdk = $env:JAVA_HOME }

if (-not $jdk) {
    $patterns = @(
        "$env:ProgramFiles\Java\jdk-25*",
        "$env:ProgramFiles\Eclipse Adoptium\jdk-25*",
        "$env:ProgramFiles\Amazon Corretto\jdk25*",
        "$env:ProgramFiles\Microsoft\jdk-25*",
        "$env:ProgramFiles\Zulu\zulu-25*"
    )
    foreach ($pattern in $patterns) {
        foreach ($dir in (Get-ChildItem -Path $pattern -Directory -ErrorAction SilentlyContinue)) {
            if ((Get-JdkMajor $dir.FullName) -ge 25) { $jdk = $dir.FullName; break }
        }
        if ($jdk) { break }
    }
}

if (-not $jdk) {
    Write-Host "[ReMoto] JDK 25 nao encontrado. Instale um JDK 25 (ex.: https://adoptium.net) ou defina JAVA_HOME." -ForegroundColor Red
    exit 1
}

$env:JAVA_HOME = $jdk
$root = Split-Path -Parent $PSScriptRoot
Write-Host "[ReMoto] Usando JDK em $jdk"
Write-Host "[ReMoto] Iniciando em http://localhost:$Port/simulator/  (Ctrl+C para parar)"

Push-Location -LiteralPath $root
try {
    & (Join-Path $root 'mvnw.cmd') -pl remoto-web -am spring-boot:run "-Dspring-boot.run.profiles=dev" "-Dspring-boot.run.arguments=--server.port=$Port"
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $code
