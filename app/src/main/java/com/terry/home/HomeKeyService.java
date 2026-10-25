package com.terry.home;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Google TV sends the remote's Home button straight to its own launcher, ignoring the default-home setting.
 * This service only filters key events: it swallows Home and opens HomeActivity instead. Every other key passes through.
 */
public class HomeKeyService extends AccessibilityService {

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            Intent i = new Intent(this, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        }
        return true;
    }

    /** Screen coming on = the TV woke from standby (remote, or its own power-on timer). */
    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { openHome(true); }
    };

    /** The system binds this service during startup: if the TV just booted, open our home screen instead of the stock one. */
    @Override
    protected void onServiceConnected() {
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
        if (android.os.SystemClock.elapsedRealtime() < 4 * 60 * 1000) openHome(true);
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(screenOn); } catch (IllegalArgumentException ignored) { }
        super.onDestroy();
    }

    /** wake=true lets the home screen decide whether it's time for the morning song. */
    private void openHome(boolean wake) {
        startActivity(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("wake", wake));
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { }
}
