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
    # Select which container exe to use based on which implementation is found on system `PATH`. The ordering below
    # (podman, docker, finch) matches the ordering in the dockcross runner scripts generated below.
    if which podman >/dev/null 2>/dev/null; then
        OCI_EXE=podman
    elif which docker >/dev/null 2>/dev/null; then
        OCI_EXE=docker
    elif which finch > /dev/null 2>/dev/null; then
        OCI_EXE=finch
    else
        die "Cannot find a container executor. Search for docker and podman."
    fi
fi

echo "using container executor OCI_EXE=$OCI_EXE"

echo "Authenticating to Public ECR:"
aws ecr-public get-login-password --region us-east-1 | $OCI_EXE login --username AWS --password-stdin public.ecr.aws

if [ "$#" -gt 0 ]; then
  IMAGES=("$@")
else
  IMAGES=(
    "linux-x64"
    "linux-arm64"
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
