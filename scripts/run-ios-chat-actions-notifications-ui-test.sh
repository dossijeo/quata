#!/usr/bin/env bash
# Opt-in local gate: seed a real Keychain session, then execute Chat composer/action UI evidence.
set -euo pipefail

: "${QUATA_IOS_AUTH_E2E_FILE:?Set QUATA_IOS_AUTH_E2E_FILE to the local credentials JSON.}"
: "${QUATA_IOS_DERIVED_DATA_PATH:?Build the signed simulator test bundle first and set QUATA_IOS_DERIVED_DATA_PATH.}"
: "${QUATA_IOS_SIMULATOR_UDID:?Set QUATA_IOS_SIMULATOR_UDID.}"
: "${QUATA_IOS_CHAT_E2E_CONVERSATION_ID:?Set QUATA_IOS_CHAT_E2E_CONVERSATION_ID.}"
: "${QUATA_IOS_CHAT_PROFILE_ONLY:=0}"
: "${QUATA_IOS_CHAT_PROFILE_LISTS_UI_E2E:=0}"
: "${QUATA_IOS_CHAT_PROFILE_CONTENT_UI_E2E:=0}"
: "${QUATA_IOS_CHAT_FEED_OFFICIAL_COMMENTS_UI_E2E:=0}"
: "${QUATA_IOS_CHAT_PROFILE_ROLES_SAFETY_UI_E2E:=0}"
: "${QUATA_IOS_CHAT_OPTIONS_MENU_SURFACE_UI_E2E:=0}"
: "${QUATA_IOS_CHAT_KEYBOARD_MENU_UI_E2E:=0}"
: "${QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E:=0}"
: "${QUATA_IOS_CHAT_ATTACHMENT_PICKER_UI_E2E:=0}"
: "${QUATA_IOS_CHAT_GROUP_SOS_UI_E2E:=0}"
: "${QUATA_IOS_CHAT_GROUP_ADMIN_UI_E2E:=0}"
: "${QUATA_IOS_CHAT_GROUP_MODERATION_UI_E2E:=0}"
: "${QUATA_IOS_CHAT_OPTIONS_MENU_SURFACE_INCLUDE_UNMUTE:=1}"
: "${QUATA_IOS_CHAT_OPTIONS_MENU_SURFACE_UNMUTE_ONLY:=0}"
if [[ "$QUATA_IOS_CHAT_ATTACHMENT_PICKER_UI_E2E" == "1" ]]; then
  : "${QUATA_IOS_CHAT_ATTACHMENT_PICKER_FIXTURE_OPT_IN:?Set QUATA_IOS_CHAT_ATTACHMENT_PICKER_FIXTURE_OPT_IN.}"
  : "${QUATA_IOS_CHAT_ATTACHMENT_PICKER_SOURCE:?Set QUATA_IOS_CHAT_ATTACHMENT_PICKER_SOURCE.}"
  : "${QUATA_IOS_CHAT_ATTACHMENT_PICKER_OUTCOME:=success}"
  : "${QUATA_IOS_CHAT_ATTACHMENT_PICKER_REASON:=attachment_picker_e2e_failure}"
  : "${QUATA_IOS_CHAT_ATTACHMENT_PICKER_PATH:?Set QUATA_IOS_CHAT_ATTACHMENT_PICKER_PATH.}"
  : "${QUATA_IOS_CHAT_ATTACHMENT_PICKER_NAME:?Set QUATA_IOS_CHAT_ATTACHMENT_PICKER_NAME.}"
  : "${QUATA_IOS_CHAT_ATTACHMENT_PICKER_MIME:?Set QUATA_IOS_CHAT_ATTACHMENT_PICKER_MIME.}"
  : "${QUATA_IOS_CHAT_ATTACHMENT_PICKER_MARKER:?Set QUATA_IOS_CHAT_ATTACHMENT_PICKER_MARKER.}"
  : "${QUATA_IOS_CHAT_E2E_MESSAGE_ID:=attachment-picker}"
  : "${QUATA_IOS_CHAT_E2E_MARKER_PROBE:=attachment-picker}"
  : "${QUATA_IOS_CHAT_PROFILE_E2E_MARKER_PROBE:=attachment-picker}"
  : "${QUATA_IOS_CHAT_PROFILE_E2E_PROFILE_ID:=attachment-picker}"
  : "${QUATA_IOS_CHAT_E2E_EDITABLE_MESSAGE_ID:=attachment-picker}"
  : "${QUATA_IOS_CHAT_E2E_EDITABLE_MARKER:=attachment-picker}"
  : "${QUATA_IOS_CHAT_E2E_COMPOSER_MARKER:=attachment-picker}"
  : "${QUATA_IOS_CHAT_E2E_REPLY_MARKER:=attachment-picker}"
  : "${QUATA_IOS_CHAT_E2E_EDIT_MARKER:=attachment-picker}"
  : "${QUATA_IOS_CHAT_E2E_FORWARD_QUERY:=attachment-picker}"
  : "${QUATA_IOS_CHAT_ATTACHMENT_DOCUMENT_PROBE:=attachment-picker}"
  : "${QUATA_IOS_CHAT_ATTACHMENT_AUDIO_PROBE:=attachment-picker}"
  : "${QUATA_IOS_CHAT_ATTACHMENT_IMAGE_PROBE:=attachment-picker}"
  : "${QUATA_IOS_CHAT_ATTACHMENT_VIDEO_PROBE:=attachment-picker}"
