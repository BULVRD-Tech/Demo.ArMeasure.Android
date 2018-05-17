package com.arwrld.armeasure;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.arwrld.armeasure.ar.BackgroundRenderer;
import com.arwrld.armeasure.ar.DisplayRotationHelper;
import com.arwrld.armeasure.ar.ObjectRenderer;
import com.arwrld.armeasure.ar.PlaneRenderer;
import com.arwrld.armeasure.ar.PointCloudRenderer;
import com.arwrld.armeasure.ar.RectanglePolygonRenderer;
import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.NotTrackingException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnNeverAskAgain;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ArRulerActivity";
    private static final String ASSET_NAME_CUBE_OBJ = "cube.obj";
    private static final String ASSET_NAME_CUBE = "cube_green.png";
    private static final String ASSET_NAME_CUBE_SELECTED = "cube_cyan.png";

    private static final String NEED_ALERT = "needAlert_preview2";
    private static final int MAX_CUBE_COUNT = 16;

    private GLSurfaceView mSurfaceView = null;

    private Session mSession = null;
    private BackgroundRenderer mBackgroundRenderer = new BackgroundRenderer();
    private GestureDetector mGestureDetector;
    private Snackbar mLoadingMessageSnackbar = null;
    private DisplayRotationHelper mDisplayRotationHelper;

    private RectanglePolygonRenderer mRectRenderer;

    private PlaneRenderer mPlaneRenderer = new PlaneRenderer();
    private PointCloudRenderer mPointCloud = new PointCloudRenderer();

    private final float[] mAnchorMatrix = new float[MAX_CUBE_COUNT];

    private ArrayBlockingQueue<MotionEvent> mQueuedSingleTaps = new ArrayBlockingQueue<>(MAX_CUBE_COUNT);
    private ArrayBlockingQueue<MotionEvent> mQueuedLongPress = new ArrayBlockingQueue<>(MAX_CUBE_COUNT);
    private final ArrayList<Anchor> mAnchors = new ArrayList<>();
    private ArrayList<Float> mShowingTapPointX = new ArrayList<>();
    private ArrayList<Float> mShowingTapPointY = new ArrayList<>();

    private ArrayBlockingQueue<Float> mQueuedScrollDx = new ArrayBlockingQueue<>(MAX_CUBE_COUNT);
    private ArrayBlockingQueue<Float> mQueuedScrollDy = new ArrayBlockingQueue<>(MAX_CUBE_COUNT);

    private ObjectRenderer mCube = new ObjectRenderer();
    private ObjectRenderer mCubeSelected = new ObjectRenderer();

    TextView tv_result;
    private LinearLayout banner;

    private static final String extraVal = "ArWrldArMeasure";

    private GLSurfaceRenderer glSerfaceRenderer = null;
    private View.OnTouchListener onTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            return mGestureDetector.onTouchEvent(event);
        }
    };
    private GestureDetector.SimpleOnGestureListener gestureDetectorListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            mQueuedSingleTaps.offer(e);
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            mQueuedLongPress.offer(e);
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                float distanceX, float distanceY) {
            mQueuedScrollDx.offer(distanceX);
            mQueuedScrollDy.offer(distanceY);
            return true;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv_result = findViewById(R.id.tv_result);
        banner = findViewById(R.id.banner);

        if (isPackageInstalled("com.google.ar.core", getPackageManager())) {
            initSurface();
        } else {
            Toast.makeText(getBaseContext(), "This device does not support ARCore", Toast.LENGTH_LONG).show();
            this.finish();
        }

        banner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse("https://bulvrdapp.com" + "?ref=" + extraVal + "?utm_source=" + extraVal + "?from=" + extraVal));
                startActivity(i);
            }
        });
    }

    private void toast(String string) {
        Toast.makeText(this, string, Toast.LENGTH_SHORT).show();
    }

    private boolean isVerticalMode = false;

    private SharedPreferences getSharedPreferences() {
        return getSharedPreferences("ArMeasureActivity", MODE_PRIVATE);
    }

    private boolean isPackageInstalled(String packagename, PackageManager packageManager) {
        try {
            packageManager.getPackageInfo(packagename, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void showNotSupportAlert(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setCancelable(false)
                .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                })
                .create().show();
    }

    private boolean initSurface() {
        Exception exception = null;
        String message = null;
        try {
            mSession = new Session(/* context= */ this);
        } catch (UnavailableArcoreNotInstalledException e) {
            message = "Please install ARCore";
            exception = e;
        } catch (UnavailableApkTooOldException e) {
            message = "Please update ARCore";
            exception = e;
        } catch (UnavailableSdkTooOldException e) {
            message = "Please update this app";
            exception = e;
        } catch (Exception e) {
            message = "This device does not support AR";
            exception = e;
        }

        if (message != null) {
            showNotSupportAlert(message);
            Log.e(TAG, "Exception creating session", exception);
            return false;
        }

        Config config = new Config(mSession);
        if (!mSession.isSupported(config)) {
            showNotSupportAlert("This device does not support AR");
            return false;
        }
        mSession.configure(config);

        glSerfaceRenderer = new GLSurfaceRenderer(this);
        mSurfaceView = new GLSurfaceView(this);
        mDisplayRotationHelper = new DisplayRotationHelper(/*context=*/ this);
        FrameLayout flContent = findViewById(R.id.fl_content);
        flContent.addView(mSurfaceView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mSurfaceView.setPreserveEGLContextOnPause(true);
        mSurfaceView.setEGLContextClientVersion(2);
        mSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        mSurfaceView.setRenderer(glSerfaceRenderer);
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        mGestureDetector = new GestureDetector(this, gestureDetectorListener);
        mSurfaceView.setOnTouchListener(onTouchListener);
        return true;
    }

    private boolean isSurfaceResume = false;

    private void surfaceOnResume() {
        try {
            if (isSurfaceResume) {
                return;
            }
            if (mSession == null) {
                return;
            }
            if (mSurfaceView != null) {
                showLoadingMessage();
                mSession.resume();
                mSurfaceView.onResume();
                mDisplayRotationHelper.onResume();
                isSurfaceResume = true;
            }
        }catch (Exception e){

        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Standard Android full-screen functionality.
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        MainActivityPermissionsDispatcher.showCameraWithPermissionCheck(MainActivity.this);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (isSurfaceResume) {
            if (mSession != null) {
                if (mDisplayRotationHelper != null) {
                    mDisplayRotationHelper.onPause();
                }
                if (mSurfaceView != null) {
                    mSurfaceView.onPause();
                }
                mSession.pause();
                isSurfaceResume = false;
            }
        }
    }

    @NeedsPermission({Manifest.permission.CAMERA})
    void showCamera() {
        surfaceOnResume();
    }

    @OnShowRationale({Manifest.permission.CAMERA})
    void showRationaleForCamera(final PermissionRequest request) {

    }

    @OnPermissionDenied({Manifest.permission.CAMERA})
    void showDeniedForCamera() {
        Toast.makeText(getBaseContext(), "Augmented Reality features require camera permissions!", Toast.LENGTH_SHORT).show();
    }

    @OnNeverAskAgain({Manifest.permission.CAMERA})
    void showNeverAskForCamera() {
        Toast.makeText(getBaseContext(), "Augmented Reality features require camera permissions!", Toast.LENGTH_SHORT).show();
    }

    private void showLoadingMessage() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLoadingMessageSnackbar = Snackbar.make(
                        MainActivity.this.findViewById(android.R.id.content),
                        "Searching for surfaces...", Snackbar.LENGTH_INDEFINITE);
                mLoadingMessageSnackbar.getView().setBackgroundColor(0xbf323232);
                mLoadingMessageSnackbar.show();
            }
        });
    }

    private void hideLoadingMessage() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLoadingMessageSnackbar.dismiss();
                mLoadingMessageSnackbar = null;
            }
        });
    }

    private class GLSurfaceRenderer implements GLSurfaceView.Renderer {
        private static final String TAG = "GLSurfaceRenderer";
        private Context context;
        private final int DEFAULT_VALUE = -1;
        private int nowTouchingPointIndex = DEFAULT_VALUE;
        private int viewWidth = 0;
        private int viewHeight = 0;
        // according to cube.obj, cube diameter = 0.02f
        private final float cubeHitAreaRadius = 0.08f;
        private final float[] centerVertexOfCube = {0f, 0f, 0f, 1};
        private final float[] vertexResult = new float[4];

        private float[] tempTranslation = new float[3];
        private float[] tempRotation = new float[4];
        private float[] projmtx = new float[16];
        private float[] viewmtx = new float[16];

        public GLSurfaceRenderer(Context context) {
            this.context = context;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

            mBackgroundRenderer.createOnGlThread(context);
            if (mSession != null) {
                mSession.setCameraTextureName(mBackgroundRenderer.getTextureId());
            }

            try {
                mRectRenderer = new RectanglePolygonRenderer();
                mCube.createOnGlThread(context, ASSET_NAME_CUBE_OBJ, ASSET_NAME_CUBE);
                mCube.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f);
                mCubeSelected.createOnGlThread(context, ASSET_NAME_CUBE_OBJ, ASSET_NAME_CUBE_SELECTED);
                mCubeSelected.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f);
            } catch (IOException e) {
            }
            try {
                mPlaneRenderer.createOnGlThread(context, "trigrid.png");
            } catch (IOException e) {
            }
            mPointCloud.createOnGlThread(context);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            if (width <= 0 || height <= 0) {
                return;
            }

            mDisplayRotationHelper.onSurfaceChanged(width, height);
            GLES20.glViewport(0, 0, width, height);
            viewWidth = width;
            viewHeight = height;
            setNowTouchingPointIndex(DEFAULT_VALUE);
        }

        public void setNowTouchingPointIndex(int index) {
            nowTouchingPointIndex = index;
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            if (viewWidth == 0) {
                return;
            }
            if (mSession == null) {
                return;
            }
            mDisplayRotationHelper.updateSessionIfNeeded(mSession);

            try {
                Frame frame = mSession.update();
                Camera camera = frame.getCamera();
                // Draw background.
                mBackgroundRenderer.draw(frame);

                if (camera.getTrackingState() == TrackingState.PAUSED) {
                    return;
                }

                camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

                camera.getViewMatrix(viewmtx, 0);

                final float lightIntensity = frame.getLightEstimate().getPixelIntensity();

                PointCloud pointCloud = frame.acquirePointCloud();
                mPointCloud.update(pointCloud);
                mPointCloud.draw(viewmtx, projmtx);

                pointCloud.release();

                if (mLoadingMessageSnackbar != null) {
                    for (Plane plane : mSession.getAllTrackables(Plane.class)) {
                        if (plane.getType() == com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING &&
                                plane.getTrackingState() == TrackingState.TRACKING) {
                            hideLoadingMessage();
                            break;
                        }
                    }
                }

                mPlaneRenderer.drawPlanes(
                        mSession.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projmtx);

                if (mAnchors.size() < 1) {
                    // no point
                    showResult("");
                } else {
                    if (nowTouchingPointIndex != DEFAULT_VALUE) {
                        drawObj(getPose(mAnchors.get(nowTouchingPointIndex)), mCubeSelected, viewmtx, projmtx, lightIntensity);
                        checkIfHit(mCubeSelected, nowTouchingPointIndex);
                    }
                    StringBuilder sb = new StringBuilder();
                    double total = 0;
                    Pose point1;
                    Pose point0 = getPose(mAnchors.get(0));
                    drawObj(point0, mCube, viewmtx, projmtx, lightIntensity);
                    checkIfHit(mCube, 0);
                    for (int i = 1; i < mAnchors.size(); i++) {
                        point1 = getPose(mAnchors.get(i));
                        drawObj(point1, mCube, viewmtx, projmtx, lightIntensity);
                        checkIfHit(mCube, i);
                        drawLine(point0, point1, viewmtx, projmtx);

                        float distanceCm = ((int) (getDistance(point0, point1) * 1000)) / 10.0f;
                        total += distanceCm;
                        sb.append(" + ").append(distanceCm);

                        point0 = point1;
                    }

                    String result = sb.toString().replaceFirst("[+]", "") + " = " + (((int) (total * 10f)) / 10f) + "cm";
                    showResult(result);
                }

                // check if there is any touch event
                MotionEvent tap = mQueuedSingleTaps.poll();
                if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
                    for (HitResult hit : frame.hitTest(tap)) {
                        Trackable trackable = hit.getTrackable();
                        if (trackable instanceof Plane
                                && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                            if (mAnchors.size() >= 16) {
                                mAnchors.get(0).detach();
                                mAnchors.remove(0);

                                mShowingTapPointX.remove(0);
                                mShowingTapPointY.remove(0);
                            }

                            mAnchors.add(hit.createAnchor());

                            mShowingTapPointX.add(tap.getX());
                            mShowingTapPointY.add(tap.getY());
                            nowTouchingPointIndex = mAnchors.size() - 1;

                            showMoreAction();
                            break;
                        }
                    }
                } else {
                    handleMoveEvent(nowTouchingPointIndex);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Exception on the OpenGL thread", t);
            }
        }

        private void handleMoveEvent(int nowSelectedIndex) {
            try {
                if (mShowingTapPointX.size() < 1 || mQueuedScrollDx.size() < 2) {
                    // no action, don't move
                    return;
                }
                if (nowTouchingPointIndex == DEFAULT_VALUE) {
                    // no selected cube, don't move
                    return;
                }
                if (nowSelectedIndex >= mShowingTapPointX.size()) {
                    // wrong index, don't move.
                    return;
                }
                float scrollDx = 0;
                float scrollDy = 0;
                int scrollQueueSize = mQueuedScrollDx.size();
                for (int i = 0; i < scrollQueueSize; i++) {
                    scrollDx += mQueuedScrollDx.poll();
                    scrollDy += mQueuedScrollDy.poll();
                }

                if (isVerticalMode) {
                    Anchor anchor = mAnchors.remove(nowSelectedIndex);
                    anchor.detach();
                    setPoseDataToTempArray(getPose(anchor));
                    tempTranslation[1] += (scrollDy / viewHeight);
                    mAnchors.add(nowSelectedIndex,
                            mSession.createAnchor(new Pose(tempTranslation, tempRotation)));
                } else {
                    float toX = mShowingTapPointX.get(nowSelectedIndex) - scrollDx;
                    mShowingTapPointX.remove(nowSelectedIndex);
                    mShowingTapPointX.add(nowSelectedIndex, toX);

                    float toY = mShowingTapPointY.get(nowSelectedIndex) - scrollDy;
                    mShowingTapPointY.remove(nowSelectedIndex);
                    mShowingTapPointY.add(nowSelectedIndex, toY);

                    if (mAnchors.size() > nowSelectedIndex) {
                        Anchor anchor = mAnchors.remove(nowSelectedIndex);
                        anchor.detach();
                        // remove duplicated anchor
                        setPoseDataToTempArray(getPose(anchor));
                        tempTranslation[0] -= (scrollDx / viewWidth);
                        tempTranslation[2] -= (scrollDy / viewHeight);
                        mAnchors.add(nowSelectedIndex,
                                mSession.createAnchor(new Pose(tempTranslation, tempRotation)));
                    }
                }
            } catch (NotTrackingException e) {
                e.printStackTrace();
            }
        }

        private final float[] mPoseTranslation = new float[3];
        private final float[] mPoseRotation = new float[4];

        private Pose getPose(Anchor anchor) {
            Pose pose = anchor.getPose();
            pose.getTranslation(mPoseTranslation, 0);
            pose.getRotationQuaternion(mPoseRotation, 0);
            return new Pose(mPoseTranslation, mPoseRotation);
        }

        private void setPoseDataToTempArray(Pose pose) {
            pose.getTranslation(tempTranslation, 0);
            pose.getRotationQuaternion(tempRotation, 0);
        }

        private void drawLine(Pose pose0, Pose pose1, float[] viewmtx, float[] projmtx) {
            float lineWidth = 0.002f;
            float lineWidthH = lineWidth / viewHeight * viewWidth;
            mRectRenderer.setVerts(
                    pose0.tx() - lineWidth, pose0.ty() + lineWidthH, pose0.tz() - lineWidth,
                    pose0.tx() + lineWidth, pose0.ty() + lineWidthH, pose0.tz() + lineWidth,
                    pose1.tx() + lineWidth, pose1.ty() + lineWidthH, pose1.tz() + lineWidth,
                    pose1.tx() - lineWidth, pose1.ty() + lineWidthH, pose1.tz() - lineWidth
                    ,
                    pose0.tx() - lineWidth, pose0.ty() - lineWidthH, pose0.tz() - lineWidth,
                    pose0.tx() + lineWidth, pose0.ty() - lineWidthH, pose0.tz() + lineWidth,
                    pose1.tx() + lineWidth, pose1.ty() - lineWidthH, pose1.tz() + lineWidth,
                    pose1.tx() - lineWidth, pose1.ty() - lineWidthH, pose1.tz() - lineWidth
            );

            mRectRenderer.draw(viewmtx, projmtx);
        }

        private void drawObj(Pose pose, ObjectRenderer renderer, float[] cameraView, float[] cameraPerspective, float lightIntensity) {
            pose.toMatrix(mAnchorMatrix, 0);
            renderer.updateModelMatrix(mAnchorMatrix, 1);
            renderer.draw(cameraView, cameraPerspective, lightIntensity);
        }

        private void checkIfHit(ObjectRenderer renderer, int cubeIndex) {
            if (isMVPMatrixHitMotionEvent(renderer.getModelViewProjectionMatrix(), mQueuedLongPress.peek())) {
                // long press hit a cube, show context menu for the cube
                nowTouchingPointIndex = cubeIndex;
                mQueuedLongPress.poll();
                showMoreAction();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                    }
                });
            } else if (isMVPMatrixHitMotionEvent(renderer.getModelViewProjectionMatrix(), mQueuedSingleTaps.peek())) {
                nowTouchingPointIndex = cubeIndex;
                mQueuedSingleTaps.poll();
                showMoreAction();
            }
        }

        private boolean isMVPMatrixHitMotionEvent(float[] ModelViewProjectionMatrix, MotionEvent event) {
            if (event == null) {
                return false;
            }
            Matrix.multiplyMV(vertexResult, 0, ModelViewProjectionMatrix, 0, centerVertexOfCube, 0);
            float radius = (viewWidth / 2) * (cubeHitAreaRadius / vertexResult[3]);
            float dx = event.getX() - (viewWidth / 2) * (1 + vertexResult[0] / vertexResult[3]);
            float dy = event.getY() - (viewHeight / 2) * (1 - vertexResult[1] / vertexResult[3]);
            double distance = Math.sqrt(dx * dx + dy * dy);
            return distance < radius;
        }

        private double getDistance(Pose pose0, Pose pose1) {
            float dx = pose0.tx() - pose1.tx();
            float dy = pose0.ty() - pose1.ty();
            float dz = pose0.tz() - pose1.tz();
            return Math.sqrt(dx * dx + dz * dz + dy * dy);
        }

        private void showResult(final String result) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tv_result.setText(result);
                }
            });
        }

        private void showMoreAction() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                }
            });
        }
    }
}
