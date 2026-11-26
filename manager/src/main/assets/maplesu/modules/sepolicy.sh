#!/system/bin/sh
# Called only by a module installer/script. This entry installs no manager base policy.
set -u
loader=/data/adb/maplesu/bin/maplesu-policy
case "${1:-}" in
  check) mode=module-check; shift ;;
  apply|patch|--live) mode=module-apply; shift ;;
  -h|--help)
    printf 'usage: sepolicy check|apply|patch <rules-file|statement>...\n'
    exit 0 ;;
  *) printf 'sepolicy: expected check, apply or patch\n' >&2; exit 2 ;;
esac
[ "$#" -gt 0 ] || { printf 'sepolicy: missing rules\n' >&2; exit 2; }
rules=$(mktemp /data/adb/maplesu/.module-rules-XXXXXX) || exit 1
trap 'rm -f "$rules"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
chmod 0600 "$rules" || exit 1
while [ "$#" -gt 0 ]; do
  case "$1" in
    --rules|--file|--apply|-f)
      shift
      [ "$#" -gt 0 ] && [ -f "$1" ] || { printf 'sepolicy: missing rule file\n' >&2; exit 2; }
      cat "$1" >> "$rules" || exit 1
      printf '\n' >> "$rules"
      ;;
    *)
      if [ -f "$1" ]; then
        cat "$1" >> "$rules" || exit 1
      else
        printf '%s\n' "$1" >> "$rules" || exit 1
      fi
      printf '\n' >> "$rules"
      ;;
  esac
  shift
done
if [ "$mode" = module-apply ]; then
  "$loader" module-apply --require-boot-policy --rules "$rules"
else
  "$loader" "$mode" --rules "$rules"
fi
exit "$?"
