#!/system/bin/sh
# SDK-owned lifecycle entry. Called only for the actual init event, never by a page.
# KSU reference: 64833c005eca6a8dc3d73ec2d37d84c37e0d9ad2, init_event.rs.
set -u
event=${1:-}
case "$event" in post-fs-data|post-mount|service|boot-completed|prepare-pending|commit-pending|state-pending|recover-pending|hot-pending|state-hot) ;; *) exit 64 ;; esac
bin=/data/adb/maplesu/bin
working=/data/adb/maplesu
modules=/data/adb/modules
updates=${modules}_update
owners=$modules/.maplesu-ultra
state=$working/module-boot/events
busybox=$bin/busybox
runner=$bin/module-run.sh
export ASH_STANDALONE=1
export PATH="${PATH:-/system/bin:/system/xbin}:$bin"
[ "$(id -u)" = 0 ] || exit 77
[ -x "$busybox" ] && [ -f "$runner" ] && [ ! -L "$runner" ] || exit 69
for parent in /data/adb "$working" "$working/module-boot" "$state" "$modules" "$owners"; do
  [ ! -L "$parent" ] || exit 73
done
mkdir -p "$state" || exit 73
chmod 0700 "$working/module-boot" "$state" || exit 73
[ ! -L "$state/lock" ] || exit 73
boot=$(cat /proc/sys/kernel/random/boot_id) || exit 74
now() { "$busybox" awk '{printf "%.0f", $1 * 1000}' /proc/uptime; }
result=0
record() {
  local code=$1; shift
  printf 'MAPLESU_BOOT_RESULT:%s:%s:%s\n' "$event" "$code" "$*"
  if [ "$code" -ne 0 ] && [ "$result" -eq 0 ]; then result=$code; fi
}
receipt() {
  local stage=$1 code=$2 status=$3 temporary
  temporary=$(mktemp "$state/.event.XXXXXX") || return 73
  printf '%s %s %s %s\n' "$boot" "$code" "$status" "$(now)" > "$temporary" &&
    chmod 0600 "$temporary" && mv -f "$temporary" "$state/$stage"
}
seen() {
  local saved_boot= saved_code= saved_status= saved_time=
  [ ! -L "$state/$1" ] || return 73
  if [ -e "$state/$1" ] && [ ! -f "$state/$1" ]; then return 73; fi
  if [ -f "$state/$1" ]; then read -r saved_boot saved_code saved_status saved_time < "$state/$1" || :; fi
  [ "$saved_boot" = "$boot" ] || return 1
  case "$saved_code" in ''|*[!0-9]*) return 73 ;; esac
  [ "$saved_code" -le 255 ] || return 73
  case "$saved_status" in started|done) ;; *) return 73 ;; esac
  case "$saved_time" in ''|*[!0-9]*) return 73 ;; esac
  printf 'MAPLESU_BOOT_DUPLICATE:%s:%s:%s\n' "$1" "$saved_code" "$saved_status"
  duplicate_code=$saved_code
  return 0
}
owned() {
  local directory=$1 id=${1##*/} identity owner revision
  # The root-owned installation receipt is authoritative. Some compatible
  # modules deliberately remove module.prop from their service hook.
  [ -d "$directory" ] && [ ! -L "$directory" ] || return 1
  case "$id" in [a-zA-Z][a-zA-Z0-9._-]*) ;; *) return 1 ;; esac
  case "$id" in *[!a-zA-Z0-9._-]*) return 1 ;; esac
  identity=$(stat -c '%d-%i' "$directory") || return 1
  owner=$owners/$identity
  if [ ! -e "$owner" ] && [ ! -L "$owner" ]; then owner=$directory/.maplesu-ultra; fi
  [ -f "$owner" ] && [ ! -L "$owner" ] && [ "$(stat -c '%u:%h:%s' "$owner")" = 0:1:65 ] || return 1
  revision=$(cat "$owner") || return 1
  case "$revision" in *[!0-9a-f]*) return 1 ;; esac
  [ "${#revision}" = 64 ]
}
valid_id() {
  case "$1" in [a-zA-Z][a-zA-Z0-9._-]*) ;; *) return 64 ;; esac
  case "$1" in *[!a-zA-Z0-9._-]*) return 64 ;; esac
}
tree_identity() { if [ -e "$1" ] || [ -L "$1" ]; then stat -c '%d:%i' "$1"; fi; }
owner_key() { local identity; identity=$(stat -c '%d-%i' "$1") || return; printf '%s/%s\n' "$owners" "$identity"; }
owner_file() {
  local file
  file=$(owner_key "$1" 2>/dev/null) || file=$1/.maplesu-ultra
  if [ ! -e "$file" ] && [ ! -L "$file" ]; then file=$1/.maplesu-ultra; fi
  printf '%s\n' "$file"
}
state_sync() { "$bin/maplesu-tools" module-sync "$@"; }
refresh_initrc() {
  local code
  "$bin/maplesu-tools" module-initrc 9>&-
  code=$?
  # This is a separate next-boot effect, not a rollback of the module commit.
  if [ "$code" -ne 0 ]; then printf 'MODULE_INITRC_REFRESH_FAILED:%s\n' "$code" >&2; fi
  return 0
}
sync_tree() { "$bin/maplesu-tools" module-sync --tree "$@"; }
publish_file() {
  local source_parent=${1%/*} destination_parent=${2%/*}
  state_sync "$1" && mv -f "$1" "$2" || return
  if [ "$source_parent" = "$destination_parent" ]; then state_sync "$destination_parent"
  else state_sync "$source_parent" "$destination_parent"; fi
}
drop_owner() { if [ -d "$1" ] && [ ! -L "$1" ]; then rm -f -- "$(owner_key "$1")" "$(owner_key "$1").tmp"; fi; }
drop_owner_durable() {
  local file
  if [ -d "$1" ] && [ ! -L "$1" ]; then
    file=$(owner_key "$1") || return
    rm -f -- "$file" "$file.tmp" || return
    if [ -d "$owners" ]; then state_sync "$owners"; fi
  fi
}
put() { printf '%s\n' "$2" > "$1.tmp" && publish_file "$1.tmp" "$1"; }
retire_transaction() { rm -rf -- "$t" && state_sync "$working/module-transactions"; }
unpublished_setup() {
  local file
  # Before origin is published, only mkdir/chmod and its atomic temporary exist.
  for file in "$t"/* "$t"/.[!.]* "$t"/..?*; do
    [ -e "$file" ] || [ -L "$file" ] || continue
    [ "$file" = "$t/origin.tmp" ] && [ -f "$file" ] && [ ! -L "$file" ] || return 1
    case "$(cat "$file")" in pending|merge) ;; *) return 1 ;; esac
  done
}
live_changed() {
  local prefix=${MAPLESU_TREE_CHANGED_PREFIX:-} id=${1##*/}
  [ "${1%/*}" = "$modules" ] || return 0
  case "$prefix" in MAPLESU_TREE_CHANGED:????????-????-????-????-????????????:) ;; *) return 0 ;; esac
  valid_id "$id" || return 0
  printf '%s%s\n' "$prefix" "$id"
}
move_tree() {
  [ ! -e "$2" ] && [ ! -L "$2" ] || return 73
  mv -Tn -- "$1" "$2" && [ ! -e "$1" ] && [ ! -L "$1" ] || return
  live_changed "$1"; live_changed "$2"
  if [ "${1%/*}" = "${2%/*}" ]; then state_sync "${2%/*}"
  else state_sync "${1%/*}" "${2%/*}"; fi
}
write_owner() {
  local file
  mkdir -p "$owners" && chmod 0700 "$owners" && state_sync "$modules" || return
  file=$(owner_key "$1") || return
  [ ! -L "$file" ] || return 73
  printf '%s\n' "$2" > "$file.tmp" && chown 0:0 "$file.tmp" && chmod 0600 "$file.tmp" && publish_file "$file.tmp" "$file"
}
pending_paths() {
  valid_id "$1" || return
  d=$modules/$1; p=$updates/$1; t=$working/module-transactions/$1
  for parent in "$updates" "$working/module-transactions"; do [ ! -L "$parent" ] || return 73; done
  [ ! -L "$t" ] || return 73
}
pending_prepare() (
  set -eu
  id=$1; source=$2; next=$3; prior_revision=$4; prior_metadata=$5; enabled=$6; token=$7; shift 7
  pending_paths "$id"
  case "$next" in *[!0-9a-f]*) exit 64 ;; esac; [ "$(printf %s "$next" | wc -c)" = 64 ]
  case "$token" in *[!0-9a-f]*) exit 64 ;; esac; [ "$(printf %s "$token" | wc -c)" = 32 ]
  [ -d "$source" ] && [ ! -L "$source" ]
  (
  "$busybox" flock 9 || exit 73
  mkdir -p "$updates" "$working/module-transactions" "$modules"
  state_sync "$working" "${modules%/*}"
  [ ! -e "$t" ] && [ ! -L "$t" ]
  if [ -L "$d" ]; then
    allowed=0; target=$(readlink "$d")
    for legacy in "$@"; do if [ "$target" = "$legacy" ]; then allowed=1; fi; done
    [ "$allowed" = 1 ]
  elif [ -e "$d" ]; then
    [ -d "$d" ]; owner=$(owner_file "$d"); [ ! -L "$owner" ]
    if [ -n "$prior_revision" ]; then
      [ "$(cat "$owner" 2>/dev/null || :)" = "$prior_revision" ]
    else
      [ ! -e "$owner" ] && [ -n "$prior_metadata" ] && [ ! -L "$d/module.prop" ]
      [ "$(sha256sum "$d/module.prop" | cut -d' ' -f1)" = "$prior_metadata" ]
    fi
  fi
  [ ! -L "$p" ]; if [ -e "$p" ]; then [ -d "$p" ]; fi
  mkdir "$t"; chmod 0700 "$t"
  state_sync "$working/module-transactions"
  put "$t/origin" pending
  put "$t/boot-id" "$boot"
  put "$t/next" "$next"
  put "$t/catalog-token" "$token"
  printf '%s\n' "$@" > "$t/legacy-targets.tmp"; publish_file "$t/legacy-targets.tmp" "$t/legacy-targets"
  put "$t/live" "$(tree_identity "$d")"
  put "$t/live-owner" "$(cat "$(owner_file "$d")" 2>/dev/null || :)"
  put "$t/pending" "$(tree_identity "$p")"
  mkdir "$t/new"
  put "$t/candidate" "$(tree_identity "$t/new")"
  # No live or pending tree moves before this complete journal is published.
  put "$t/layout" pending-v2
  ) 9> "$state/lock"
  cp -a "$source/." "$t/new/"
  rm -f "$t/new/.maplesu-ultra"
  write_owner "$t/new" "$next"
  chown 0:0 "$t/new"; chmod 0755 "$t/new"
  (
  "$busybox" flock 9 || exit 73
  [ "$(cat "$t/layout")" = pending-v2 ] && [ "$(cat "$t/next")" = "$next" ]
  [ "$(cat "$t/catalog-token")" = "$token" ]
  [ "$(tree_identity "$t/new")" = "$(cat "$t/candidate")" ]
  [ "$(tree_identity "$d")" = "$(cat "$t/live")" ]
  [ "$(tree_identity "$p")" = "$(cat "$t/pending")" ]
  if [ -e "$p" ]; then move_tree "$p" "$t/previous-update"; fi
  move_tree "$t/new" "$p"
  [ ! -L "$p/disable" ] && [ ! -L "$p/remove" ]
  if [ -e "$d" ]; then
    if [ -e "$d/disable" ]; then : > "$p/disable"; else rm -f "$p/disable"; fi
    if [ -e "$d/remove" ]; then : > "$p/remove"; else rm -f "$p/remove"; fi
  elif [ "$enabled" = true ]; then rm -f "$p/disable"; else : > "$p/disable"; fi
  [ -f "$p/module.prop" ] && [ ! -L "$p/module.prop" ]
  ) 9> "$state/lock"
)
pending_durable() {
  local file
  sync_tree "$p" || return
  file=$(owner_file "$p") || return
  state_sync "$file" "${file%/*}" "$updates" || return
  for file in "$d/module.prop" "$d/update"; do
    if [ -e "$file" ] || [ -L "$file" ]; then state_sync "$file" || return; fi
  done
  state_sync "$d" "$modules" "$t" "$working/module-transactions" "$working" "${modules%/*}" || return
  file=$(owner_file "$d") || return
  if [ -f "$file" ] && [ ! -L "$file" ]; then state_sync "$file" "${file%/*}" || return; fi
  if [ -f "$t/meta-target" ]; then state_sync /data/adb || return; fi
}
pending_state() (
  set -eu
  pending_paths "$1"; next=$2; token=$3
  # No surviving descriptor protects inode lifetime across separate IPC calls.
  [ -d "$t" ] || { printf 'MAPLESU_PENDING_COMMIT_UNKNOWN\n'; exit 75; }
  [ "$(cat "$t/layout")" = pending-v2 ] || exit 73
  [ "$(cat "$t/catalog-token")" = "$token" ] && [ "$(cat "$t/next")" = "$next" ] || exit 73
  [ ! -L "$t/ready" ] || exit 73
  [ -e "$t/ready" ] || exit 1
  [ -f "$t/ready" ] && [ "$(cat "$t/ready")" = "$token" ] || exit 73
  # A committed but externally changed tree is a conflict, never proof of rollback.
  [ "$(tree_identity "$p")" = "$(cat "$t/candidate")" ] && owned "$p" || exit 73
  [ "$(cat "$(owner_file "$p")")" = "$next" ] || exit 73
  # A failed reply may follow rename but precede its durability barrier.
  pending_durable
  state_sync "$t/ready" "$t"
  printf 'MAPLESU_PENDING_COMMITTED\n'
)
pending_commit() (
  set -eu
  pending_paths "$1"; next=$2; token=$3; metamodule=${4:-false}
  [ "$(cat "$t/layout")" = pending-v2 ]
  [ "$(cat "$t/next")" = "$next" ] && [ "$(cat "$t/catalog-token")" = "$token" ]
  [ ! -e "$t/ready" ] && [ ! -L "$t/ready" ]
  [ "$(tree_identity "$p")" = "$(cat "$t/candidate")" ] && owned "$p"
  [ "$(cat "$(owner_file "$p")")" = "$next" ]
  [ "$(tree_identity "$d")" = "$(cat "$t/live")" ]
  [ "$(cat "$(owner_file "$d")" 2>/dev/null || :)" = "$(cat "$t/live-owner")" ]
  [ -f "$p/module.prop" ] && [ ! -L "$p/module.prop" ]
  [ ! -L "$p/disable" ] && [ ! -L "$p/remove" ]
  if [ "$metamodule" = true ]; then
    meta=/data/adb/metamodule; before=
    if [ -L "$meta" ]; then
      before=$(readlink "$meta"); allowed=0
      [ "$before" != "$d" ] || allowed=1
      while IFS= read -r legacy; do [ "$legacy" != "$before" ] || allowed=1; done < "$t/legacy-targets"
      if [ "$allowed" != 1 ]; then printf 'MAPLESU_METAMODULE_CONFLICT:%s\n' "$before" >&2; exit 3; fi
    elif [ -e "$meta" ]; then printf 'MAPLESU_METAMODULE_CONFLICT:%s\n' "$meta" >&2; exit 3; fi
    put "$t/meta-before" "$before"
    put "$t/meta-target" "$d"
  fi
  if [ -e "$d" ]; then
    [ ! -L "$d" ] && [ ! -L "$d/module.prop" ] && [ ! -L "$d/update" ]
    if [ -e "$d/module.prop" ]; then
      [ -f "$d/module.prop" ]; cp -p "$d/module.prop" "$t/previous-prop.tmp"
      publish_file "$t/previous-prop.tmp" "$t/previous-prop"
    else put "$t/prop-missing" yes; fi
    if [ -e "$d/update" ]; then put "$t/update-present" yes; fi
    put "$t/published-live" "$(tree_identity "$d")"
    cp -p "$p/module.prop" "$t/metadata"
    publish_file "$t/metadata" "$d/module.prop"
    : > "$d/update"
  else
    mkdir "$t/placeholder"; chmod 0755 "$t/placeholder"
    write_owner "$t/placeholder" "$next"
    cp -p "$p/module.prop" "$t/placeholder/module.prop"
    : > "$t/placeholder/update"
    put "$t/published-live" "$(tree_identity "$t/placeholder")"
    move_tree "$t/placeholder" "$d"
  fi
  if [ "$metamodule" = true ]; then
    ln -s "$d" "$t/meta-link"
    chown -h 0:0 "$t/meta-link"
    mv -Tf "$t/meta-link" /data/adb/metamodule
    state_sync "$t" /data/adb
    [ "$(readlink /data/adb/metamodule)" = "$d" ]
  fi
  pending_durable
  printf '%s\n' "$token" > "$t/.ready"
  # The root journal's durable ready publication is the only installation commit.
  publish_file "$t/.ready" "$t/ready"
  printf 'MAPLESU_PENDING_COMMITTED\n'
)
pending_recover() (
  set -eu
  pending_paths "$1"
  [ -d "$t" ] || exit 0
  if [ -f "$t/hot-state" ] && [ "$(cat "$t/hot-state")" != skipped ]; then
    hot_recover "$1"; exit
  fi
  layout=$(cat "$t/layout" 2>/dev/null || :)
  if [ "$layout" = pending-merge-v2 ]; then pending_merge "$1"; exit; fi
  if [ -z "$layout" ]; then
    # An unpublished setup journal has not moved either external tree.
    case "$(cat "$t/origin" 2>/dev/null || :)" in
      pending|merge) ;;
      '') unpublished_setup || exit 73 ;;
      *) exit 73 ;;
    esac
    [ ! -e "$t/previous" ] && [ ! -L "$t/previous" ]
    [ ! -e "$t/previous-update" ] && [ ! -L "$t/previous-update" ]
    drop_owner_durable "$t/new"; retire_transaction; exit 0
  fi
  [ "$layout" = pending-v2 ] || exit 73
  if [ ! -f "$t/live" ] || [ ! -f "$t/pending" ] || [ ! -f "$t/candidate" ]; then
    # No tree move occurs before all three identities have been recorded.
    [ ! -e "$t/previous-update" ] && [ ! -L "$t/previous-update" ]
    drop_owner_durable "$t/new"; drop_owner_durable "$t/placeholder"; retire_transaction; exit 0
  fi
  next=$(cat "$t/next"); candidate=$(cat "$t/candidate"); previous=$(cat "$t/live"); pending=$(cat "$t/pending")
  current=$(tree_identity "$d"); update=$(tree_identity "$p"); backup=$(tree_identity "$t/previous-update")
  staged=$(tree_identity "$t/new")
  [ -z "$staged" ] || [ "$staged" = "$candidate" ]
  [ -z "$backup" ] || [ "$backup" = "$pending" ]
  if [ -f "$t/ready" ]; then
    [ "$(cat "$t/ready")" = "$(cat "$t/catalog-token")" ]
    [ "$update" = "$candidate" ] && owned "$p"
    [ "$(cat "$(owner_file "$p")")" = "$next" ]
    [ "$current" = "$(cat "$t/published-live")" ]
    pending_durable
    state_sync "$t/ready" "$t"
    drop_owner_durable "$t/previous-update"
    retire_transaction
    exit 0
  fi
  case "$update" in ''|"$candidate"|"$pending") ;; *) exit 73 ;; esac
  sync_tree "$t"
  state_sync "$working/module-transactions"
  if [ -f "$t/meta-target" ]; then
    meta=/data/adb/metamodule; before=$(cat "$t/meta-before"); target=$(cat "$t/meta-target")
    if [ -L "$meta" ]; then current_meta=$(readlink "$meta"); else [ ! -e "$meta" ]; current_meta=; fi
    case "$current_meta" in "$before"|"$target") ;; *) exit 73 ;; esac
    if [ -n "$before" ]; then
      rm -f -- "$t/meta-link"; ln -s "$before" "$t/meta-link"; chown -h 0:0 "$t/meta-link"
      mv -Tf "$t/meta-link" "$meta"
    else rm -f -- "$meta"; fi
    state_sync "$t" /data/adb
  fi
  if [ -f "$t/published-live" ]; then
    published=$(cat "$t/published-live")
    if [ -z "$previous" ]; then
      # SIGKILL may occur after recording the placeholder, before its rename.
      if [ -z "$current" ]; then
        [ "$(tree_identity "$t/placeholder")" = "$published" ]
      else
      [ "$current" = "$published" ] && [ ! -L "$d" ]
      [ "$(cat "$(owner_file "$d")")" = "$next" ]
      for file in "$d"/* "$d"/.[!.]* "$d"/..?*; do
        [ -e "$file" ] || [ -L "$file" ] || continue
        case "$(basename "$file")" in module.prop|update) ;; *) exit 73 ;; esac
      done
      move_tree "$d" "$t/placeholder"
      fi
    else
      [ "$current" = "$published" ] && [ "$current" = "$previous" ]
      [ "$(cat "$(owner_file "$d")" 2>/dev/null || :)" = "$(cat "$t/live-owner")" ]
      if [ -f "$t/previous-prop" ]; then
        cp -p "$t/previous-prop" "$t/metadata"; publish_file "$t/metadata" "$d/module.prop"
      elif [ -e "$t/prop-missing" ]; then rm -f -- "$d/module.prop"; fi
      if [ -e "$t/update-present" ]; then : > "$d/update"; state_sync "$d/update"; else rm -f -- "$d/update"; fi
      state_sync "$d"
    fi
  else
    [ "$current" = "$previous" ]
  fi
  if [ "$update" = "$candidate" ]; then
    owned "$p"; [ "$(cat "$(owner_file "$p")")" = "$next" ]
    move_tree "$p" "$t/new"
  fi
  if [ -n "$backup" ]; then
    [ ! -e "$p" ] && [ ! -L "$p" ]; move_tree "$t/previous-update" "$p"
  elif [ -n "$pending" ]; then [ "$(tree_identity "$p")" = "$pending" ]; fi
  state_sync "$modules" "$updates"
  drop_owner_durable "$t/new"; drop_owner_durable "$t/placeholder"; retire_transaction
)
# Hot install extends the existing ready journal; ready remains the install commit.
hot_result() {
  local status=${1:-none} code=${2:--} requested=${3:-false} applied=${4:-false}
  printf '\nMAPLESU_HOT_RESULT:%s:%s:%s:%s\n' "$requested" "$applied" "$status" "$code"
}
hot_state() (
  set -eu
  pending_paths "$1"; next=$2; token=$3
  [ -d "$t" ] && [ "$(cat "$t/next")" = "$next" ] && [ "$(cat "$t/catalog-token")" = "$token" ] || exit 75
  status=$(cat "$t/hot-state" 2>/dev/null || :)
  case "$status" in
    '') hot_result ;;
    skipped) hot_result skipped - true false ;;
    prepared) exit 75 ;;
    applied|running|started|ok|failed)
      [ "$(tree_identity "$d")" = "$(cat "$t/candidate")" ] && owned "$d" || exit 75
      [ "$(cat "$(owner_file "$d")")" = "$next" ] || exit 75
      code=-
      if [ -f "$t/hot-run" ]; then
        run=$(cat "$t/hot-run")
        if [ -f "$run/exit" ] && [ ! -L "$run/exit" ]; then
          code=$(cat "$run/exit")
          case "$code" in ''|*[!0-9]*) exit 73 ;; esac; [ "$code" -le 255 ]
          if [ "$code" = 0 ]; then status=ok; else status=failed; fi
        elif [ "$status" = running ]; then status=started; fi
      fi
      hot_result "$status" "$code" true true ;;
    *) exit 73 ;;
  esac
)
hot_swap() {
  local candidate previous current update backup trash
  candidate=$(cat "$t/candidate"); previous=$(cat "$t/published-live"); trash=$(cat "$t/hot-trash")
  current=$(tree_identity "$d"); update=$(tree_identity "$p"); backup=$(tree_identity "$trash")
  [ -z "$backup" ] || [ "$backup" = "$previous" ] || return 73
  if [ "$current" != "$candidate" ]; then
    [ "$update" = "$candidate" ] && owned "$p" || return 73
    [ "$(cat "$(owner_file "$p")")" = "$(cat "$t/next")" ] || return 73
    if [ -n "$current" ]; then
      [ "$current" = "$previous" ] && [ -z "$backup" ] && [ ! -L "$d" ] || return 73
      move_tree "$d" "$trash" || return
    else [ "$backup" = "$previous" ] || return 73; fi
    # A failed move preserves both the journal and old live inode for recovery.
    move_tree "$p" "$d" || return
  fi
  [ "$(tree_identity "$d")" = "$candidate" ] && owned "$d" || return 73
  rm -f -- "$d/update" || return
  sync_tree "$d" && state_sync "$(owner_file "$d")" "$modules" "$updates" || return
  put "$t/hot-state" applied
}
hot_recover() (
  set -eu
  pending_paths "$1"
  [ "$(cat "$t/layout")" = pending-v2 ] && [ "$(cat "$t/ready")" = "$(cat "$t/catalog-token")" ]
  # Recovery completes only the inode publication; it never replays the hot hook.
  if [ "$(cat "$t/hot-state")" = prepared ]; then hot_swap; fi
  [ "$(tree_identity "$d")" = "$(cat "$t/candidate")" ] && owned "$d"
  [ "$(cat "$(owner_file "$d")")" = "$(cat "$t/next")" ]
  sync_tree "$d"; state_sync "$(owner_file "$d")" "$modules" "$updates" "$t/ready" "$t"
  drop_owner_durable "$t/previous-update"
  retire_transaction
)
hot_prepare() (
  set -eu
  pending_paths "$1"; next=$2; token=$3; metamodule=$4; safe=$5
  [ "$(cat "$t/layout")" = pending-v2 ] && [ "$(cat "$t/ready")" = "$token" ]
  [ "$(cat "$t/next")" = "$next" ] && [ "$(cat "$t/catalog-token")" = "$token" ]
  [ ! -e "$t/hot-state" ] && [ ! -L "$t/hot-state" ] || exit 75
  [ "$(tree_identity "$p")" = "$(cat "$t/candidate")" ] && owned "$p"
  [ "$(cat "$(owner_file "$p")")" = "$next" ]
  marker=$p/.ksu_hot_install
  [ -f "$marker" ] && [ ! -L "$marker" ] || { hot_result; exit 0; }
  [ "$(stat -c %s "$marker")" -le 4096 ] || exit 7
  script=$(cat "$marker" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
  put "$t/hot-request" "$script"
  rm -f -- "$marker"; state_sync "$p"
  if [ "$metamodule" = true ] || [ "$safe" = true ]; then
    printf '%s\n' '- Hot install skipped: metamodule or safe mode; reboot to apply'
    put "$t/hot-state" skipped; hot_result skipped - true false; exit 0
  fi
  while [ "${script#./}" != "$script" ]; do script=${script#./}; done
  if [ -n "$script" ]; then
    case "$script" in /*|*/../*|../*|*/..|..|*/./*|*/.|.|*//*)
      printf '%s\n' '! Invalid MODULE_HOT_RUN_SCRIPT; reboot to apply' >&2
      put "$t/hot-state" skipped; hot_result skipped - true false; exit 0 ;;
    esac
    if [ ! -e "$p/$script" ] && [ ! -L "$p/$script" ]; then
      printf '%s\n' '! MODULE_HOT_RUN_SCRIPT not found, skipping it'; script=
    else
      resolved=$(readlink -f "$p/$script") || exit 73
      case "$resolved" in "$p"/*) [ -f "$resolved" ] || exit 73 ;; *)
        printf '%s\n' '! MODULE_HOT_RUN_SCRIPT points outside the module; reboot to apply' >&2
        put "$t/hot-state" skipped; hot_result skipped - true false; exit 0 ;;
      esac
    fi
  fi
  put "$t/hot-request" "$script"
  trash=$working/hot_install_trash/$boot
  for parent in "$working/hot_install_trash" "$trash"; do [ ! -L "$parent" ]; done
  mkdir -p "$trash"; chmod 0700 "$working/hot_install_trash" "$trash"
  state_sync "$working" "$working/hot_install_trash"
  put "$t/hot-trash" "$trash/$1.$token"
  put "$t/hot-state" prepared
  hot_swap
)
hot_run() (
  set -eu
  pending_paths "$1"
  [ "$(cat "$t/hot-state" 2>/dev/null || :)" = applied ] || exit 0
  script=$(cat "$t/hot-request")
  if [ -z "$script" ]; then hot_state "$@"; exit; fi
  run=$(cat "$t/hot-trash").run
  [ ! -e "$run" ] && [ ! -L "$run" ]; mkdir "$run"; chmod 0700 "$run"
  state_sync "${run%/*}"
  put "$t/hot-run" "$run"
  # Publish the attempted hook before launch: lost replies are inspected, never replayed.
  put "$t/hot-state" running
  "$busybox" setsid "$busybox" sh -c '
    run=$1; runner=$2; bin=$3; directory=$4; script=$5
    unset MAPLESU_TREE_CHANGED_PREFIX KSUD_MODULE_LIVE_EVENT
    trap "" PIPE
    "$bin/busybox" sh "$runner" "$directory" hot-install 0 "$script" </dev/null >"$run/stdout" 2>"$run/stderr"
    code=$?
    printf "%s\n" "$code" > "$run/.exit" &&
      "$bin/maplesu-tools" module-sync "$run/.exit" &&
      mv -f "$run/.exit" "$run/exit" && "$bin/maplesu-tools" module-sync "$run"
  ' module-hot "$run" "$runner" "$bin" "$d" "$script" </dev/null >/dev/null 2>&1 9>&- &
  deadline=$(( $(now) + 30000 ))
  while [ ! -f "$run/exit" ] && [ "$(now)" -lt "$deadline" ]; do "$busybox" sleep 0.05; done
  if [ -f "$run/stdout" ]; then cat "$run/stdout"; fi
  if [ -f "$run/stderr" ]; then cat "$run/stderr" >&2; fi
  if [ -f "$run/exit" ]; then
    code=$(cat "$run/exit")
    if [ "$code" = 0 ]; then put "$t/hot-state" ok; else put "$t/hot-state" failed; fi
  else put "$t/hot-state" started; fi
  hot_state "$@"
)
clean_hot_trash() {
  local old entry saved
  [ ! -L "$working/hot_install_trash" ] || return 73
  for old in "$working/hot_install_trash"/*; do
    [ -d "$old" ] && [ ! -L "$old" ] || continue
    saved=${old##*/}; [ "$saved" != "$boot" ] || continue
    case "$saved" in ????????-????-????-????-????????????) ;; *) continue ;; esac
    case "$saved" in *[!0-9a-f-]*) continue ;; esac
    for entry in "$old"/*; do drop_owner_durable "$entry" || return; done
    rm -rf -- "$old" && state_sync "$working/hot_install_trash" || return
  done
}

pending_merge() (
  set -eu
  pending_paths "$1"
  if [ ! -d "$t" ]; then
    [ -d "$p" ] && [ ! -L "$p" ] && owned "$p"
    mkdir -p "$working/module-transactions"
    state_sync "$working"
    mkdir "$t"; chmod 0700 "$t"
    state_sync "$working/module-transactions"
    put "$t/origin" merge
    put "$t/live" "$(tree_identity "$d")"
    put "$t/candidate" "$(tree_identity "$p")"
    put "$t/next" "$(cat "$(owner_file "$p")")"
    put "$t/layout" pending-merge-v2
  fi
  [ "$(cat "$t/layout")" = pending-merge-v2 ]
  previous=$(cat "$t/live"); candidate=$(cat "$t/candidate"); next=$(cat "$t/next")
  current=$(tree_identity "$d"); update=$(tree_identity "$p"); backup=$(tree_identity "$t/previous")
  [ -z "$backup" ] || [ "$backup" = "$previous" ]
  if [ "$current" != "$candidate" ]; then
    [ "$update" = "$candidate" ] && owned "$p"
    [ "$(cat "$(owner_file "$p")")" = "$next" ]
    if [ -n "$current" ]; then
      [ "$current" = "$previous" ] && [ -z "$backup" ]
      [ ! -L "$d" ] && [ ! -L "$p/disable" ] && [ ! -L "$p/remove" ]
      if [ -e "$d/disable" ]; then : > "$p/disable"; else rm -f -- "$p/disable"; fi
      if [ -e "$d/remove" ]; then : > "$p/remove"; else rm -f -- "$p/remove"; fi
      move_tree "$d" "$t/previous"
    elif [ -n "$previous" ]; then [ "$backup" = "$previous" ]; fi
    move_tree "$p" "$d"
  fi
  [ "$(tree_identity "$d")" = "$candidate" ] && owned "$d"
  [ "$(cat "$(owner_file "$d")")" = "$next" ]
  sync_tree "$d"
  state_sync "$(owner_file "$d")" "$modules" "$updates"
  drop_owner_durable "$t/previous"; retire_transaction
)
merge_updates() {
  local transaction directory id saved_boot layout
  for transaction in "$working/module-transactions"/*; do
    [ -d "$transaction" ] && [ ! -L "$transaction" ] || continue
    id=$(basename "$transaction"); valid_id "$id" || continue
    layout=$(cat "$transaction/layout" 2>/dev/null || :)
    case "$layout" in
      pending-v2)
        saved_boot=$(cat "$transaction/boot-id" 2>/dev/null || :)
        # A current-boot installer may still be running customize outside the lock.
        if [ ! -f "$transaction/ready" ] && [ "$saved_boot" = "$boot" ]; then continue; fi
        pending_recover "$id"; record "$?" "pending-recover:$id" ;;
      pending-merge-v2) pending_merge "$id"; record "$?" "pending-resume:$id" ;;
      '')
        case "$(cat "$transaction/origin" 2>/dev/null || :)" in
          pending|merge) pending_recover "$id"; record "$?" "pending-setup:$id" ;;
          '') t=$transaction
              if unpublished_setup; then pending_recover "$id"; record "$?" "pending-setup:$id"; fi ;;
        esac ;;
    esac
  done
  for directory in "$updates"/*; do
    owned "$directory" || continue
    id=$(basename "$directory")
    [ ! -e "$working/module-transactions/$id" ] && [ ! -L "$working/module-transactions/$id" ] || continue
    pending_merge "$id"; record "$?" "pending-merge:$id"
  done
}

active() { owned "$1" && [ ! -e "$1/disable" ] && [ ! -e "$1/remove" ]; }
meta_path() {
  local path
  path=$(readlink -f /data/adb/metamodule 2>/dev/null) || return 0
  case "$path" in "$modules"/*) active "$path" && printf '%s\n' "$path" ;; esac
}
launch() {
  # The event lock must never survive in a script or a module daemon.
  "$busybox" sh "$runner" "$@" 9>&-
  record "$?" "script:$1:$2"
}
common() {
  local stage=$1 directory file
  # Preserve MapleSU hooks and support KSU's public /data/adb/<stage>.d contract.
  for directory in "/data/adb/$stage.d" "$working/$stage.d"; do
    [ -d "$directory" ] && [ ! -L "$directory" ] || continue
    for file in "$directory"/*; do
      [ -f "$file" ] && [ -x "$file" ] && [ ! -L "$file" ] || continue
      launch "$directory" "$stage" "$deadline" "${file##*/}"
    done
  done
}
stage_scripts() {
  local stage=$1 directory meta
  common "$stage"
  meta=$(meta_path)
  if [ -n "$meta" ] && [ -f "$meta/$stage.sh" ]; then launch "$meta" "$stage" "$deadline"; fi
  for directory in "$modules"/*; do
    active "$directory" && [ "$directory" != "$meta" ] && [ -f "$directory/$stage.sh" ] || continue
    launch "$directory" "$stage" "$deadline"
  done
}
prune() {
  local directory identity owner meta code
  for directory in "$modules"/*; do
    owned "$directory" || continue
    # A same-boot customize may still own its uncommitted pending tree.
    [ ! -e "$working/module-transactions/${directory##*/}" ] || continue
    if [ -e "$directory/remove" ]; then
      for parent in /data/adb/modules_update /data/adb/ksu /data/adb/ksu/module_configs; do
        if [ -L "$parent" ]; then record 73 "remove-parent:$parent"; return; fi
      done
      identity=$(stat -c '%d-%i' "$directory") || { record 74 "remove-identity:$directory"; continue; }
      meta=$(readlink -f /data/adb/metamodule 2>/dev/null) || meta=
      if [ "$meta" = "$directory" ]; then rm -f /data/adb/metamodule || record "$?" "remove-metamodule:$directory"; fi
      launch "$directory" uninstall 0
      # KSU prunes after an uninstall hook's nonzero result, preserving that result.
      # Parent directories are fixed and symlinks were excluded before rm -rf.
      drop_owner "$updates/${directory##*/}"
      rm -rf -- "$directory" "$updates/${directory##*/}" "/data/adb/ksu/module_configs/${directory##*/}"
      code=$?; record "$code" "remove-files:$directory"
      if [ "$code" = 0 ]; then rm -f -- "$owners/$identity" || record "$?" "remove-owner:$directory"; fi
    elif [ -e "$directory/update" ]; then
      rm -f -- "$directory/update" || record "$?" "update:$directory"
    fi
  done
}
policies() {
  local directory
  set --
  for directory in "$modules"/*; do
    active "$directory" && [ -f "$directory/sepolicy.rule" ] && [ ! -L "$directory/sepolicy.rule" ] || continue
    set -- "$@" --rules "$directory/sepolicy.rule"
  done
  [ "$#" -gt 0 ] || return 0
  "$bin/maplesu-policy" module-apply --require-boot-policy "$@" 9>&-
  record "$?" policy
}
properties_and_mount() {
  local directory meta mount_code
  for directory in "$modules"/*; do
    active "$directory" && [ -f "$directory/system.prop" ] && [ ! -L "$directory/system.prop" ] || continue
    "$bin/resetprop" -n -f "$directory/system.prop" 9>&-
    record "$?" "properties:$directory"
  done
  meta=$(meta_path)
  set --
  if [ -z "$meta" ] || [ ! -f "$meta/metamount.sh" ]; then
    for directory in "$modules"/*; do
      active "$directory" && [ ! -e "$directory/skip_mount" ] || continue
      for tree in system vendor product system_ext odm oem; do
        if [ -d "$directory/$tree" ]; then set -- "$@" "${directory##*/}" "$directory"; break; fi
      done
    done
  fi
  # Reuse the existing module-requested mount implementation and exact ledger.
  mount_code=0
  "$busybox" sh "$bin/module-mount.sh" "$@" 9>&- || mount_code=$?
  record "$mount_code" mount
  # Do not overwrite a failed reset/unknown predecessor with a metamount success.
  [ "$mount_code" = 0 ] || return "$mount_code"
  if [ -n "$meta" ] && [ -f "$meta/metamount.sh" ]; then launch "$meta" metamount 0; fi
}
case "$event" in
  prepare-pending)
    shift; pending_prepare "$@"; exit "$?" ;;
  commit-pending|state-pending|recover-pending|hot-pending|state-hot)
    shift
    (
      "$busybox" flock 9 || exit 73
      case "$event" in
        commit-pending) pending_commit "$@" ;;
        state-pending) pending_state "$@" ;;
        recover-pending) pending_recover "$@" ;;
        hot-pending) hot_prepare "$@" ;;
        state-hot) hot_state "$@" ;;
      esac
    ) 9> "$state/lock"
    code=$?
    [ "$code" = 0 ] || exit "$code"
    if [ "$event" = hot-pending ]; then hot_run "$1" "$2" "$3"; exit "$?"; fi
    exit 0 ;;
