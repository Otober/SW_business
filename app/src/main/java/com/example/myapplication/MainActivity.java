/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.myapplication;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.TextPaint;
import android.util.Log;
import android.util.Size;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.AndroidPacketGetter;
import com.google.mediapipe.framework.Packet;
import com.google.mediapipe.framework.PacketGetter;
import com.google.mediapipe.glutil.EglManager;
import com.google.protobuf.InvalidProtocolBufferException;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.lang.Math;


/**
 * Main activity of MediaPipe example apps.
 */

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String BINARY_GRAPH_NAME = "pose_tracking_gpu.binarypb";
    private static final String INPUT_VIDEO_STREAM_NAME = "input_video";
    private static final String OUTPUT_VIDEO_STREAM_NAME = "output_video";
    private static final String OUTPUT_LANDMARKS_STREAM_NAME = "pose_landmarks";
    private static final int NUM_HANDS = 2;
    private static final CameraHelper.CameraFacing CAMERA_FACING = CameraHelper.CameraFacing.BACK;

    // Flips the camera-preview frames vertically before sending them into FrameProcessor to be
    // processed in a MediaPipe graph, and flips the processed frames back when they are displayed.
    // This is needed because OpenGL represents images assuming the image origin is at the bottom-left
    // corner, whereas MediaPipe in general assumes the image origin is at top-left.
    private static final boolean FLIP_FRAMES_VERTICALLY = true;

    private static int f_width = 1, f_height = 1;

    public static NormalizedLandmarkList poseLandmarks;

    public boolean view_flag = false;

    public myView my_view;

    private float avg_dist = 0.0f;

    static {
        // Load all native libraries needed by the app.
        System.loadLibrary("mediapipe_jni");
        System.loadLibrary("opencv_java3");
    }


    // {@link SurfaceTexture} where the camera-preview frames can be accessed.
    private SurfaceTexture previewFrameTexture;
    // {@link SurfaceView} that displays the camera-preview frames processed by a MediaPipe graph.
    private SurfaceView previewDisplayView;
    // Creates and manages an {@link EGLContext}.
    private EglManager eglManager;
    // Sends camera-preview frames into a MediaPipe graph for processing, and displays the processed
    // frames onto a {@link Surface}.
    private FrameProcessor processor;
    // Converts the GL_TEXTURE_EXTERNAL_OES texture from Android camera into a regular texture to be
    // consumed by {@link FrameProcessor} and the underlying MediaPipe graph.
    private ExternalTextureConverter converter;
    // ApplicationInfo for retrieving metadata defined in the manifest.
    private ApplicationInfo applicationInfo;
    // Handles camera access via the {@link CameraX} Jetpack support library.
    private CameraXPreviewHelper cameraHelper;

    private String state;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getContentViewLayoutResId());

        previewDisplayView = new SurfaceView(this);
        setupPreviewDisplayView();


        try {
            applicationInfo =
                    getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            Log.v(TAG, "Cannot find application info: " + e);
        }
        // Initialize asset manager so that MediaPipe native libraries can access the app assets, e.g.,
        // binary graphs.
        AndroidAssetUtil.initializeNativeAssetManager(this);
        eglManager = new EglManager(null);
        processor =
                new FrameProcessor(
                        this,
                        eglManager.getNativeContext(),
                        BINARY_GRAPH_NAME,
                        INPUT_VIDEO_STREAM_NAME,
                        OUTPUT_VIDEO_STREAM_NAME);
        processor
                .getVideoSurfaceOutput()
                .setFlipY(FLIP_FRAMES_VERTICALLY);


        // To show verbose logging, run:
        // adb shell setprop log.tag.MainActivity VERBOSE
