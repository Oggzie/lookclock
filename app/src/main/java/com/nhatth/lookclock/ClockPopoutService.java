package com.nhatth.lookclock;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

public class ClockPopoutService extends Service {

    private static final String TAG = "ClockPopoutService";
    private static final String EVENT_STOP = "stop-clock";

    private WindowManager mWindowManager;
    private FrameLayout mContainer;
    private TextView mClockText;
    private TextureView mCameraPreview;

    private BroadcastReceiver mReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: created!");

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LayoutInflater layoutInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

        mContainer = (FrameLayout) layoutInflater.inflate(R.layout.service_clock_popout, null);
        mClockText = (TextView) mContainer.findViewById(R.id.popout_clock);
        mCameraPreview = (TextureView) mContainer.findViewById(R.id.popout_camerapreview);


        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                stopSelf();
            }
        };
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(mReceiver, new IntentFilter(EVENT_STOP));

        // Show the notification for the user to stop and to note that our clock is running
        NotificationCompat.Builder notiBuilder =
                new NotificationCompat.Builder(this)
                .setSmallIcon(android.R.color.transparent)
                .setContentTitle("Look-at Clock")
                .setContentText("Tap to stop the Popout Clock.")
                .setOngoing(true);
        Intent stopIntent = new Intent(EVENT_STOP);

        // Attach the clock to the window
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 0;
        params.y = 100;

        mWindowManager.addView(mContainer, params);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mContainer != null) mWindowManager.removeView(mContainer);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
