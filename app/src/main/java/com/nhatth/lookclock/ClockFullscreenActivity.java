package com.nhatth.lookclock;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.TextureView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import java.io.IOException;
import java.util.Calendar;

@SuppressWarnings("deprecation")
public class ClockFullscreenActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener,
        Camera.FaceDetectionListener {
    private static final String TAG = "ClockFullScreenActivity";
    //Template declaration
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    //Activity declaration

    private enum ClockState {NOT_LOOKING_AT, LOOKING_AT, CHANGING};

    private static final int REQUEST_CODE_CAMERA = 1;

    /**
     * The pulsing animation.
     */
    private Animation mPulseAnim;

    /**
     * A handler, which updates the time displayed on screen at a fixed-rate.
     */
    private Handler mUpdateHandler;
    private Runnable mNotLookingRunnable;
    private Runnable mLookingRunnable;

    private Handler mTimeoutHandler;
    private Runnable mTimeoutRunnable;
    /**
     * The period after there isn't anybody looking at the clock
     * to change the clock's state.
     */
    private static final int TIMEOUT = 3000;
    /**
     * The interval which {@link #mPulseAnim} runs and update the time in ms.
     */
    private static final int UPDATE_INTERVAL = 33;

    /**
     * The number of milliseconds which the clock changes from one state
     * to another.
     */
    private static final int CHANGE_DURATION = 500;

    /**
     * State of the clock right now. Default is NOT_LOOKING_AT
     */
    private ClockState mCurrentClockState = ClockState.NOT_LOOKING_AT;

    private AutoResizeTextView mHourView, mMinView, mSecView;
    private TextureView mCameraPreview;
    private TransitionDrawable mBackground;
    private GradientDrawable mGradientBackground;

    private int mOldHour, mOldMin, mOldSec;

    // Camera stuff
    private Camera mCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_clock_fullscreen);

        mVisible = true;
        mContentView = findViewById(R.id.fullscreen_content);

        mHourView = (AutoResizeTextView) findViewById(R.id.hour);
        mMinView = (AutoResizeTextView) findViewById(R.id.minute);
        mSecView = (AutoResizeTextView) findViewById(R.id.second);

        mCameraPreview = (TextureView) findViewById(R.id.fullscreen_camerapreview);
        mCameraPreview.setSurfaceTextureListener(this);

        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        // Load current time, and set up the clock
        Calendar currentTime = Calendar.getInstance();
        int hour = mOldHour = currentTime.get(Calendar.HOUR);
        if (currentTime.get(Calendar.AM_PM) == Calendar.PM) {
            hour += 12;
            mOldHour += 12;
        }
        int min = mOldMin = currentTime.get(Calendar.MINUTE);
        int sec = mOldSec = currentTime.get(Calendar.SECOND);

        setClockLabel(hour, min, sec);

        // Load the animation
        mPulseAnim = AnimationUtils.loadAnimation(this, R.anim.pulse);
        mPulseAnim.setInterpolator(new ReverseHalfwayInterpolator());

        // Set up the handler to update
        mUpdateHandler = new Handler();
        mNotLookingRunnable = new Runnable() {
            @Override
            public void run() {
                Calendar currentTime = Calendar.getInstance();
                int hour = currentTime.get(Calendar.HOUR);
                if (currentTime.get(Calendar.AM_PM) == Calendar.PM) {
                    hour += 12;
                }
                int min = currentTime.get(Calendar.MINUTE);
                int sec = currentTime.get(Calendar.SECOND);

                // Clock pulsing if the numbers change
                if (hour != mOldHour) {
                    mHourView.startAnimation(mPulseAnim);
                    mOldHour = hour;
                }
                if (min != mOldMin) {
                    mMinView.startAnimation(mPulseAnim);
                    mOldMin = min;
                }
                if (sec != mOldSec) {
                    mSecView.startAnimation(mPulseAnim);
                    mOldSec = sec;
                }

                setClockLabel(hour, min, sec);

                // run the handler again, and again, and again
                mUpdateHandler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
        mUpdateHandler.post(mNotLookingRunnable);

        mLookingRunnable = new Runnable() {
            @Override
            public void run() {
                Calendar currentTime = Calendar.getInstance();
                int hour = currentTime.get(Calendar.HOUR);
                int min = currentTime.get(Calendar.MINUTE);
                int sec = currentTime.get(Calendar.SECOND);

                // Clock pulsing if the numbers change
                if (hour != mOldHour) {
                    mHourView.startAnimation(mPulseAnim);
                    mOldHour = hour;
                }
                if (min != mOldMin) {
                    mMinView.startAnimation(mPulseAnim);
                    mOldMin = min;
                }
                if (sec != mOldSec) {
                    mSecView.startAnimation(mPulseAnim);
                    mOldSec = sec;
                }

                //TODO Run the background gradient animation

                mUpdateHandler.postDelayed(this, UPDATE_INTERVAL);
            }
        };

        //Set up the timeout handler, which change the state of the clock
        mTimeoutHandler = new Handler();
        mTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                mBackground.reverseTransition(CHANGE_DURATION);
                //TODO Run the background animation first, then change the state and run.
                mCurrentClockState = ClockState.NOT_LOOKING_AT;
                mUpdateHandler.removeCallbacks(mLookingRunnable);
                mUpdateHandler.post(mNotLookingRunnable);
            }
        };

        mBackground = (TransitionDrawable) mContentView.getBackground();
        mGradientBackground = (GradientDrawable) mBackground.getDrawable(1);

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
//        findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);
    }

    @Override
    public void onFaceDetection(Camera.Face[] faces, Camera camera) {
        // Face detection, to see if there're people looking at the screen

        // If there's at least one face
        if (faces.length > 0) {

            // First time someone look at, change the state
            if (mCurrentClockState == ClockState.NOT_LOOKING_AT) {
                mCurrentClockState = ClockState.LOOKING_AT;
                mSecView.setTextColor(Color.DKGRAY);
                mBackground.startTransition(CHANGE_DURATION);

                // Change the runnable of handler to stop update time
                mUpdateHandler.removeCallbacks(mNotLookingRunnable);
                mUpdateHandler.post(mLookingRunnable);
            }

            // Remove the current timeout, and schedule the next timeout
            mTimeoutHandler.removeCallbacks(mTimeoutRunnable);
            mTimeoutHandler.postDelayed(mTimeoutRunnable, TIMEOUT);
        }
        // No one is looking!
        else {
            mSecView.setTextColor(Color.parseColor("#CCCCCC"));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.clock_fullscreen, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_popout) {
            //TODO Open the Pop out activity
            startService(new Intent(getApplicationContext(), ClockPopoutService.class));
            Log.i(TAG, "onOptionsItemSelected: Pressed on Popout button");
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_CAMERA:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    initCamera(mCameraPreview.getSurfaceTexture());

                } else {
                    // Camera request is refused...
                    Log.i(TAG, "onRequestPermissionsResult: User denied camera permission.");
                    Toast.makeText(this,
                            "Look-at Clock needs camera permission to do its magic! App will quit.",
                            Toast.LENGTH_LONG).show();
                    exitActivity();
                }
                break;
            default:
                Log.i(TAG, "onRequestPermissionsResult: Didn't ask for this: "+requestCode);
        }
    }

    // TextureView Listener Methods
    // All about the camera
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        // Can we do it? CAN WE DO IT?
        int cameraPermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA);
        // Unclear...
        if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {
                //TODO Show the reason why this app needs camera permission
            }
            // Request the permission needed for the camera
            ActivityCompat.requestPermissions(this,
                    new String[] {Manifest.permission.CAMERA},
                    REQUEST_CODE_CAMERA);
        }

        // Yes WE CAN!
        else {
            initCamera(surface);
        }
    }

    private void initCamera(SurfaceTexture surface) {
        int frontCameraId = findFrontFacingCamera();
        if (frontCameraId == -1) {
            Log.e(TAG, "onCreate: Can't find the id of the front-facing camera!");
            Toast.makeText(this, "Can't find front-facing camera. App will quit.",
                    Toast.LENGTH_SHORT).show();
            exitActivity();
        }

        mCamera = Camera.open(frontCameraId);

        try {
            mCamera.setPreviewTexture(surface);
            mCamera.startPreview();
        } catch (IOException ioe) {
            Log.e(TAG, "onSurfaceTextureAvailable: Can't open the camera!", ioe);
        }

        mCamera.setFaceDetectionListener(this);
        mCamera.startFaceDetection();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mCamera.stopFaceDetection();
        mCamera.stopPreview();
        mCamera.release();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    private void setClockLabel(int hour, int min, int sec) {
        String sHour, sMin, sSec;

        // Format the time with zero leading
        if (hour >= 10)
            sHour = String.valueOf(hour);
        else
            sHour = "0" + hour;
        mHourView.setText(sHour);

        if (min >= 10)
            sMin = String.valueOf(min);
        else
            sMin = "0" + min;
        mMinView.setText(sMin);

        if (sec >= 10)
            sSec = String.valueOf(sec);
        else
            sSec = "0" + sec;
        mSecView.setText(sSec);
    }

    /**
     * By Lars Vogel
     * <p/>
     * From http://www.vogella.com/tutorials/AndroidCamera/article.html
     * <p/>
     * <p/>
     * Get the id of the front-facing camera on the device
     *
     * @return id of the front-facing camera
     */
    private int findFrontFacingCamera() {
        int cameraId = -1;
        // Search for the front facing camera
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                Log.i(TAG, "findFrontFacingCamera: cameraId found: " + i);
                cameraId = i;
                break;
            }
        }
        return cameraId;
    }

    /**
     * Exit the current activity to home screen
     */
    private void exitActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        this.finish();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
//        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }
}