elif [[ "$QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E" == "1" ]]; then
  : "${QUATA_IOS_CHAT_ATTACHMENT_DOCUMENT_PROBE:?Set QUATA_IOS_CHAT_ATTACHMENT_DOCUMENT_PROBE.}"
  : "${QUATA_IOS_CHAT_ATTACHMENT_AUDIO_PROBE:?Set QUATA_IOS_CHAT_ATTACHMENT_AUDIO_PROBE.}"
  : "${QUATA_IOS_CHAT_ATTACHMENT_AUDIO_NAME:?Set QUATA_IOS_CHAT_ATTACHMENT_AUDIO_NAME.}"
  : "${QUATA_IOS_CHAT_ATTACHMENT_NEXT_AUDIO_NAME:?Set QUATA_IOS_CHAT_ATTACHMENT_NEXT_AUDIO_NAME.}"
  : "${QUATA_IOS_CHAT_ATTACHMENT_IMAGE_PROBE:?Set QUATA_IOS_CHAT_ATTACHMENT_IMAGE_PROBE.}"
  : "${QUATA_IOS_CHAT_ATTACHMENT_VIDEO_PROBE:?Set QUATA_IOS_CHAT_ATTACHMENT_VIDEO_PROBE.}"
  : "${QUATA_IOS_CHAT_ATTACHMENT_IMAGE_MESSAGE_ID:?Set QUATA_IOS_CHAT_ATTACHMENT_IMAGE_MESSAGE_ID.}"
  : "${QUATA_IOS_CHAT_ATTACHMENT_VIDEO_MESSAGE_ID:?Set QUATA_IOS_CHAT_ATTACHMENT_VIDEO_MESSAGE_ID.}"
  : "${QUATA_IOS_CHAT_AUDIO_RECORDING_MARKER:?Set QUATA_IOS_CHAT_AUDIO_RECORDING_MARKER.}"
  : "${QUATA_IOS_CHAT_E2E_MESSAGE_ID:=attachments-audio}"
  : "${QUATA_IOS_CHAT_E2E_MARKER_PROBE:=attachments-audio}"
  : "${QUATA_IOS_CHAT_PROFILE_E2E_MARKER_PROBE:=attachments-audio}"
  : "${QUATA_IOS_CHAT_PROFILE_E2E_PROFILE_ID:=attachments-audio}"
  : "${QUATA_IOS_CHAT_E2E_EDITABLE_MESSAGE_ID:=attachments-audio}"
  : "${QUATA_IOS_CHAT_E2E_EDITABLE_MARKER:=attachments-audio}"
  : "${QUATA_IOS_CHAT_E2E_COMPOSER_MARKER:=attachments-audio}"
  : "${QUATA_IOS_CHAT_E2E_REPLY_MARKER:=attachments-audio}"
  : "${QUATA_IOS_CHAT_E2E_EDIT_MARKER:=attachments-audio}"
  : "${QUATA_IOS_CHAT_E2E_FORWARD_QUERY:=attachments-audio}"
elif [[ "$QUATA_IOS_CHAT_PROFILE_ONLY" == "1" || "$QUATA_IOS_CHAT_PROFILE_CONTENT_UI_E2E" == "1" || "$QUATA_IOS_CHAT_FEED_OFFICIAL_COMMENTS_UI_E2E" == "1" || "$QUATA_IOS_CHAT_PROFILE_ROLES_SAFETY_UI_E2E" == "1" || "$QUATA_IOS_CHAT_OPTIONS_MENU_SURFACE_UI_E2E" == "1" || "$QUATA_IOS_CHAT_KEYBOARD_MENU_UI_E2E" == "1" || "$QUATA_IOS_CHAT_GROUP_SOS_UI_E2E" == "1" || "$QUATA_IOS_CHAT_GROUP_ADMIN_UI_E2E" == "1" || "$QUATA_IOS_CHAT_GROUP_MODERATION_UI_E2E" == "1" ]]; then
  : "${QUATA_IOS_CHAT_E2E_MESSAGE_ID:?Set QUATA_IOS_CHAT_E2E_MESSAGE_ID.}"
  : "${QUATA_IOS_CHAT_E2E_MARKER_PROBE:?Set QUATA_IOS_CHAT_E2E_MARKER_PROBE.}"
  : "${QUATA_IOS_CHAT_PROFILE_E2E_MARKER_PROBE:?Set QUATA_IOS_CHAT_PROFILE_E2E_MARKER_PROBE.}"
  : "${QUATA_IOS_CHAT_PROFILE_E2E_PROFILE_ID:?Set QUATA_IOS_CHAT_PROFILE_E2E_PROFILE_ID.}"
  : "${QUATA_IOS_CHAT_GROUP_ADMIN_PROFILE_ID:=profile-only}"
  : "${QUATA_IOS_CHAT_GROUP_ADMIN_DISPLAY_NAME:=profile-only}"
  : "${QUATA_IOS_CHAT_GROUP_ADMIN_SEARCH_QUERY:=profile-only}"
  : "${QUATA_IOS_CHAT_GROUP_REMOVE_PROFILE_ID:=profile-only}"
  : "${QUATA_IOS_CHAT_GROUP_REMOVE_DISPLAY_NAME:=profile-only}"
  : "${QUATA_IOS_CHAT_GROUP_REMOVE_SEARCH_QUERY:=profile-only}"
  : "${QUATA_IOS_CHAT_GROUP_BLOCK_PROFILE_ID:=profile-only}"
  : "${QUATA_IOS_CHAT_GROUP_BLOCK_DISPLAY_NAME:=profile-only}"
  : "${QUATA_IOS_CHAT_GROUP_BLOCK_SEARCH_QUERY:=profile-only}"
  : "${QUATA_IOS_CHAT_E2E_EDITABLE_MESSAGE_ID:=profile-only}"
  : "${QUATA_IOS_CHAT_E2E_EDITABLE_MARKER:=profile-only}"
  : "${QUATA_IOS_CHAT_E2E_COMPOSER_MARKER:=keyboard-menu-probe}"
  : "${QUATA_IOS_CHAT_E2E_REPLY_MARKER:=profile-only}"
  : "${QUATA_IOS_CHAT_E2E_EDIT_MARKER:=profile-only}"
  : "${QUATA_IOS_CHAT_E2E_FORWARD_QUERY:=profile-only}"
  : "${QUATA_IOS_CHAT_FEED_COMMENTS_POST_ID:=feed-official-comments}"
  : "${QUATA_IOS_CHAT_FEED_COMMENTS_UI_COMMENT:=feed-official-comments}"
  : "${QUATA_IOS_CHAT_OFFICIAL_COMMENTS_POST_ID:=feed-official-comments}"
  : "${QUATA_IOS_CHAT_OFFICIAL_COMMENTS_UI_COMMENT:=feed-official-comments}"
