#!/bin/zsh
# Puts back apps removed by debloat.sh and hands Home back to the stock launcher.
# Usage: ./restore.sh                 restore everything
#        ./restore.sh com.cltv.fast   restore one app
set -u
export PATH=/opt/homebrew/bin:$PATH
export ANDROID_SERIAL=${ANDROID_SERIAL:-192.168.1.XXX:5555}

if (( $# )); then
  PKGS=($@)
else
  PKGS=(${(f)"$(grep -oE '^  [a-z][a-zA-Z0-9_.]+' ${0:A:h}/debloat.sh | tr -d ' ')"})
  adb shell settings put secure enabled_accessibility_services null
  adb shell pm uninstall com.terry.home
fi

for p in $PKGS; do
  printf '%-58s ' $p
  adb shell cmd package install-existing $p 2>&1 | tr -d '\r'
done

# Ad-blocking DNS: to undo it on its own, run
#   adb shell settings put global private_dns_mode off
if (( ! $# )); then
  adb shell settings put global private_dns_mode off
fi
