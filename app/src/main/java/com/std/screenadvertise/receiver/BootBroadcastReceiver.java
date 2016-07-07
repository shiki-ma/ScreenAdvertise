package com.std.screenadvertise.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.std.screenadvertise.MainActivity;

/**
 * Created by Maik on 2016/6/11.
 */
public class BootBroadcastReceiver extends BroadcastReceiver {
    private static String ACTION_BOOT="android.intent.action.BOOT_COMPLETED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(ACTION_BOOT)) {
            Intent bootIntent = new Intent(context, MainActivity.class);
            bootIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(bootIntent);
        }
    }
}