else
  : "${QUATA_IOS_CHAT_E2E_MESSAGE_ID:?Set QUATA_IOS_CHAT_E2E_MESSAGE_ID.}"
  : "${QUATA_IOS_CHAT_E2E_MARKER_PROBE:?Set QUATA_IOS_CHAT_E2E_MARKER_PROBE.}"
  : "${QUATA_IOS_CHAT_PROFILE_E2E_MARKER_PROBE:?Set QUATA_IOS_CHAT_PROFILE_E2E_MARKER_PROBE.}"
  : "${QUATA_IOS_CHAT_PROFILE_E2E_PROFILE_ID:?Set QUATA_IOS_CHAT_PROFILE_E2E_PROFILE_ID.}"
  : "${QUATA_IOS_CHAT_E2E_EDITABLE_MESSAGE_ID:?Set QUATA_IOS_CHAT_E2E_EDITABLE_MESSAGE_ID.}"
  : "${QUATA_IOS_CHAT_E2E_EDITABLE_MARKER:?Set QUATA_IOS_CHAT_E2E_EDITABLE_MARKER.}"
  : "${QUATA_IOS_CHAT_E2E_COMPOSER_MARKER:?Set QUATA_IOS_CHAT_E2E_COMPOSER_MARKER.}"
  : "${QUATA_IOS_CHAT_E2E_REPLY_MARKER:?Set QUATA_IOS_CHAT_E2E_REPLY_MARKER.}"
  : "${QUATA_IOS_CHAT_E2E_EDIT_MARKER:?Set QUATA_IOS_CHAT_E2E_EDIT_MARKER.}"
  : "${QUATA_IOS_CHAT_E2E_FORWARD_QUERY:?Set QUATA_IOS_CHAT_E2E_FORWARD_QUERY.}"
fi
: "${QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR:=build/reports/ios/chat-actions-notifications}"
: "${QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_RESULT_BUNDLE_DIR:=}"
watchdog="scripts/run-ios-command-watchdog.py"
[[ -f "$watchdog" ]] || { echo "Missing shared iOS command watchdog: $watchdog" >&2; exit 2; }

xctestruns=()
while IFS= read -r xctestrun_path; do
  xctestruns+=("$xctestrun_path")
done < <(find "$QUATA_IOS_DERIVED_DATA_PATH/Build/Products" -name '*.xctestrun' ! -name '*-quata-patched.xctestrun' -type f -print)
[[ "${#xctestruns[@]}" -eq 1 ]] || { echo "Expected exactly one .xctestrun, found ${#xctestruns[@]}" >&2; exit 2; }
xctestrun="${xctestruns[0]}"
mkdir -p "$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR"
patched_xctestrun="$(dirname "$xctestrun")/$(basename "$xctestrun" .xctestrun)-quata-patched.xctestrun"
rm -f "$patched_xctestrun"
cp "$xctestrun" "$patched_xctestrun"
xctestrun="$patched_xctestrun"

redact_diagnostics() {
  /usr/bin/python3 -c '
import re, sys
secret = re.compile(r"(?i)(bearer\\s+|authorization\\s*[:=]\\s*|token\\s*[:=]\\s*|password\\s*[:=]\\s*|apikey\\s*[:=]\\s*)[^\\s,;]+")
for line in sys.stdin:
    print(secret.sub(lambda match: match.group(1) + "[REDACTED]", line), end="")
'
}

timeout_diagnostics() {
  local label="$1" diagnostics="$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR/${label}-timeout-diagnostics.log"
  {
    echo "===== bounded iOS command timeout: $label ====="
    echo "===== selected simulator state ====="
    xcrun simctl list devices | grep -F "$QUATA_IOS_SIMULATOR_UDID" || true
    echo "===== host processes: testmanager / QuataIos ====="
    ps -axo pid,ppid,state,etime,command | grep -E '[t]estmanager|[Q]uataIos' || true
    echo "===== last two minutes: testmanager / QuataIos (redacted) ====="
    xcrun simctl spawn "$QUATA_IOS_SIMULATOR_UDID" log show --last 2m --style compact \
      --predicate 'process == "testmanagerd" OR process == "QuataIos"' 2>&1 | redact_diagnostics
  } > "$diagnostics"
  echo "Watchdog timeout diagnostics: $diagnostics" >&2
}

