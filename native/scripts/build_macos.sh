#!/usr/bin/env bash
set -euo pipefail

if [[ "${OSTYPE:-}" != darwin* ]]; then
  echo "This script must be run on macOS."
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BUILD_DIR="${ROOT_DIR}/build/native"
GENERATOR="${GENERATOR:-Xcode}"
CONFIG="${CONFIG:-Release}"

cmake -S "${ROOT_DIR}/native" -B "${BUILD_DIR}" -G "${GENERATOR}"
cmake --build "${BUILD_DIR}" --config "${CONFIG}" --target minecraft_metal
