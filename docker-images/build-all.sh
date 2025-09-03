#!/bin/bash
set -e

err() {
    echo -e >&2 "ERROR: $*\n"
}

die() {
    err "$*"
    exit 1
}

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
PROJ_ROOT=$(realpath "$SCRIPT_DIR/..")

if [ -z "$OCI_EXE" ]; then
    if which finch > /dev/null 2>/dev/null; then
      OCI_EXE=finch
    elif which podman >/dev/null 2>/dev/null; then
        OCI_EXE=podman
    elif which docker >/dev/null 2>/dev/null; then
        OCI_EXE=docker
    else
        die "Cannot find a container executor. Search for docker and podman."
    fi
fi

echo "using container executor OCI_EXE=$OCI_EXE"

if [ "$#" -gt 0 ]; then
  IMAGES=("$@")
else
  IMAGES=(
    "linux-x64"
    "linux-arm64"
    "mingw-x64"
  )
fi

echo "Building images $IMAGES"

for IMAGE in "${IMAGES[@]}"; do
  echo "Building dockcross-$IMAGE..."
  $OCI_EXE build -f "$SCRIPT_DIR/$IMAGE/Dockerfile" -t "aws-crt-kotlin/$IMAGE:latest" "$PROJ_ROOT"
  $OCI_EXE run --rm "aws-crt-kotlin/$IMAGE:latest" > "$PROJ_ROOT/dockcross-$IMAGE"
  chmod ug+x "$PROJ_ROOT/dockcross-$IMAGE"
  echo ""
done
