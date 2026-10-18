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

exec java -jar /app/app.jar