//        if (Log.isLoggable(TAG, Log.VERBOSE)) {
        processor.addPacketCallback(
                OUTPUT_LANDMARKS_STREAM_NAME,
                (packet) -> {
                    view_flag = true;
                    try {
//                        NormalizedLandmarkList poseLandmarks = PacketGetter.getProto(packet, NormalizedLandmarkList.class);
                        byte[] landmarksRaw = PacketGetter.getProtoBytes(packet);
                        //NormalizedLandmarkList poseLandmarks = NormalizedLandmarkList.parseFrom(landmarksRaw);
                        poseLandmarks = NormalizedLandmarkList.parseFrom(landmarksRaw);
                        //Log.v(TAG, "[TS:" + packet.getTimestamp() + "] " + getPoseLandmarksDebugString(poseLandmarks));
                        SurfaceHolder srh = previewDisplayView.getHolder();
                        my_view.invalidate();
//
//                  -- this line cannot Running --
//                    Canvas canvas = null;
//                    try {
//                        canvas= srh.lockCanvas();
//                        synchronized(srh){
//                            Paint paint = new Paint();
//                            paint.setColor(Color.RED);
//                            canvas.drawCircle(10.0f,10.0f,10.0f,paint);
//                        }
//                    }finally{
//                        if(canvas != null){
//                            srh.unlockCanvasAndPost(canvas);
//                        }
//                    }
////                    processor.getVideoSurfaceOutput().setSurface(srh.getSurface());
                    } catch (InvalidProtocolBufferException exception) {
                        Log.e(TAG, "failed to get proto.", exception);
                    }

                }
        );
        /*processor.addPacketCallback(
                "throttled_input_video_cpu",
                (packet) ->{
                    Log.d("Raw Image","Receive image with ts: "+packet.getTimestamp());
                    Bitmap image = AndroidPacketGetter.getBitmapFromRgba(packet);
                }
        );*/
        PermissionHelper.checkAndRequestCameraPermissions(this);
    }

    // Used to obtain the content view for this application. If you are extending this class, and
    // have a custom layout, override this method and return the custom layout.
    protected int getContentViewLayoutResId() {
        return R.layout.activity_main;
    }

    @Override
    protected void onResume() {
        super.onResume();
        converter =
                new ExternalTextureConverter(
                        eglManager.getContext(), 2);
        converter.setFlipY(FLIP_FRAMES_VERTICALLY);
        converter.setConsumer(processor);
        if (PermissionHelper.cameraPermissionsGranted(this)) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        converter.close();

        // Hide preview display until we re-open the camera again.
        previewDisplayView.setVisibility(View.GONE);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    protected void onCameraStarted(SurfaceTexture surfaceTexture) {
        previewFrameTexture = surfaceTexture;
        // Make the display view visible to start showing the preview. This triggers the
        // SurfaceHolder.Callback added to (the holder of) previewDisplayView.
        previewDisplayView.setVisibility(View.VISIBLE);
    }

    protected Size cameraTargetResolution() {
        return null; // No preference and let the camera (helper) decide.
    }

    public void startCamera() {
        cameraHelper = new CameraXPreviewHelper();
        cameraHelper.setOnCameraStartedListener(
                surfaceTexture -> {
                    onCameraStarted(surfaceTexture);
                });
        CameraHelper.CameraFacing cameraFacing = CameraHelper.CameraFacing.BACK;
        cameraHelper.startCamera(
                this, cameraFacing, /*unusedSurfaceTexture=*/ null, cameraTargetResolution());
    }

    protected Size computeViewSize(int width, int height) {
        return new Size(width, height);
    }

    protected void onPreviewDisplaySurfaceChanged(

            SurfaceHolder holder, int format, int width, int height) {
        // (Re-)Compute the ideal size of the camera-preview display (the area that the
        // camera-preview frames get rendered onto, potentially with scaling and rotation)
        // based on the size of the SurfaceView that contains the display.
        Size viewSize = computeViewSize(width, height);
        Size displaySize = cameraHelper.computeDisplaySizeFromViewSize(viewSize);
        boolean isCameraRotated = cameraHelper.isCameraRotated();

        //displaySize.getHeight(); ????????? ??????????????? ???????????? ??????
        //displaySize.getWidth();


        // Connect the converter to the camera-preview frames as its input (via
        // previewFrameTexture), and configure the output width and height as the computed
        // display size.
        converter.setSurfaceTextureAndAttachToGLContext(
                previewFrameTexture,
                isCameraRotated ? displaySize.getHeight()  : displaySize.getWidth(),
                isCameraRotated ? displaySize.getWidth() : displaySize.getHeight());
    }

    private ArrayList<Double> f_get_Angle_Array() {
        ArrayList<Double> Angle = new ArrayList<Double>();
        ArrayList<PoseLandMark> poseMarkers = new ArrayList<PoseLandMark>();
        if(poseLandmarks == null) return null;
        int landmarkIndex = 0;
        for (NormalizedLandmark landmark : poseLandmarks.getLandmarkList()) {
            PoseLandMark marker = new PoseLandMark(landmark.getX(), landmark.getY(), landmark.getVisibility());
//          poseLandmarkStr += "\tLandmark ["+ landmarkIndex+ "]: ("+ (landmark.getX()*720)+ ", "+ (landmark.getY()*1280)+ ", "+ landmark.getVisibility()+ ")\n";
            ++landmarkIndex;
            poseMarkers.add(marker);
        }
        double rightAngle = getAngle(poseMarkers.get(16), poseMarkers.get(14), poseMarkers.get(12));
        double leftAngle = getAngle(poseMarkers.get(15), poseMarkers.get(13), poseMarkers.get(11));
        double rightKnee = getAngle(poseMarkers.get(24), poseMarkers.get(26), poseMarkers.get(28));
        double leftKnee = getAngle(poseMarkers.get(23), poseMarkers.get(25), poseMarkers.get(27));
        double rightShoulder = getAngle(poseMarkers.get(14), poseMarkers.get(12), poseMarkers.get(24));
        double leftShoulder = getAngle(poseMarkers.get(13), poseMarkers.get(11), poseMarkers.get(23));
        Angle.add(rightAngle);
        Angle.add(leftAngle);
        Angle.add(rightKnee);
        Angle.add(leftKnee);
        Angle.add(rightShoulder);
        Angle.add(leftShoulder);
        return Angle;
    }

    private void setupPreviewDisplayView() {
        previewDisplayView.setVisibility(View.GONE);

        ViewGroup viewGroup = findViewById(R.id.preview_display_layout);
        //ViewGroup viewGroup1 = findViewById(R.id.preview_display_layout_2);
        //viewGroup1.addView(previewDisplayView);
        viewGroup.addView(previewDisplayView);
        my_view = new myView(findViewById(R.id.preview_display_layout).getContext());
        viewGroup.addView(my_view);
        //Log.v(TAG, get_Angle_String());
        previewDisplayView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
                                Log.d("Surface", "Surface Created");

                            }

                            @Override
                            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                                onPreviewDisplaySurfaceChanged(holder, format, width, height);
                                // ???????????? width , height ??? 720,128
                                Log.d("Surface", "Surface Changed");
                            }

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(null);
                                Log.d("Surface", "Surface destroy");
                            }

                        });
    }

    //?????? ???????????? landmark??? ????????? ???????????? ??? ??????.
    //[0.0 , 1.0] ?????? normazlized ??? coordinate -> image width, height
    private static String getPoseLandmarksDebugString(NormalizedLandmarkList poseLandmarks) {
        String poseLandmarkStr = "Pose landmarks: " + poseLandmarks.getLandmarkCount() + "\n";
        ArrayList<PoseLandMark> poseMarkers = new ArrayList<PoseLandMark>();
        int landmarkIndex = 0;
        for (NormalizedLandmark landmark : poseLandmarks.getLandmarkList()) {
            PoseLandMark marker = new PoseLandMark(landmark.getX(), landmark.getY(), landmark.getVisibility());
//          poseLandmarkStr += "\tLandmark ["+ landmarkIndex+ "]: ("+ (landmark.getX()*720)+ ", "+ (landmark.getY()*1280)+ ", "+ landmark.getVisibility()+ ")\n";
            ++landmarkIndex;
            poseMarkers.add(marker);
        }
        // Get Angle of Positions
        double rightAngle = getAngle(poseMarkers.get(16), poseMarkers.get(14), poseMarkers.get(12));
        double leftAngle = getAngle(poseMarkers.get(15), poseMarkers.get(13), poseMarkers.get(11));
        double rightKnee = getAngle(poseMarkers.get(24), poseMarkers.get(26), poseMarkers.get(28));
        double leftKnee = getAngle(poseMarkers.get(23), poseMarkers.get(25), poseMarkers.get(27));
        double rightShoulder = getAngle(poseMarkers.get(14), poseMarkers.get(12), poseMarkers.get(24));
        double leftShoulder = getAngle(poseMarkers.get(13), poseMarkers.get(11), poseMarkers.get(23));
        /*
        Log.v(TAG, "======Degree Of Position]======\n" +
                "rightAngle :" + rightAngle + "\n" +
                "leftAngle :" + leftAngle + "\n" +
                "rightHip :" + rightKnee + "\n" +
                "leftHip :" + leftKnee + "\n" +
                "rightShoulder :" + rightShoulder + "\n" +
                "leftShoulder :" + leftShoulder + "\n");
        */

        //Log.v(TAG, Str_angle);
        return poseLandmarkStr;
        /*
           16 ?????? ?????? 14 ?????? ????????? 12 ?????? ?????? --> ????????? ??????
           15 ?????? ?????? 13 ?????? ????????? 11 ?????? ?????? --> ???  ??? ??????
           24 ?????? ?????? 26 ?????? ??????   28 ?????? ?????? --> ???????????? ??????
           23 ?????? ?????? 25 ?????? ??????   27 ?????? ?????? --> ??? ?????? ??????
           14 ?????? ?????? 12 ?????? ??????   24 ?????? ?????? --> ?????? ???????????? ??????
           13 ???   ?????? 11 ???  ??????   23  ???  ?????? --> ?????? ???????????? ??????
        */
    }

    static double getAngle(PoseLandMark firstPoint, PoseLandMark midPoint, PoseLandMark lastPoint) {
        double result =
                Math.toDegrees(
                        Math.atan2((lastPoint.getY() - midPoint.getY()) * f_height, (lastPoint.getX() - midPoint.getX()) * f_width)
                                - Math.atan2((firstPoint.getY() - midPoint.getY()) * f_height, (firstPoint.getX() - midPoint.getX()) * f_width));
        result = Math.abs(result); // Angle should never be negative
        if (result > 180) {
            result = (360.0 - result); // Always get the acute representation of the angle
        }
        return result;
    }

    public float f_get_dist(PoseLandMark a, PoseLandMark b, int width, int height) {
        return (float)Math.sqrt(Math.pow((a.x - b.x) * width, 2.0)+ Math.pow((a.y - b.y) * height, 2.0));
    }

    public float f_get_cross(PoseLandMark a, PoseLandMark b, int width, int height, Boolean flag) {

        double vx = (a.x - b.x) * width;
        double vy = 1 - (a.y - b.y) * height;
        double t_vx = 100.0;
        double t_vy = 0.0;
        Log.v(TAG, Double.toString(vx) + " " + Double.toString(vy));
        //Log.v(TAG, Double.toString(a.y));
        double a1 = (vx * t_vy - vy * t_vx) / (Math.sqrt(vx * vx + vy * vy) * Math.sqrt(t_vx * t_vx + t_vy * t_vy));
        double test = Math.asin(a1);
        test = test / 3.14 * 180;
        Log.v(TAG, "asin : " + Double.toString(test) + " A1 : " + Double.toString(a1));
        double answer = 180 - test ;
        if(answer < 0) answer += 360;
        return (float)answer;
    }

    public float f_dist(int width, int height) {
        ArrayList<PoseLandMark> poseMarkers = new ArrayList<PoseLandMark>();
        int landmarkIndex = 0;
        for (NormalizedLandmark landmark : poseLandmarks.getLandmarkList()) {
            PoseLandMark marker = new PoseLandMark(landmark.getX(), landmark.getY(), landmark.getVisibility());
            ++landmarkIndex;
            poseMarkers.add(marker);
        }
        float right = f_get_dist(poseMarkers.get(11), poseMarkers.get(13), width, height) / 2.0f;
        float left = f_get_dist(poseMarkers.get(12), poseMarkers.get(14), width, height) / 2.0f;
        return (right > left) ? left : right;
    }

    public class myView extends View {
        public myView(Context context) {
            super(context);
        }

        public void onDraw(Canvas canvas) {;
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            if(view_flag) {
                int width = getWidth();
                int height = getHeight();
                f_width = width;
                f_height = height;
                ArrayList<PoseLandMark> poseMarkers = new ArrayList<PoseLandMark>();
                int landmarkIndex = 0;
                for (NormalizedLandmark landmark : poseLandmarks.getLandmarkList()) {
                    PoseLandMark marker = new PoseLandMark(landmark.getX(), landmark.getY(), landmark.getVisibility());
                    ++landmarkIndex;
                    poseMarkers.add(marker);
                }
                if(avg_dist == 0.0f) avg_dist = f_dist(width, height);
                float dist = (avg_dist + f_dist(width, height))  / 2;
                //float dist = avg_dist;

                ArrayList<Double> Angle_array = f_get_Angle_Array();
                Paint paint = new Paint();
                paint.setColor(Color.BLUE);
                paint.setStrokeWidth(10F);
                paint.setStyle(Paint.Style.STROKE);
                //Log.v(TAG, Float.toString(poseMarkers.get(11).x) + " " + Float.toString(poseMarkers.get(11).y));
                //float test = f_get_cross(poseMarkers.get(11), poseMarkers.get(13), width, height);
                //Log.v(TAG, "test : " + Float.toString(test));

                /*dist
                RectF right_shoulder_rect = new RectF();
                right_shoulder_rect.set(poseMarkers.get(11).x * width - dist, poseMarkers.get(11).y * height - dist, poseMarkers.get(11).x * width + dist, poseMarkers.get(11).y * height + dist);
                canvas.drawArc(right_shoulder_rect, test, Angle_array.get(5).floatValue(), true, paint);
                //canvas.drawCircle(poseMarkers.get(11).x * width, poseMarkers.get(11).y * height, f_dist(width, height), paint);
                 */


                float left_arm = f_get_cross(poseMarkers.get(11), poseMarkers.get(13), width, height, false);
                //Log.v(TAG, "test : " + Float.toString(test));
                RectF left_arm_rect = new RectF();
                left_arm_rect.set(poseMarkers.get(13).x * width - dist, poseMarkers.get(13).y * height - dist, poseMarkers.get(13).x * width + dist, poseMarkers.get(13).y * height + dist);
                canvas.drawArc(left_arm_rect, left_arm, Angle_array.get(1).floatValue(), true, paint);

                float right_arm = 360.0f - f_get_cross(poseMarkers.get(14), poseMarkers.get(16), width, height, false);
                //Log.v(TAG, "test : " + Float.toString(test));
                RectF right_arm_rect = new RectF();
                right_arm_rect.set(poseMarkers.get(14).x * width - dist, poseMarkers.get(14).y * height - dist, poseMarkers.get(14).x * width + dist, poseMarkers.get(14).y * height + dist);
                canvas.drawArc(right_arm_rect, right_arm, Angle_array.get(0).floatValue(), true, paint);


                state = "idle";
                if(Math.abs(Angle_array.get(0)) - 180.0 <= 30.0 && Math.abs(Angle_array.get(1)) - 180.0 <= 30.0) {
                    if(Math.abs(Angle_array.get(0)) - 180.0 >= 15.0 || Math.abs(Angle_array.get(1)) - 180.0 >= 15.0) {
                        state = "test1";
                    }
                }

                else if(Math.abs(Angle_array.get(0)) - 90.0 <= 30.0 && Math.abs(Angle_array.get(1)) - 90.0 <= 30.0) {
                    if(Math.abs(Angle_array.get(0)) - 90.0 >= 15.0 || Math.abs(Angle_array.get(1)) - 90.0 >= 15.0) {
                        state = "test2";
                    }
                }

                if(Angle_array.get(0) >= 90) state = "ttets1";
                else state = "ttest2";

                Log.v(TAG, Double.toString(Math.abs(Angle_array.get(0))) + " " + Double.toString(Math.abs(Angle_array.get(1))) );
                paint.setTextSize(120);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(state, f_width / 2.0f, 120, paint);


                 /*
           16 ?????? ?????? 14 ?????? ????????? 12 ?????? ?????? --> ????????? ??????
           15 ?????? ?????? 13 ?????? ????????? 11 ?????? ?????? --> ???  ??? ??????
           24 ?????? ?????? 26 ?????? ??????   28 ?????? ?????? --> ???????????? ??????
           23 ?????? ?????? 25 ?????? ??????   27 ?????? ?????? --> ??? ?????? ??????
           14 ?????? ?????? 12 ?????? ??????   24 ?????? ?????? --> ?????? ???????????? ??????
           13 ???   ?????? 11 ???  ??????   23  ???  ?????? --> ?????? ???????????? ??????
        */
            }
            else {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                Log.v(TAG, "reset");
            }
        }
    }
}