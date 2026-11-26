# Sobe o ReMoto (versao nova) em modo desenvolvimento: o simulador (Spring Boot) e, na frente
# dele, o site de docs/ servido pelo Node, como os dois ficam no ar.
# Uso: .\scripts\start.ps1 [-Port 8081] [-SitePort 8090] [-NoSite]
# Procura um JDK 25+ (JAVA_HOME ou pastas comuns) e roda o remoto-web com o perfil "dev"
# (usuarios de teste em application-dev.yml). O site precisa do Node; sem ele, so o simulador sobe.
# Se o Windows bloquear scripts: powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1
param([int]$Port = 8081, [int]$SitePort = 8090, [switch]$NoSite)

function Get-JdkMajor([string]$JdkHome) {
    if (-not $JdkHome) { return 0 }
    $release = Join-Path $JdkHome 'release'
    if (-not (Test-Path -LiteralPath $release)) { return 0 }
    $match = Select-String -LiteralPath $release -Pattern '^JAVA_VERSION="(\d+)' | Select-Object -First 1
    if ($match) { return [int]$match.Matches[0].Groups[1].Value }
    return 0
}

# Tudo o que esta sessao abriu: o Maven, o JVM do simulador que ele abre, e o Node.
function Stop-Descendants([int]$ParentId) {
    foreach ($child in (Get-CimInstance Win32_Process -Filter "ParentProcessId=$ParentId" -ErrorAction SilentlyContinue)) {
        Stop-Descendants $child.ProcessId
        Stop-Process -Id $child.ProcessId -Force -ErrorAction SilentlyContinue
    }
}

# O simulador roda num JVM que o Maven abre, e que nao morre junto com ele: se o Maven sair antes,
# esse JVM fica orfao segurando a porta. Aqui se derruba o que ainda estiver nela, e so ele: a
# linha de comando tem de ser a do simulador.
function Stop-Simulator([int]$OnPort) {
    $listening = Get-NetTCPConnection -LocalPort $OnPort -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $listening) { return }
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listening.OwningProcess)" -ErrorAction SilentlyContinue
    if ($process -and $process.CommandLine -like '*br.remoto.web.RemotoApplication*') {
        Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue
    }
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

# Um simulador de uma sessao anterior que tenha ficado de pe segura a porta, e o Maven so diria
# "Port $Port was already in use" la no fim do build; aqui se diz quem e, antes de compilar.
$busy = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($busy) {
    $owner = Get-Process -Id $busy.OwningProcess -ErrorAction SilentlyContinue
    Write-Host "[ReMoto] A porta $Port ja esta em uso por $($owner.ProcessName) (PID $($owner.Id))." -ForegroundColor Red
    Write-Host "[ReMoto] Pare esse processo com  Stop-Process -Id $($owner.Id) -Force  ou use outra porta (-Port)." -ForegroundColor Red
    exit 1
}

# O site fica na frente e repassa /simulator/ para o Spring Boot (ver scripts/site-serve.mjs):
# e o que faz os links entre os dois lados funcionarem em localhost como funcionam no ar.
$site = $null
if (-not $NoSite) {
    $node = Get-Command node -ErrorAction SilentlyContinue
    if (-not $node) {
        Write-Host "[ReMoto] Node nao encontrado: sobe so o simulador, sem o site." -ForegroundColor Yellow
    } elseif (Get-NetTCPConnection -LocalPort $SitePort -State Listen -ErrorAction SilentlyContinue) {
        Write-Host "[ReMoto] A porta $SitePort ja esta em uso: sobe so o simulador, sem o site." -ForegroundColor Yellow
    } else {
        $site = Start-Process -FilePath $node.Source -NoNewWindow -PassThru -ArgumentList @(
            (Join-Path $PSScriptRoot 'site-serve.mjs'), '--port', "$SitePort", '--simulator', "http://localhost:$Port")
    }
}

if ($site) {
    Write-Host "[ReMoto] Site em http://localhost:$SitePort/  (o simulador tambem, em /simulator/)"
}
Write-Host "[ReMoto] Iniciando em http://localhost:$Port/simulator/  (Ctrl+C para parar)"

Push-Location -LiteralPath $root
try {
    & (Join-Path $root 'mvnw.cmd') -pl remoto-web -am spring-boot:run "-Dspring-boot.run.profiles=dev" "-Dspring-boot.run.arguments=--server.port=$Port"
    $code = $LASTEXITCODE
} finally {
    # O Maven sobe o simulador num JVM separado, que nao morre junto com o Maven: sem esta
    # limpeza, sair daqui deixaria o simulador de pe segurando a porta (e a proxima subida
    # falharia com "Port 8081 was already in use"). O site, pelo mesmo motivo, para aqui tambem.
    Stop-Descendants $PID
    Stop-Simulator $Port
    if ($site -and -not $site.HasExited) {
        Stop-Process -Id $site.Id -Force -ErrorAction SilentlyContinue
    }
    Pop-Location
}
exit $code