run_bounded() {
  local label="$1" seconds="$2" log="$3"
  shift 3
  echo "[$label] starting (watchdog ${seconds}s)" >&2
  set +e
  /usr/bin/python3 "$watchdog" --timeout-seconds "$seconds" --log "$log" -- "$@"
  local status=$?
  set -e
  cat "$log"
  if [[ "$status" -eq 124 ]]; then
    timeout_diagnostics "$label"
  fi
  return "$status"
}

set +e
run_bounded bootstatus 120 "$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR/bootstatus.log" \
  xcrun simctl bootstatus "$QUATA_IOS_SIMULATOR_UDID" -b
bootstatus_status=$?
set -e
if [[ "$bootstatus_status" -eq 124 ]]; then
  devices_json="$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR/simulator-devices-after-bootstatus-timeout.json"
  xcrun simctl list devices -j | tee "$devices_json"
  /usr/bin/python3 scripts/check-ios-simulator-booted.py \
    --udid "$QUATA_IOS_SIMULATOR_UDID" < "$devices_json" || exit 124
  echo "bootstatus timed out but selected simulator is Booted: $QUATA_IOS_SIMULATOR_UDID" >&2
elif [[ "$bootstatus_status" -ne 0 ]]; then
  exit "$bootstatus_status"
fi

if [[ "$QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E" == "1" ]]; then
  xcrun simctl privacy "$QUATA_IOS_SIMULATOR_UDID" grant microphone com.quata.ios \
    > "$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR/microphone-permission.log" 2>&1
fi

/usr/bin/python3 - "$xctestrun" "$QUATA_IOS_AUTH_E2E_FILE" "$QUATA_IOS_CHAT_E2E_CONVERSATION_ID" "$QUATA_IOS_CHAT_E2E_MESSAGE_ID" "$QUATA_IOS_CHAT_E2E_MARKER_PROBE" "$QUATA_IOS_CHAT_PROFILE_E2E_MARKER_PROBE" "$QUATA_IOS_CHAT_PROFILE_E2E_PROFILE_ID" "${QUATA_IOS_CHAT_ACTOR_PROFILE_ID:-profile-only}" "${QUATA_IOS_CHAT_PROFILE_FOLLOW_UI_E2E:-0}" "${QUATA_IOS_CHAT_PROFILE_LISTS_UI_E2E:-0}" "${QUATA_IOS_CHAT_PROFILE_CONTENT_UI_E2E:-0}" "${QUATA_IOS_CHAT_FEED_OFFICIAL_COMMENTS_UI_E2E:-0}" "${QUATA_IOS_CHAT_PROFILE_ROLES_SAFETY_UI_E2E:-0}" "${QUATA_IOS_CHAT_OPTIONS_MENU_SURFACE_UI_E2E:-0}" "${QUATA_IOS_CHAT_KEYBOARD_MENU_UI_E2E:-0}" "${QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E:-0}" "${QUATA_IOS_CHAT_ATTACHMENT_PICKER_UI_E2E:-0}" "${QUATA_IOS_CHAT_GROUP_SOS_UI_E2E:-0}" "${QUATA_IOS_CHAT_GROUP_ADMIN_UI_E2E:-0}" "${QUATA_IOS_CHAT_GROUP_MODERATION_UI_E2E:-0}" "${QUATA_IOS_CHAT_GROUP_ADMIN_PROFILE_ID:-profile-only}" "${QUATA_IOS_CHAT_GROUP_ADMIN_DISPLAY_NAME:-profile-only}" "${QUATA_IOS_CHAT_GROUP_ADMIN_SEARCH_QUERY:-profile-only}" "${QUATA_IOS_CHAT_GROUP_REMOVE_PROFILE_ID:-profile-only}" "${QUATA_IOS_CHAT_GROUP_REMOVE_DISPLAY_NAME:-profile-only}" "${QUATA_IOS_CHAT_GROUP_REMOVE_SEARCH_QUERY:-profile-only}" "${QUATA_IOS_CHAT_GROUP_BLOCK_PROFILE_ID:-profile-only}" "${QUATA_IOS_CHAT_GROUP_BLOCK_DISPLAY_NAME:-profile-only}" "${QUATA_IOS_CHAT_GROUP_BLOCK_SEARCH_QUERY:-profile-only}" "$QUATA_IOS_CHAT_E2E_EDITABLE_MESSAGE_ID" "$QUATA_IOS_CHAT_E2E_EDITABLE_MARKER" "$QUATA_IOS_CHAT_E2E_COMPOSER_MARKER" "$QUATA_IOS_CHAT_E2E_REPLY_MARKER" "$QUATA_IOS_CHAT_E2E_EDIT_MARKER" "$QUATA_IOS_CHAT_E2E_FORWARD_QUERY" "$QUATA_IOS_CHAT_ATTACHMENT_DOCUMENT_PROBE" "$QUATA_IOS_CHAT_ATTACHMENT_AUDIO_PROBE" "${QUATA_IOS_CHAT_ATTACHMENT_AUDIO_NAME:-}" "${QUATA_IOS_CHAT_ATTACHMENT_NEXT_AUDIO_NAME:-}" "$QUATA_IOS_CHAT_ATTACHMENT_IMAGE_PROBE" "$QUATA_IOS_CHAT_ATTACHMENT_VIDEO_PROBE" "${QUATA_IOS_CHAT_ATTACHMENT_IMAGE_MESSAGE_ID:-}" "${QUATA_IOS_CHAT_ATTACHMENT_VIDEO_MESSAGE_ID:-}" "${QUATA_IOS_CHAT_AUDIO_RECORDING_MARKER:-}" "${QUATA_IOS_CHAT_PROFILE_CONTENT_POST_ID:-profile-content}" "${QUATA_IOS_CHAT_PROFILE_CONTENT_COMMENT_ID:-profile-content}" "${QUATA_IOS_CHAT_PROFILE_CONTENT_ATTACHMENT_ID:-profile-content}" "${QUATA_IOS_CHAT_PROFILE_CONTENT_UI_COMMENT:-profile-content}" "${QUATA_IOS_CHAT_FEED_COMMENTS_POST_ID:-feed-official-comments}" "${QUATA_IOS_CHAT_FEED_COMMENTS_UI_COMMENT:-feed-official-comments}" "${QUATA_IOS_CHAT_OFFICIAL_COMMENTS_POST_ID:-feed-official-comments}" "${QUATA_IOS_CHAT_OFFICIAL_COMMENTS_UI_COMMENT:-feed-official-comments}" "${QUATA_IOS_CHAT_ATTACHMENT_PICKER_FIXTURE_OPT_IN:-}" "${QUATA_IOS_CHAT_ATTACHMENT_PICKER_SOURCE:-}" "${QUATA_IOS_CHAT_ATTACHMENT_PICKER_OUTCOME:-success}" "${QUATA_IOS_CHAT_ATTACHMENT_PICKER_REASON:-attachment_picker_e2e_failure}" "${QUATA_IOS_CHAT_ATTACHMENT_PICKER_PATH:-}" "${QUATA_IOS_CHAT_ATTACHMENT_PICKER_NAME:-}" "${QUATA_IOS_CHAT_ATTACHMENT_PICKER_MIME:-}" "${QUATA_IOS_CHAT_ATTACHMENT_PICKER_MARKER:-}" <<'PY'
import os, plistlib, sys
path, credentials, conversation, message, marker, profile_marker, profile_id, actor_profile_id, profile_follow, profile_lists, profile_content, feed_official_comments, profile_roles_safety, menu_surface, keyboard_menu, attachments_audio, attachment_picker, group_sos, group_admin, group_moderation, group_admin_profile_id, group_admin_display_name, group_admin_search_query, group_remove_profile_id, group_remove_display_name, group_remove_search_query, group_block_profile_id, group_block_display_name, group_block_search_query, editable_message, editable_marker, composer, reply, edit, forward_query, attachment_document, attachment_audio, attachment_audio_name, attachment_next_audio_name, attachment_image, attachment_video, attachment_image_message, attachment_video_message, audio_recording_marker, profile_content_post, profile_content_comment, profile_content_attachment, profile_content_ui_comment, feed_comments_post, feed_comments_ui_comment, official_comments_post, official_comments_ui_comment, picker_opt_in, picker_source, picker_outcome, picker_reason, picker_path, picker_name, picker_mime, picker_marker = sys.argv[1:]
with open(path, 'rb') as f:
    data = plistlib.load(f)
