#!/usr/bin/env bash
set -euo pipefail

CONF=/opt/flink/conf/config.yaml
if [[ -n "${FLINK_REST_URL:-}" && -f "${CONF}" ]]; then
  host_port="${FLINK_REST_URL#*://}"
  host="${host_port%%:*}"
  port="${host_port##*:}"
  sed -i "s/^  address: .*/  address: ${host}/" "${CONF}"
  if grep -q '^  port:' "${CONF}"; then
    sed -i "s/^  port: .*/  port: ${port}/" "${CONF}"
  else
    sed -i "/^rest:/a\\  port: ${port}" "${CONF}"
  fi
fi

JAVA_OPTS=(
  --add-exports=java.base/sun.net.util=ALL-UNNAMED
  --add-exports=java.rmi/sun.rmi.registry=ALL-UNNAMED
  --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
  --add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
  --add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED
  --add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
  --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
  --add-exports=java.security.jgss/sun.security.krb5=ALL-UNNAMED
  --add-opens=java.base/java.lang=ALL-UNNAMED
  --add-opens=java.base/java.net=ALL-UNNAMED
  --add-opens=java.base/java.io=ALL-UNNAMED
  --add-opens=java.base/java.nio=ALL-UNNAMED
  --add-opens=java.base/sun.nio.ch=ALL-UNNAMED
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED
  --add-opens=java.base/java.text=ALL-UNNAMED
  --add-opens=java.base/java.time=ALL-UNNAMED
  --add-opens=java.base/java.util=ALL-UNNAMED
  --add-opens=java.base/java.util.concurrent=ALL-UNNAMED
  --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED
  --add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED
)

exec java "${JAVA_OPTS[@]}" -cp "/app/app.jar:/app/lib/*" com.ontoweave.data.DataServiceApplication
