#!/usr/bin/env sh
# Sobe o ReMoto (versao nova) em modo desenvolvimento: um Tomcat so, que serve o simulador em
# /simulator e o site descritivo de docs/ na raiz, como no ar.
# Uso: scripts/start.sh [porta]      (padrao: 8081)
# Procura um JDK 25+ (JAVA_HOME, java do PATH ou pastas comuns) e roda o remoto-web
# com o perfil "dev" (usuarios de teste em application-dev.yml).
set -eu

PORT="${1:-8081}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Versao principal de um JDK, lida do arquivo "release" (0 se nao der para ler).
java_major() {
  v=""
  if [ -n "${1:-}" ] && [ -f "$1/release" ]; then
    v=$(sed -n 's/^JAVA_VERSION="\([0-9]*\).*/\1/p' "$1/release" | head -n 1)
  fi
  echo "${v:-0}"
}

find_jdk() {
  if [ "$(java_major "${JAVA_HOME:-}")" -ge 25 ]; then
    echo "$JAVA_HOME"; return 0
  fi
  if command -v java >/dev/null 2>&1; then
    home=$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.home = //p' | head -n 1)
    if [ "$(java_major "$home")" -ge 25 ]; then
      echo "$home"; return 0
    fi
  fi
  for d in /usr/lib/jvm/*25* \
           /Library/Java/JavaVirtualMachines/*25*/Contents/Home \
           "$HOME"/.sdkman/candidates/java/25* \
           "$HOME"/.jdks/*25* \
           "/c/Program Files/Java/jdk-25"* \
           "/c/Program Files/Eclipse Adoptium/jdk-25"* \
           "/c/Program Files/Amazon Corretto/jdk25"* \
           "/c/Program Files/Microsoft/jdk-25"*; do
    [ -d "$d" ] || continue
    if [ "$(java_major "$d")" -ge 25 ]; then
      echo "$d"; return 0
    fi
  done
  return 1
}

if ! JDK=$(find_jdk); then
  echo "[ReMoto] JDK 25 nao encontrado. Instale um JDK 25 (ex.: https://adoptium.net) ou defina JAVA_HOME." >&2
  exit 1
fi

export JAVA_HOME="$JDK"
echo "[ReMoto] Usando JDK em $JAVA_HOME"

# O simulador roda num JVM que o Maven abre, e que nao morre junto com ele: se este script sair
# antes, esse JVM fica orfao segurando a porta e a proxima subida falha. So morre o que estiver na
# porta do simulador, e so se for ele mesmo.
stop_leftovers() {
  case "$(uname -s)" in
    MINGW* | MSYS* | CYGWIN*)
      # No Windows o JVM nem aparece na lista de processos do shell; quem o conhece e o Windows.
      pid=$(netstat -ano -p tcp 2>/dev/null | grep -E "[:.]$PORT[[:space:]]+.*LISTENING" | awk '{print $5}' | head -n 1)
      if [ -n "${pid:-}" ] && tasklist //FI "PID eq $pid" //FI "IMAGENAME eq java.exe" 2>/dev/null | grep -qi 'java.exe'; then
        taskkill //F //PID "$pid" >/dev/null 2>&1 || true
      fi
      ;;
    *)
      if command -v pkill >/dev/null 2>&1; then
        pkill -f "br.remoto.web.RemotoApplication.*--server.port=$PORT" 2>/dev/null || true
      fi
      ;;
  esac
}
trap stop_leftovers EXIT INT TERM

echo "[ReMoto] Site em http://localhost:$PORT/  (e /pt/ em portugues)"
echo "[ReMoto] Iniciando em http://localhost:$PORT/simulator/  (Ctrl+C para parar)"
cd "$ROOT"
# Sem exec: o processo precisa continuar vivo para a limpeza do fim (trap).
sh ./mvnw -pl remoto-web -am spring-boot:run \
  "-Dspring-boot.run.profiles=dev" \
  "-Dspring-boot.run.arguments=--server.port=$PORT"
