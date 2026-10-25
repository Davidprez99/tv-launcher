#!/bin/zsh
# Safe cleanup for the Sansui / Expressluck AiPlus2K (Android TV 11).
# Uses "uninstall for user 0": the app files stay on the TV, so ./restore.sh brings any of them back.
# Usage: ./debloat.sh            (set ANDROID_SERIAL=<tv-ip>:5555 first)
set -u
export PATH=/opt/homebrew/bin:$PATH
export ANDROID_SERIAL=${ANDROID_SERIAL:-192.168.1.XXX:5555}

REMOVE=(
  tv.anoki.acr.controller                             # ACR: identifies what's on screen and reports it (viewing tracker)
  com.cltv.fast                                       # FAST ad-supported channels
  com.google.android.tvrecommendations                # recommendation/ad rows on the stock home screen
  com.mediatek.android.leanbacklauncher.partnercustomizer  # manufacturer promo rows on the stock home screen
  fusion.android.tv.demo                              # in-store demo mode
  com.expressluck.tvhelpmanual                        # e-manual
  com.google.android.feedback                         # crash/feedback reporter
  com.google.android.play.games                       # Play Games
  com.google.android.backdrop                         # Ambient-mode screensaver (heavy on 1 GB RAM)
  com.android.printspooler                            # printing
  air.com.vudu.air.DownloaderTablet                   # Vudu
  com.google.android.videos                           # Play Movies
  com.FDGEntertainment.redball4.gp                    # Red Ball 4 game
)

for p in $REMOVE; do
  printf '%-58s ' $p
  adb shell pm uninstall -k --user 0 $p 2>&1 | tr -d '\r'
done
