package com.terry.home;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/** Opens a title in the Netflix TV app. */
final class Netflix {
    private Netflix() { }

    static void open(Activity a, String titleId, String name) {
        // The TV app only follows the link with source=30, and only on a fresh task (otherwise it resumes its home page).
        // It shows "Who's watching?" first, then the title.
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.netflix.com/title/" + titleId))
                .setClassName("com.netflix.ninja", "com.netflix.ninja.MainActivity")
                .putExtra("source", "30")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            a.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(a, "Couldn't open " + name, Toast.LENGTH_SHORT).show();
        }
    }

    static boolean installed(Activity a) {
        try {
            return a.getPackageManager().getApplicationInfo("com.netflix.ninja", 0).enabled;
        } catch (Exception e) {
            return false;
        }
    }
}