matched = set()
def patch_target(target, hint=''):
    name = f"{hint} {target.get('TestTargetName', '')} {target.get('BlueprintName', '')}"
    env = target.setdefault('EnvironmentVariables', {})
    if 'QuataIosTests' in name:
        env['QUATA_IOS_AUTH_E2E_FILE'] = credentials
        matched.add('seed')
    if 'QuataIosUITests' in name:
        env['QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_UI_E2E'] = '1'
        env['QUATA_IOS_CHAT_PROFILE_UI_E2E'] = '1'
        env['QUATA_IOS_CHAT_PROFILE_FOLLOW_UI_E2E'] = profile_follow
        env['QUATA_IOS_CHAT_PROFILE_LISTS_UI_E2E'] = profile_lists
        env['QUATA_IOS_CHAT_PROFILE_CONTENT_UI_E2E'] = profile_content
        env['QUATA_IOS_CHAT_FEED_OFFICIAL_COMMENTS_UI_E2E'] = feed_official_comments
        env['QUATA_IOS_CHAT_PROFILE_ROLES_SAFETY_UI_E2E'] = profile_roles_safety
        env['QUATA_IOS_CHAT_OPTIONS_MENU_SURFACE_UI_E2E'] = menu_surface
        env['QUATA_IOS_CHAT_KEYBOARD_MENU_UI_E2E'] = keyboard_menu
        env['QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E'] = attachments_audio
        env['QUATA_IOS_CHAT_ATTACHMENT_PICKER_UI_E2E'] = attachment_picker
        env['QUATA_IOS_CHAT_GROUP_SOS_UI_E2E'] = group_sos
        env['QUATA_IOS_CHAT_GROUP_ADMIN_UI_E2E'] = group_admin
        env['QUATA_IOS_CHAT_GROUP_MODERATION_UI_E2E'] = group_moderation
        env['QUATA_IOS_CHAT_GROUP_ADMIN_PROFILE_ID'] = group_admin_profile_id
        env['QUATA_IOS_CHAT_GROUP_ADMIN_DISPLAY_NAME'] = group_admin_display_name
        env['QUATA_IOS_CHAT_GROUP_ADMIN_SEARCH_QUERY'] = group_admin_search_query
        env['QUATA_IOS_CHAT_GROUP_REMOVE_PROFILE_ID'] = group_remove_profile_id
        env['QUATA_IOS_CHAT_GROUP_REMOVE_DISPLAY_NAME'] = group_remove_display_name
        env['QUATA_IOS_CHAT_GROUP_REMOVE_SEARCH_QUERY'] = group_remove_search_query
        env['QUATA_IOS_CHAT_GROUP_BLOCK_PROFILE_ID'] = group_block_profile_id
        env['QUATA_IOS_CHAT_GROUP_BLOCK_DISPLAY_NAME'] = group_block_display_name
        env['QUATA_IOS_CHAT_GROUP_BLOCK_SEARCH_QUERY'] = group_block_search_query
        env['QUATA_IOS_CHAT_E2E_CONVERSATION_ID'] = conversation
        env['QUATA_IOS_CHAT_E2E_MESSAGE_ID'] = message
        env['QUATA_IOS_CHAT_E2E_MARKER_PROBE'] = marker
        env['QUATA_IOS_CHAT_PROFILE_E2E_MARKER_PROBE'] = profile_marker
        env['QUATA_IOS_CHAT_PROFILE_E2E_PROFILE_ID'] = profile_id
        env['QUATA_IOS_CHAT_ACTOR_PROFILE_ID'] = actor_profile_id
        env['QUATA_IOS_CHAT_E2E_EDITABLE_MESSAGE_ID'] = editable_message
        env['QUATA_IOS_CHAT_E2E_EDITABLE_MARKER'] = editable_marker
        env['QUATA_IOS_CHAT_E2E_COMPOSER_MARKER'] = composer
        env['QUATA_IOS_CHAT_E2E_REPLY_MARKER'] = reply
        env['QUATA_IOS_CHAT_E2E_EDIT_MARKER'] = edit
        env['QUATA_IOS_CHAT_E2E_FORWARD_QUERY'] = forward_query
        env['QUATA_IOS_CHAT_ATTACHMENT_DOCUMENT_PROBE'] = attachment_document
        env['QUATA_IOS_CHAT_ATTACHMENT_AUDIO_PROBE'] = attachment_audio
        env['QUATA_IOS_CHAT_ATTACHMENT_AUDIO_NAME'] = attachment_audio_name
        env['QUATA_IOS_CHAT_ATTACHMENT_NEXT_AUDIO_NAME'] = attachment_next_audio_name
        env['QUATA_IOS_CHAT_ATTACHMENT_IMAGE_PROBE'] = attachment_image
        env['QUATA_IOS_CHAT_ATTACHMENT_VIDEO_PROBE'] = attachment_video
        env['QUATA_IOS_CHAT_ATTACHMENT_IMAGE_MESSAGE_ID'] = attachment_image_message
        env['QUATA_IOS_CHAT_ATTACHMENT_VIDEO_MESSAGE_ID'] = attachment_video_message
        env['QUATA_IOS_CHAT_AUDIO_RECORDING_MARKER'] = audio_recording_marker
        env['QUATA_IOS_CHAT_PROFILE_CONTENT_POST_ID'] = profile_content_post
        env['QUATA_IOS_CHAT_PROFILE_CONTENT_COMMENT_ID'] = profile_content_comment
        env['QUATA_IOS_CHAT_PROFILE_CONTENT_ATTACHMENT_ID'] = profile_content_attachment
        env['QUATA_IOS_CHAT_PROFILE_CONTENT_UI_COMMENT'] = profile_content_ui_comment
        env['QUATA_IOS_CHAT_FEED_COMMENTS_POST_ID'] = feed_comments_post
        env['QUATA_IOS_CHAT_FEED_COMMENTS_UI_COMMENT'] = feed_comments_ui_comment
        env['QUATA_IOS_CHAT_OFFICIAL_COMMENTS_POST_ID'] = official_comments_post
        env['QUATA_IOS_CHAT_OFFICIAL_COMMENTS_UI_COMMENT'] = official_comments_ui_comment
        for key in [
            'QUATA_IOS_CHAT_PROFILE_CONTENT_REPLY_COMMENT',
            'QUATA_IOS_CHAT_FEED_COMMENTS_COMMENT_ID',
            'QUATA_IOS_CHAT_FEED_COMMENTS_REPLY_COMMENT',
            'QUATA_IOS_CHAT_OFFICIAL_COMMENTS_COMMENT_ID',
            'QUATA_IOS_CHAT_OFFICIAL_COMMENTS_REPLY_COMMENT',
        ]:
            value = os.environ.get(key)
            if value is not None:
                env[key] = value
        env['QUATA_IOS_CHAT_ATTACHMENT_PICKER_FIXTURE_OPT_IN'] = picker_opt_in
        env['QUATA_IOS_CHAT_ATTACHMENT_PICKER_SOURCE'] = picker_source
        env['QUATA_IOS_CHAT_ATTACHMENT_PICKER_OUTCOME'] = picker_outcome
        env['QUATA_IOS_CHAT_ATTACHMENT_PICKER_REASON'] = picker_reason
        env['QUATA_IOS_CHAT_ATTACHMENT_PICKER_PATH'] = picker_path
        env['QUATA_IOS_CHAT_ATTACHMENT_PICKER_NAME'] = picker_name
        env['QUATA_IOS_CHAT_ATTACHMENT_PICKER_MIME'] = picker_mime
        env['QUATA_IOS_CHAT_ATTACHMENT_PICKER_MARKER'] = picker_marker
        matched.add('ui')
