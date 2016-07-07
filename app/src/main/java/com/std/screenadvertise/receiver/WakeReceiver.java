package com.std.screenadvertise.receiver;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * Created by Maik on 2016/6/16.
 */
public class WakeReceiver extends BroadcastReceiver {
    private final static int WAKE_SERVICE_ID = 1001;
    public static final String GRAY_WAKE_ACTION = "com.shiki.gray";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (GRAY_WAKE_ACTION.equals(action)) {
            Intent wakeIntent = new Intent(context, WakeNotifyService.class);
            context.startService(wakeIntent);
        }
    }

    public static class WakeNotifyService extends Service {
        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            if (Build.VERSION.SDK_INT < 18) {
                startForeground(WAKE_SERVICE_ID, new Notification());
            } else {
                Intent innerIntent = new Intent(this, WakeGrayInnerService.class);
                startService(innerIntent);
                startForeground(WAKE_SERVICE_ID, new Notification());
            }
            return START_STICKY;
        }

        @Override
        public IBinder onBind(Intent intent) {
            throw new UnsupportedOperationException("Not yet implemented");
        }
    }

    public static class WakeGrayInnerService extends Service {

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            startForeground(WAKE_SERVICE_ID, new Notification());
            stopSelf();
            return super.onStartCommand(intent, flags, startId);
        }

        @Override
        public IBinder onBind(Intent intent) {
            throw new UnsupportedOperationException("Not yet implemented");
        }
    }
}
