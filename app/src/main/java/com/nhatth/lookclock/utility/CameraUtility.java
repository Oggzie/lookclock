package com.nhatth.lookclock.utility;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;

import java.io.IOException;

/**
 * Created by nhatth on 4/11/16.
 * A utility class for all things camera related.
 */
@SuppressWarnings("deprecation")
public class CameraUtility {
    private static final String TAG = "CameraUtility";

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
    public static int findFrontFacingCamera() {
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
     * Open the camera, start previewing it in a surface, and start face detection
     * @param surface The SurfaceTexture to preview
     * @param faceDetectionListener FaceDetectionListener callback
     * @return Opened camera
     * @throws Exception Error while opening the camera
     */
    public static Camera initCamera(SurfaceTexture surface,
                                    Camera.FaceDetectionListener faceDetectionListener) throws Exception{
        Camera camera;
        int frontCameraId = CameraUtility.findFrontFacingCamera();
        if (frontCameraId == -1) {
            Log.e(TAG, "initCamera: Cannot find front-facing camera.");
        }

        camera = Camera.open(frontCameraId);

        try {
            camera.setPreviewTexture(surface);
            camera.startPreview();
        } catch (IOException ioe) {
            Log.e(TAG, "initCamera: Can't open camera.", ioe);
            throw ioe;
        }

        camera.setFaceDetectionListener(faceDetectionListener);
        camera.startFaceDetection();

        return camera;
    }

    public static void releaseCamera(Camera camera) {
        camera.stopFaceDetection();
        camera.stopPreview();
        camera.release();
    }

}
