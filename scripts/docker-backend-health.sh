#!/usr/bin/env bash
# Bash is provided by the Temurin Ubuntu runtime; no host utility is required.
set -euo pipefail
exec 3<>/dev/tcp/127.0.0.1/8080
printf 'GET /actuator/health HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3
response=$(cat <&3)
[[ ${response%%$'\n'*} =~ ^HTTP/1[.]1\ 200([[:space:]]|$) && "$response" == *'"status":"UP"'* ]]
