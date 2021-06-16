#!/usr/bin/env bash

FWDIR="$(
  cd "$(dirname "$0")"/.. || exit
  pwd
)"

EXIT=false

cd ${FWDIR} && \
sbt ++${VERSION} clean publishM2 && \
sbt ++${VERSION} test && \
EXIT=true

${EXIT}