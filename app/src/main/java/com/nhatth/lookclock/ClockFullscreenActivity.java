package com.nhatth.lookclock;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.TextureView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import com.nhatth.lookclock.utility.ArgbEvaluator;
import com.nhatth.lookclock.utility.CameraUtility;
import com.nhatth.lookclock.utility.ClockUtility;
import com.nhatth.lookclock.utility.GradientColorAnimator;
import com.nhatth.lookclock.utility.ReverseHalfwayInterpolator;

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

    private static final int REQUEST_CODE_OVERLAY = 100;
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

    private static final int REQUEST_CODE_CAMERA = 1;

    /**
     * The pulsing animation.
     */
    private Animation mPulseAnim;

    // Clock text color animation
    AnimatorSet mDimToLitSet, mLitToDimSet;

    // Background color animation
    GradientColorAnimator mBackgroundAnim;

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
     * The interval which {@link #mUpdateHandler} runs and update the time in ms.
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
    private ClockUtility.ClockState mCurrentClockState = ClockUtility.ClockState.NOT_LOOKING_AT;

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
                mUpdateHandler.postDelayed(this, UPDATE_INTERVAL);
            }
        };

        // Set up the timeout handler, which change the state of the clock
        mTimeoutHandler = new Handler();
        mTimeoutRunnable = new Runnable() {
            @Override
            public void run() {

                // Animate all the changes
                mBackground.reverseTransition(CHANGE_DURATION);
                mLitToDimSet.start();
                mBackgroundAnim.stopAndReset();

                mCurrentClockState = ClockUtility.ClockState.CHANGING;
                mUpdateHandler.removeCallbacks(mLookingRunnable);
                mUpdateHandler.post(mNotLookingRunnable);
            }
        };

        mBackground = (TransitionDrawable) mContentView.getBackground();
        mGradientBackground = (GradientDrawable) mBackground.getDrawable(1);

        // Load the animation
        loadAnimation();

    }

    private void loadAnimation() {
        mPulseAnim = AnimationUtils.loadAnimation(this, R.anim.pulse);
        mPulseAnim.setInterpolator(new ReverseHalfwayInterpolator());

        ObjectAnimator dimToLit = ObjectAnimator.ofInt(mHourView, "textColor",
                getResources().getColor(R.color.clock_text_dim),
                getResources().getColor(R.color.clock_text_lit));
        dimToLit.setEvaluator(ArgbEvaluator.getInstance());
        ObjectAnimator[] dimToLitSets = {dimToLit, dimToLit.clone(), dimToLit.clone()};
        dimToLitSets[1].setTarget(mMinView);
        dimToLitSets[2].setTarget(mSecView);
        mDimToLitSet = new AnimatorSet();
        mDimToLitSet.playTogether(dimToLitSets);
        mDimToLitSet.setDuration(CHANGE_DURATION);

        mDimToLitSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mCurrentClockState = ClockUtility.ClockState.LOOKING_AT;
                mBackgroundAnim.animateBothIndefinite(30, 5000);
                mBackgroundAnim.start();
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });

        ObjectAnimator litToDim = ObjectAnimator.ofInt(mHourView, "textColor",
                getResources().getColor(R.color.clock_text_lit),
                getResources().getColor(R.color.clock_text_dim));
        litToDim.setEvaluator(ArgbEvaluator.getInstance());
        ObjectAnimator[] litToDimSets = {litToDim, litToDim.clone(), litToDim.clone()};
        litToDimSets[1].setTarget(mMinView);
        litToDimSets[2].setTarget(mSecView);
        mLitToDimSet = new AnimatorSet();
        mLitToDimSet.playTogether(litToDimSets);
        mLitToDimSet.setDuration(CHANGE_DURATION);

        litToDim.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mCurrentClockState = ClockUtility.ClockState.NOT_LOOKING_AT;
                mGradientBackground.setColors(new int[] {getResources().getColor(R.color.gradient_top),
                    getResources().getColor(R.color.gradient_bottom)});
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });

        mBackgroundAnim = new GradientColorAnimator(mGradientBackground,
                getResources().getColor(R.color.gradient_top),
                getResources().getColor(R.color.gradient_bottom));
        mBackgroundAnim.animateBothIndefinite(30, 5000);
    }

    @Override
    public void onFaceDetection(Camera.Face[] faces, Camera camera) {
        // Face detection, to see if there're people looking at the screen

        // If there's at least one face
        if (faces.length > 0) {

            // First time someone look at, change the state
            if (mCurrentClockState == ClockUtility.ClockState.NOT_LOOKING_AT) {
                mCurrentClockState = ClockUtility.ClockState.CHANGING;

                mBackground.startTransition(CHANGE_DURATION);
                mDimToLitSet.start();

                // Change the runnable of handler to stop update time
                mUpdateHandler.removeCallbacks(mNotLookingRunnable);
                mUpdateHandler.post(mLookingRunnable);
            }

            // Remove the current timeout, and schedule the next timeout
            mTimeoutHandler.removeCallbacks(mTimeoutRunnable);
            mTimeoutHandler.postDelayed(mTimeoutRunnable, TIMEOUT);
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if(!Settings.canDrawOverlays(this)) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_CODE_OVERLAY);
                }
                else {
                    popClock();
                }
            }
            else {
                popClock();
            }
        }
        return true;
    }

    private void popClock() {
        AlertDialog.Builder builder =
                new AlertDialog.Builder(this)
                        .setTitle(R.string.dialog_popout_title)
                        .setMessage(R.string.dialog_popout_content)
                        .setPositiveButton("GOT IT", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // release the camera for pop out clock
                                CameraUtility.releaseCamera(mCamera);
                                mCamera = null;

                                exitActivity();
                                startService(new Intent(getApplicationContext(), ClockPopoutService.class));
                            }
                        });
        builder.create().show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_OVERLAY) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    popClock();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_CAMERA:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    try {
                        mCamera = CameraUtility.initCamera(mCameraPreview.getSurfaceTexture(), this);
                    } catch (Exception e) {
                        Toast.makeText(this, R.string.error_open_camera_toast, Toast.LENGTH_LONG)
                                .show();
                        exitActivity();
                    }

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

                AlertDialog.Builder builder =
                        new AlertDialog.Builder(this)
                        .setTitle(R.string.dialog_reason_title)
                        .setMessage(R.string.dialog_reason_content)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(ClockFullscreenActivity.this,
                                        new String[]{Manifest.permission.CAMERA},
                                        REQUEST_CODE_CAMERA);
                            }
                        });
                builder.create().show();
            } else {
                // Request the permission needed for the camera
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        REQUEST_CODE_CAMERA);
            }
        }

        // Yes WE CAN!
        else {
            try {
                mCamera = CameraUtility.initCamera(surface, this);
            } catch (Exception e) {
                Toast.makeText(this, R.string.error_open_camera_toast, Toast.LENGTH_LONG).show();
                exitActivity();
            }
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (mCamera != null) { // if camera is not released
            CameraUtility.releaseCamera(mCamera);
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    private void setClockLabel(int hour, int min, int sec) {
        mHourView.setText(ClockUtility.addLeadingZero(hour));
        mMinView.setText(ClockUtility.addLeadingZero(min));
        mSecView.setText(ClockUtility.addLeadingZero(sec));
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