for configuration in data.get('TestConfigurations', []):
    for target in configuration.get('TestTargets', []):
        patch_target(target)
for key, target in data.items():
    if isinstance(target, dict):
        patch_target(target, key)
if matched != {'seed', 'ui'}:
    raise SystemExit(f'xctestrun targets missing: {matched}')
with open(path, 'wb') as f:
    plistlib.dump(data, f)
PY

seed='QuataIosTests/QuataIosAuthenticatedSessionSeederTests/testSeedAuthenticatedSessionForVisualGates'
profile='QuataIosUITests/QuataIosAuthenticatedChatActionsNotificationsUITests/testProfileEntryFromChatOpensPublicProfileAndReturns'
profile_follow='QuataIosUITests/QuataIosAuthenticatedChatActionsNotificationsUITests/testProfileFollowFromChatTogglesSharedPublicProfileAction'
profile_follow_method='testProfileFollowFromChatTogglesSharedPublicProfileAction'
profile_lists='QuataIosUITests/QuataIosAuthenticatedChatActionsNotificationsUITests/testProfileFollowListsFromChatOpenAndReturn'
profile_content='QuataIosUITests/QuataIosAuthenticatedChatActionsNotificationsUITests/testProfileContentFromChatUsesSharedPublicProfileSurface'
feed_official_comments='QuataIosUITests/QuataIosAuthenticatedChatActionsNotificationsUITests/testFeedAndOfficialCommentsUseSharedEmojiPicker'
profile_roles_safety='QuataIosUITests/QuataIosAuthenticatedChatActionsNotificationsUITests/testProfileRolesAndSafetyFromChatUseSharedPublicProfileControls'
menu_surface='QuataIosUITests/QuataIosAuthenticatedChatActionsNotificationsUITests/testOptionsMenuSurfaceUsesSharedOpaqueHeaderSurface'
menu_surface_unmute='QuataIosUITests/QuataIosAuthenticatedChatActionsNotificationsUITests/testOptionsMenuSurfaceUnmutesFromSharedOpaqueHeaderSurface'
keyboard_menu='QuataIosUITests/QuataIosAuthenticatedChatActionsNotificationsUITests/testKeyboardAndSelectedActionBarUseSharedChatChrome'
attachments_audio='QuataIosUITests/QuataIosAuthenticatedChatActionsNotificationsUITests/testAttachmentsAndAudioExposeSharedAnchors'
attachment_picker='QuataIosUITests/QuataIosAuthenticatedChatActionsNotificationsUITests/testAttachmentPickerFixtureUsesSharedComposerAnchors'
group_sos='QuataIosUITests/QuataIosAuthenticatedChatActionsNotificationsUITests/testGroupMenuAndSosMessagesExposeSharedAnchors'
group_admin='QuataIosUITests/QuataIosAuthenticatedChatActionsNotificationsUITests/testGroupAdminPromotesParticipantThroughSharedMemberMenu'
group_moderation='QuataIosUITests/QuataIosAuthenticatedChatActionsNotificationsUITests/testGroupModerationRemovesAndBlocksParticipantsThroughSharedMemberMenu'
ui='QuataIosUITests/QuataIosAuthenticatedChatActionsNotificationsUITests/testComposerReplyEditAndSelectedActionsUseSharedChatSurface'
profile_method='testProfileEntryFromChatOpensPublicProfileAndReturns'
profile_lists_method='testProfileFollowListsFromChatOpenAndReturn'
profile_content_method='testProfileContentFromChatUsesSharedPublicProfileSurface'
feed_official_comments_method='testFeedAndOfficialCommentsUseSharedEmojiPicker'
profile_roles_safety_method='testProfileRolesAndSafetyFromChatUseSharedPublicProfileControls'
menu_surface_method='testOptionsMenuSurfaceUsesSharedOpaqueHeaderSurface'
menu_surface_unmute_method='testOptionsMenuSurfaceUnmutesFromSharedOpaqueHeaderSurface'
keyboard_menu_method='testKeyboardAndSelectedActionBarUseSharedChatChrome'
attachments_audio_method='testAttachmentsAndAudioExposeSharedAnchors'
attachment_picker_method='testAttachmentPickerFixtureUsesSharedComposerAnchors'
group_sos_method='testGroupMenuAndSosMessagesExposeSharedAnchors'
group_admin_method='testGroupAdminPromotesParticipantThroughSharedMemberMenu'
group_moderation_method='testGroupModerationRemovesAndBlocksParticipantsThroughSharedMemberMenu'
ui_method='testComposerReplyEditAndSelectedActionsUseSharedChatSurface'