esac
(
  "$busybox" flock 9 || exit 73
  duplicate_code=0
  if seen "$event"; then exit "$duplicate_code"; else code=$?; [ "$code" = 1 ] || exit "$code"; fi
  receipt "$event" 75 started || exit 73
  printf 'MAPLESU_BOOT_EVENT:%s:%s:%s\n' "$event" "$boot" "$(now)"
  deadline=$(( $(now) + 35000 ))
  case "$event" in
    post-fs-data)
      common post-fs-data
      merge_updates
      clean_hot_trash; record "$?" hot-trash
      prune
      policies
      meta=$(meta_path)
      if [ -n "$meta" ] && [ -f "$meta/post-fs-data.sh" ]; then launch "$meta" post-fs-data "$deadline"; fi
      for directory in "$modules"/*; do
        active "$directory" && [ "$directory" != "$meta" ] && [ -f "$directory/post-fs-data.sh" ] || continue
        launch "$directory" post-fs-data "$deadline"
      done
      properties_and_mount
      if seen post-mount; then
        record "$duplicate_code" post-mount
      else
        code=$?
        if [ "$code" = 1 ]; then
          receipt post-mount 75 started || exit 73
          prior=$result; result=0
          stage_scripts post-mount
          receipt post-mount "$result" done || exit 73
          code=$result; result=$prior; record "$code" post-mount
        else record "$code" post-mount; fi
      fi
      ;;
    boot-completed)
      # The parked (>3) counter survives successful fallback boots.
      "$bin/maplesu-tools" module-preinit boot-completed 9>&-
      record "$?" preinit-boot-completed
      stage_scripts "$event" ;;
    *) stage_scripts "$event" ;;
  esac
  # Catch changes made by hooks as well as the merged/pruned module inventory.
  # Native EOF on the next boot consumes the atomically published snapshot.
  refresh_initrc
  receipt "$event" "$result" done || exit 73
  exit "$result"
) 9> "$state/lock"
