#!/usr/bin/env bash
# Sourced by the iOS public matrix. Contains no simulator/build side effects.

quata_config_mode() {
  if [[ "$(uname -s)" == "Darwin" ]]; then
    stat -f '%Lp' "$1"
  else
    stat -c '%a' "$1"
  fi
}

quata_backup_runtime_config() {
  local config_path="$1"
  local backup_path="$2"
  QUATA_RUNTIME_CONFIG_HAD=0
  QUATA_RUNTIME_CONFIG_MODE=""
  chmod 0600 "$backup_path"
  if [[ -e "$config_path" ]]; then
    QUATA_RUNTIME_CONFIG_MODE="$(quata_config_mode "$config_path")"
    cp "$config_path" "$backup_path"
    # cp may inherit a permissive mode on some hosts. The backup is always private.
    chmod 0600 "$backup_path"
    QUATA_RUNTIME_CONFIG_HAD=1
  fi
}

quata_restore_runtime_config() {
  local config_path="$1"
  local backup_path="$2"
  rm -f "$config_path" || return 1
  if ((QUATA_RUNTIME_CONFIG_HAD)); then
    mv -f "$backup_path" "$config_path" || return 1
    chmod "$QUATA_RUNTIME_CONFIG_MODE" "$config_path" || return 1
  else
    rm -f "$backup_path" || return 1
  fi
}