run_and_require() {
  local selected="$1" method="$2" log="$3"
  local result_args=()
  if [[ -n "$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_RESULT_BUNDLE_DIR" ]]; then
    mkdir -p "$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_RESULT_BUNDLE_DIR"
    local result_bundle="$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_RESULT_BUNDLE_DIR/${method}.xcresult"
    rm -rf "$result_bundle"
    result_args=(-resultBundlePath "$result_bundle")
  fi
  set +e
  run_bounded "$method" 480 "$log" \
    xcodebuild test-without-building -xctestrun "$xctestrun" \
    -destination "platform=iOS Simulator,id=$QUATA_IOS_SIMULATOR_UDID" "${result_args[@]}" -only-testing:"$selected"
  local xcode_status=$?
  set -e
  /usr/bin/python3 scripts/check-ios-xctest-executed.py \
    --method "$method" --log "$log" --require-terminal-success-marker || exit 1
  if [[ "$xcode_status" -ne 0 ]]; then
    echo "xcodebuild exited $xcode_status after $method emitted a terminal success marker; accepting XCTest success and preserving log for diagnostics." >&2
  fi
  printf 'PASS_EXECUTED:%s\n' "$method" | tee -a "$log"
}

run_and_require "$seed" testSeedAuthenticatedSessionForVisualGates "$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR/seed.log"
if [[ "$QUATA_IOS_CHAT_OPTIONS_MENU_SURFACE_UI_E2E" == "1" ]]; then
  if [[ "$QUATA_IOS_CHAT_OPTIONS_MENU_SURFACE_UNMUTE_ONLY" == "1" ]]; then
    run_and_require "$menu_surface_unmute" "$menu_surface_unmute_method" "$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR/menu-surface-unmute.log"
  else
    run_and_require "$menu_surface" "$menu_surface_method" "$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR/menu-surface.log"
    if [[ "$QUATA_IOS_CHAT_OPTIONS_MENU_SURFACE_INCLUDE_UNMUTE" == "1" ]]; then
      run_and_require "$menu_surface_unmute" "$menu_surface_unmute_method" "$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR/menu-surface-unmute.log"
    fi
  fi
