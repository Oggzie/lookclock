package com.nhatth.lookclock;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.nhatth.lookclock.utility.CameraUtility;
import com.nhatth.lookclock.utility.ClockUtility;

@SuppressWarnings("deprecation")
public class ClockPopoutService extends Service
        implements TextureView.SurfaceTextureListener, Camera.FaceDetectionListener{

    private static final String TAG = "ClockPopoutService";
    private static final String STOP_POPOUT = "com.nhatth.lookclock.STOP_POPOUT";
    private static final int STOP_REQUEST_CODE = 4444;
    private static final int NOTIFICATION_ID = 1;

    /**
     * The period after there isn't anybody looking at the clock
     * to change the clock's state.
     */
    private static final int TIMEOUT = 7500;
    /**
     * The interval which {@link #mUpdateHandler} runs and update the time in ms.
     */
    private static final int UPDATE_INTERVAL = 500;

    private WindowManager mWindowManager;
    private FrameLayout mContainer;
    private TextView mClockText;
    private TextureView mCameraPreview;

    private ClockUtility.ClockState mCurrentClockState = ClockUtility.ClockState.NOT_LOOKING_AT;

    /**
     * Update handler, which updates the time
     */
    private Handler mUpdateHandler;

    private Handler mTimeoutHandler;
    private Runnable mTimeoutRunnable;

    private Camera mCamera;

    private BroadcastReceiver mReceiver, mScreenStateReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: created!");

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LayoutInflater layoutInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

        mContainer = (FrameLayout) layoutInflater.inflate(R.layout.service_clock_popout, null);
        mClockText = (TextView) mContainer.findViewById(R.id.popout_clock);
        mCameraPreview = (TextureView) mContainer.findViewById(R.id.popout_camerapreview);

        // Set up the camera
        mCameraPreview.setSurfaceTextureListener(this);

        // Set up the handler and update runnable to run the clock
        mUpdateHandler = new Handler();
        Runnable mUpdateRunnable = new Runnable() {
            @Override
            public void run() {

                if (mCurrentClockState == ClockUtility.ClockState.NOT_LOOKING_AT) {
                    mClockText.setText(ClockUtility.getCurrentFormattedTime(getApplicationContext()));
                }

                mUpdateHandler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
        mUpdateHandler.post(mUpdateRunnable);

        mTimeoutHandler = new Handler();
        mTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                mCurrentClockState = ClockUtility.ClockState.NOT_LOOKING_AT;
                mClockText.setTextColor(getResources().getColor(R.color.clock_text_dim));
            }
        };

        // Register screen on/off event
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        mScreenStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Turn off the camera to save battery
                if(intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    Log.i(TAG, "onReceive: Screen turns off");
                    if (mCamera != null) {
                        CameraUtility.releaseCamera(mCamera);
                    }
                }
                // And if the user switch phone on, so do us!
                else if(intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                    Log.i(TAG, "onReceive: Screen turns on");
                    Handler handler = new Handler();
                    // delay the opening of camera, in case screen is locked by face
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                mCamera = CameraUtility.initCamera(mCameraPreview.getSurfaceTexture(),
                                        ClockPopoutService.this);
                            } catch (Exception e) {
                                Log.e(TAG, "onSurfaceTextureAvailable: Error while opening camera", e);
                            }
                        }
                    }, 5000);
                }
            }
        };
        registerReceiver(mScreenStateReceiver, filter);

        // Dynamically register the broadcast receiver for our notification.
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(STOP_POPOUT);
        mReceiver = new NotificationReceiver();
        registerReceiver(mReceiver, intentFilter);

        // Show the notification for the user to stop and to note that our clock is running
        NotificationCompat.Builder notifyBuilder =
                new NotificationCompat.Builder(this)
                .setSmallIcon(android.R.color.transparent)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_content))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE);

        Intent stopIntent = new Intent(STOP_POPOUT);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, STOP_REQUEST_CODE, stopIntent,
                PendingIntent.FLAG_ONE_SHOT);
        notifyBuilder.setContentIntent(pendingIntent);

        NotificationManager notifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // Builds the notification and issues it.
        notifyMgr.notify(NOTIFICATION_ID, notifyBuilder.build());

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
        // Remove clock from the window
        if (mContainer != null) mWindowManager.removeView(mContainer);
        // Release the camera
        if (mCamera != null) {
            CameraUtility.releaseCamera(mCamera);
        }
        // Unregister the stop receiver
        unregisterReceiver(mReceiver);
        unregisterReceiver(mScreenStateReceiver);
        // Remove the notification
        NotificationManager notifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notifyMgr.cancel(NOTIFICATION_ID);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onFaceDetection(Camera.Face[] faces, Camera camera) {
        // Face detection, to see if there're people looking at the screen

        // If there's at least one face
        if (faces.length > 0) {

            // First time someone look at, change the state
            if (mCurrentClockState == ClockUtility.ClockState.NOT_LOOKING_AT) {
                mCurrentClockState = ClockUtility.ClockState.LOOKING_AT;
                mClockText.setTextColor(getResources().getColor(R.color.clock_text_lit));
            }

            // Remove the current timeout, and schedule the next timeout
            mTimeoutHandler.removeCallbacks(mTimeoutRunnable);
            mTimeoutHandler.postDelayed(mTimeoutRunnable, TIMEOUT);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "onSurfaceTextureAvailable");
        try {
            mCamera = CameraUtility.initCamera(surface, this);
        } catch (Exception e) {
            Log.e(TAG, "onSurfaceTextureAvailable: Error while opening camera", e);
            Toast.makeText(this, R.string.error_open_camera_toast, Toast.LENGTH_LONG).show();
        }
        mCameraPreview.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    /**
     * Receiver class for receiving the stop message.
     */
    public class NotificationReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // We only have one action, and that is to stop the current service
            // So no need to check, just stop the service
            stopSelf();
        }
    }

}


