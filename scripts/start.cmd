@echo off
rem Sobe o ReMoto (versao nova) em modo desenvolvimento: um Tomcat so, que serve o simulador em
rem /simulator e o site descritivo de docs/ na raiz, como no ar.
rem Uso: scripts\start.cmd [porta]      (padrao: 8081)
rem Procura um JDK 25+ (JAVA_HOME ou pastas comuns) e roda o remoto-web com o perfil "dev"
rem (usuarios de teste em application-dev.yml).
setlocal EnableExtensions EnableDelayedExpansion

set "PORT=%~1"
if "%PORT%"=="" set "PORT=8081"
set "ROOT=%~dp0.."
set "JDK="

call :check "%JAVA_HOME%"
for %%B in ("%ProgramFiles%\Java" "%ProgramFiles%\Eclipse Adoptium" "%ProgramFiles%\Amazon Corretto" "%ProgramFiles%\Microsoft" "%ProgramFiles%\Zulu") do (
  if not defined JDK (
    for /d %%D in ("%%~B\jdk-25*" "%%~B\jdk25*" "%%~B\zulu-25*") do (
      if not defined JDK call :check "%%~fD"
    )
  )
)

if not defined JDK (
  echo [ReMoto] JDK 25 nao encontrado. Instale um JDK 25 ^(ex.: https://adoptium.net^) ou defina JAVA_HOME.
  exit /b 1
)

set "JAVA_HOME=%JDK%"
echo [ReMoto] Usando JDK em %JAVA_HOME%

rem Um simulador de uma sessao anterior que tenha ficado de pe segura a porta, e o Maven so diria
rem "Port %PORT% was already in use" la no fim do build; aqui se diz quem e, antes de compilar.
set "BUSY="
for /f "tokens=5" %%P in ('netstat -ano -p tcp ^| findstr /r /c:":%PORT% .*LISTENING"') do set "BUSY=%%P"
if defined BUSY (
  echo [ReMoto] A porta %PORT% ja esta em uso pelo processo %BUSY%.
  echo [ReMoto] Pare esse processo com  taskkill /f /pid %BUSY%  ou use outra porta ^(primeiro argumento^).
  exit /b 1
)

echo [ReMoto] Site em http://localhost:%PORT%/  (e /pt/ em portugues)
echo [ReMoto] Iniciando em http://localhost:%PORT%/simulator/  (Ctrl+C para parar)
cd /d "%ROOT%"
call "%ROOT%\mvnw.cmd" -pl remoto-web -am spring-boot:run "-Dspring-boot.run.profiles=dev" "-Dspring-boot.run.arguments=--server.port=%PORT%"
set "CODE=%ERRORLEVEL%"
rem O JVM do simulador e filho do Maven e nao morre junto com ele. A porta estava livre quando esta
rem janela comecou (a checagem la em cima), entao o java que estiver nela agora e desta sessao.
for /f "tokens=5" %%P in ('netstat -ano -p tcp ^| findstr /r /c:":%PORT% .*LISTENING"') do (
  tasklist /fi "PID eq %%P" /fi "IMAGENAME eq java.exe" | findstr /i "java.exe" >nul && taskkill /f /pid %%P >nul 2>&1
)
exit /b %CODE%

rem Define JDK se a pasta %1 for um JDK 25 ou mais novo (le o arquivo "release").
:check
set "CAND=%~1"
set "VER="
set "MAJOR="
if "%CAND%"=="" exit /b 0
if not exist "%CAND%\release" exit /b 0
for /f "tokens=2 delims==" %%V in ('findstr /b "JAVA_VERSION=" "%CAND%\release"') do set "VER=%%~V"
for /f "tokens=1 delims=." %%M in ("%VER%") do set "MAJOR=%%M"
if defined MAJOR if !MAJOR! GEQ 25 set "JDK=%CAND%"
exit /b 0
