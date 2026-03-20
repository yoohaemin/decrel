#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VUEPRESS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ROOT_DIR="$(cd "${VUEPRESS_DIR}/.." && pwd)"
PUBLIC_API_DIR="${VUEPRESS_DIR}/docs/.vuepress/public/api"

declare -a MODULES=(
  "core:core:core"
  "zquery:zquery:zquery"
  "fetch:fetch:fetch"
  "scalacheck:scalacheck:scalacheck"
  "ziotest:ziotest:ziotest"
  "cats:cats:cats"
  "kyo:kyo:kyo"
  "kyoBatch:kyo-batch:kyo-batch"
)

pushd "${ROOT_DIR}" >/dev/null

rm -rf "${PUBLIC_API_DIR}"
mkdir -p "${PUBLIC_API_DIR}"

declare -a SBT_COMMAND=()
for module in "${MODULES[@]}"; do
  IFS=":" read -r project_id _ _ <<<"${module}"
  SBT_COMMAND+=("${project_id}/doc")
done

sbt "${SBT_COMMAND[@]}"

for module in "${MODULES[@]}"; do
  IFS=":" read -r _ source_dir dest_dir <<<"${module}"

  api_source="$(
    find "${ROOT_DIR}/${source_dir}/.jvm/target" -type d -name api -exec stat -c '%Y %n' {} + \
      | sort -nr \
      | head -n 1 \
      | cut -d' ' -f2-
  )"
  if [[ -z "${api_source}" ]]; then
    echo "Could not locate Scaladoc output for ${source_dir}" >&2
    exit 1
  fi

  cp -R "${api_source}" "${PUBLIC_API_DIR}/${dest_dir}"
done

popd >/dev/null