elif [[ "$QUATA_IOS_CHAT_KEYBOARD_MENU_UI_E2E" == "1" ]]; then
  run_and_require "$keyboard_menu" "$keyboard_menu_method" "$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR/keyboard-menu.log"
elif [[ "$QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E" == "1" ]]; then
  run_and_require "$attachments_audio" "$attachments_audio_method" "$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR/attachments-audio.log"
elif [[ "$QUATA_IOS_CHAT_ATTACHMENT_PICKER_UI_E2E" == "1" ]]; then
  run_and_require "$attachment_picker" "$attachment_picker_method" "$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR/attachment-picker.log"
elif [[ "$QUATA_IOS_CHAT_GROUP_SOS_UI_E2E" == "1" ]]; then
  run_and_require "$group_sos" "$group_sos_method" "$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR/group-sos.log"
elif [[ "$QUATA_IOS_CHAT_GROUP_ADMIN_UI_E2E" == "1" ]]; then
  run_and_require "$group_admin" "$group_admin_method" "$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR/group-admin.log"
elif [[ "$QUATA_IOS_CHAT_GROUP_MODERATION_UI_E2E" == "1" ]]; then
  run_and_require "$group_moderation" "$group_moderation_method" "$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR/group-moderation.log"
elif [[ "$QUATA_IOS_CHAT_PROFILE_LISTS_UI_E2E" == "1" ]]; then
  run_and_require "$profile_lists" "$profile_lists_method" "$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR/profile-lists.log"
elif [[ "$QUATA_IOS_CHAT_PROFILE_CONTENT_UI_E2E" == "1" ]]; then
  run_and_require "$profile_content" "$profile_content_method" "$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR/profile-content.log"
elif [[ "$QUATA_IOS_CHAT_FEED_OFFICIAL_COMMENTS_UI_E2E" == "1" ]]; then
  run_and_require "$feed_official_comments" "$feed_official_comments_method" "$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR/feed-official-comments.log"
elif [[ "$QUATA_IOS_CHAT_PROFILE_ROLES_SAFETY_UI_E2E" == "1" ]]; then
  run_and_require "$profile_roles_safety" "$profile_roles_safety_method" "$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR/profile-roles-safety.log"
elif [[ "${QUATA_IOS_CHAT_PROFILE_FOLLOW_UI_E2E:-0}" == "1" ]]; then
  run_and_require "$profile_follow" "$profile_follow_method" "$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR/profile-follow.log"
else
  run_and_require "$profile" "$profile_method" "$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR/profile.log"
fi
if [[ "$QUATA_IOS_CHAT_PROFILE_ONLY" != "1" && "$QUATA_IOS_CHAT_PROFILE_CONTENT_UI_E2E" != "1" && "$QUATA_IOS_CHAT_FEED_OFFICIAL_COMMENTS_UI_E2E" != "1" && "$QUATA_IOS_CHAT_PROFILE_ROLES_SAFETY_UI_E2E" != "1" && "$QUATA_IOS_CHAT_OPTIONS_MENU_SURFACE_UI_E2E" != "1" && "$QUATA_IOS_CHAT_KEYBOARD_MENU_UI_E2E" != "1" && "$QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E" != "1" && "$QUATA_IOS_CHAT_ATTACHMENT_PICKER_UI_E2E" != "1" && "$QUATA_IOS_CHAT_GROUP_SOS_UI_E2E" != "1" && "$QUATA_IOS_CHAT_GROUP_ADMIN_UI_E2E" != "1" && "$QUATA_IOS_CHAT_GROUP_MODERATION_UI_E2E" != "1" ]]; then
  run_and_require "$ui" "$ui_method" "$QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR/ui.log"
fi
echo "CHAT_ACTIONS_NOTIFICATIONS_IOS_UI_GATE_PASSED" >&2
