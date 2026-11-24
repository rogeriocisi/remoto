#!/usr/bin/env sh
# Sobe o ReMoto (versao nova) em modo desenvolvimento.
# Uso: scripts/start.sh [porta]      (padrao: 8080)
# Procura um JDK 25+ (JAVA_HOME, java do PATH ou pastas comuns) e roda o remoto-web
# com o perfil "dev" (usuarios de teste em application-dev.yml).
set -eu

PORT="${1:-8080}"
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
echo "[ReMoto] Iniciando em http://localhost:$PORT/simulator/  (Ctrl+C para parar)"
cd "$ROOT"
exec sh ./mvnw -pl remoto-web -am spring-boot:run \
  "-Dspring-boot.run.profiles=dev" \
  "-Dspring-boot.run.arguments=--server.port=$PORT"
