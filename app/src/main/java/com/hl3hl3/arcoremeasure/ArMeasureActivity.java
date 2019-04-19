package com.hl3hl3.arcoremeasure;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.BaseTransientBottomBar;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.crashlytics.android.Crashlytics;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.helloar.CameraPermissionHelper;
import com.google.ar.core.examples.java.helloar.DisplayRotationHelper;
import com.google.ar.core.examples.java.helloar.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.helloar.rendering.ObjectRenderer;
import com.google.ar.core.examples.java.helloar.rendering.PlaneRenderer;
import com.google.ar.core.examples.java.helloar.rendering.PointCloudRenderer;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotTrackingException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.hl3hl3.arcoremeasure.renderer.RectanglePolygonRenderer;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import io.fabric.sdk.android.Fabric;

import static com.hl3hl3.arcoremeasure.Globals.autoCalculatedDistance;
import static com.hl3hl3.arcoremeasure.Globals.notRecording;
import static com.hl3hl3.arcoremeasure.Globals.stopPoints;

public class ArMeasureActivity extends AppCompatActivity implements SensorEventListener {


    private final float[] mAccelerometerReading = new float[3];
    private final float[] mMagnetometerReading = new float[3];
    private float[] mAccelerometerData = new float[3];
    private float[] mMagnetometerData = new float[3];

    private TextView mTextSensorPitch;
    private TextView mTextSensorRoll;
    private TextView value_roll8;
    private TextView value_roll7;

    private TextView click;
    private Display mDisplay;

    private static final float VALUE_DRIFT = 0.05f;


    private final float[] mRotationMatrix = new float[9];
    private final float[] mOrientationAngles = new float[3];

    private SensorManager sen;
    Sensor acc;
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE = 1000;
    private int mScreenDensity;
    private MediaProjectionManager mProjectionManager;


    private static int DISPLAY_WIDTH = 0;
    private static int DISPLAY_HEIGHT = 0;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionCallback mMediaProjectionCallback;
    private ToggleButton mToggleButton;
    private ToggleButton mToggleButton2;

    private MediaRecorder mMediaRecorder;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_PERMISSIONS = 10;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    // The cube created when user keeps the phone stable
    private static final String ASSET_NAME_CUBE_OBJ = "cube.obj";
    private static final String ASSET_NAME_CUBE = "cube_green.png";
    private static final String ASSET_NAME_CUBE_SELECTED = "cube_cyan.png";

    private static final int MAX_CUBE_COUNT = 16;

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private GLSurfaceView surfaceView = null;

    // For ARCore installation request
    private boolean installRequested;

    // Everything on surfaceView
    private Session session = null;

    // For Click detection
    private GestureDetector gestureDetector;


    private Snackbar messageSnackbar = null;
    private DisplayRotationHelper displayRotationHelper;

    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final PlaneRenderer planeRenderer = new PlaneRenderer();
    private final PointCloudRenderer pointCloud = new PointCloudRenderer();

    private final ObjectRenderer cube = new ObjectRenderer();
    private final ObjectRenderer cubeSelected = new ObjectRenderer();
    private RectanglePolygonRenderer rectRenderer = null;

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private final float[] anchorMatrix = new float[MAX_CUBE_COUNT];
    private final ImageView[] ivCubeIconList = new ImageView[MAX_CUBE_COUNT];
    private final int[] cubeIconIdArray = {
            R.id.iv_cube1,
            R.id.iv_cube2,
            R.id.iv_cube3,
            R.id.iv_cube4,
            R.id.iv_cube5,
            R.id.iv_cube6,
            R.id.iv_cube7,
            R.id.iv_cube8,
            R.id.iv_cube9,
            R.id.iv_cube10,
            R.id.iv_cube11,
            R.id.iv_cube12,
            R.id.iv_cube13,
            R.id.iv_cube14,
            R.id.iv_cube15,
            R.id.iv_cube16
    };

    // Tap handling and UI.
    private ArrayBlockingQueue<MotionEvent> queuedSingleTaps = new ArrayBlockingQueue<>(MAX_CUBE_COUNT);
    private ArrayBlockingQueue<MotionEvent> queuedLongPress = new ArrayBlockingQueue<>(MAX_CUBE_COUNT);
    private final ArrayList<Anchor> anchors = new ArrayList<>();
    private ArrayList<Float> showingTapPointX = new ArrayList<>();
    private ArrayList<Float> showingTapPointY = new ArrayList<>();

    private ArrayBlockingQueue<Float> queuedScrollDx = new ArrayBlockingQueue<>(MAX_CUBE_COUNT);
    private ArrayBlockingQueue<Float> queuedScrollDy = new ArrayBlockingQueue<>(MAX_CUBE_COUNT);

    private void log(String tag, String log){
        if(BuildConfig.DEBUG) {
            Log.d(tag, log);
        }
    }

    private void log(Exception e){
        try {
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                e.printStackTrace();
            }
        }catch (Exception ex){
            if (BuildConfig.DEBUG) {
                ex.printStackTrace();
            }
        }
    }

    private void logStatus(String msg){
        try {
            Crashlytics.log(msg);
        }catch (Exception e){
            log(e);
        }
    }

    //    OverlayView overlayViewForTest;
    private TextView tv_result;
    private TextView tv_result2;


    private FloatingActionButton fab;

    private GLSurfaceRenderer glSerfaceRenderer = null;
    private GestureDetector.SimpleOnGestureListener gestureDetectorListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            // Queue tap if there is space. Tap is lost if queue is full.
            queuedSingleTaps.offer(e);
//            log(TAG, "onSingleTapUp, e=" + e.getRawX() + ", " + e.getRawY());
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            queuedLongPress.offer(e);
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                float distanceX, float distanceY) {
//            log(TAG, "onScroll, dx=" + distanceX + " dy=" + distanceY);
            queuedScrollDx.offer(distanceX);
            queuedScrollDy.offer(distanceY);
            return true;
        }
    };

    int i = 0;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        tv_result = findViewById(R.id.tv_result);
        tv_result2 = findViewById(R.id.tv_result2);


        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            // Do something here...
            mToggleButton.performClick();
            x++;
            Globals.click = true;
            mTextSensorPitch.setVisibility(View.VISIBLE);
            mTextSensorRoll.setVisibility(View.VISIBLE);
            value_roll8.setVisibility(View.VISIBLE);
            value_roll7.setVisibility(View.VISIBLE);

            tv_result2.setVisibility(View.VISIBLE);
            tv_result.setVisibility(View.VISIBLE);

            Globals.recording = false;
            if(y > 0){
                Config config = new Config(session);
                config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL);
                session.configure(config);
                Globals.notRecording = true;


            }

            //camera.getPose().tx();

            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            // Do something here...

            Config config = new Config(session);
            config.setPlaneFindingMode(Config.PlaneFindingMode.DISABLED);
            session.configure(config);



            mTextSensorPitch.setVisibility(View.INVISIBLE);
            mTextSensorRoll.setVisibility(View.INVISIBLE);
            value_roll8.setVisibility(View.INVISIBLE);
            value_roll7.setVisibility(View.INVISIBLE);
            tv_result2.setVisibility(View.INVISIBLE);
            tv_result.setVisibility(View.INVISIBLE);


            stopPoints = true;
            Globals.recording = true;
            Globals.notRecording = false;

            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    mToggleButton2.performClick();
                }
            }, 1000);



            // Globals.y++;

            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    Sensor gyro;
    Sensor mag;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sen = (SensorManager)getSystemService(Context.SENSOR_SERVICE);

        Fabric.with(this, new Crashlytics());
        setContentView(R.layout.activity_main);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        mDisplay = wm.getDefaultDisplay();

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int height = displayMetrics.heightPixels;
        int width = displayMetrics.widthPixels;

//        DISPLAY_WIDTH = surfaceView.getWidth();
//        DISPLAY_HEIGHT = surfaceView.getHeight();

//        Button shareAPK = (Button) findViewById(R.id.shareAPK);
//        shareAPK.setOnClickListener(new View.OnClickListener() {
//                                        @Override
//                                        public void onClick(View v) {
//                                            ApplicationInfo api = getApplicationContext().getApplicationInfo();
//                                            String apkPath = api.sourceDir;
//
//                                            Intent inten = new Intent(Intent.ACTION_SEND);
//                                            inten.setType("application/vnd.android.package-archive");
//
//                                            inten.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(apkPath)));
//                                            startActivity(Intent.createChooser(inten, "SHARE APP USING"));
//                                        }
//
//                                    });






        // mTextSensorAzimuth = (TextView) findViewById(R.id.value_azimuth);
        mTextSensorPitch = (TextView) findViewById(R.id.value_pitch);
        mTextSensorRoll = (TextView) findViewById(R.id.value_roll);
        value_roll8 = (TextView) findViewById(R.id.value_roll8);
        value_roll7 = (TextView) findViewById(R.id.value_roll7);


        click =  (TextView)findViewById(R.id.click);
        mScreenDensity = metrics.densityDpi;

        mMediaRecorder = new MediaRecorder();

        mProjectionManager = (MediaProjectionManager) getSystemService
                (Context.MEDIA_PROJECTION_SERVICE);

        mToggleButton = (ToggleButton) findViewById(R.id.toggle);
        mToggleButton2 = (ToggleButton) findViewById(R.id.toggle2);

        mToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(ArMeasureActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) + ContextCompat
                        .checkSelfPermission(ArMeasureActivity.this,
                                Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale
                            (ArMeasureActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                            ActivityCompat.shouldShowRequestPermissionRationale
                                    (ArMeasureActivity.this, Manifest.permission.RECORD_AUDIO)) {
                        mToggleButton.setChecked(false);
                        Snackbar.make(findViewById(android.R.id.content), R.string.need_permission,
                                Snackbar.LENGTH_INDEFINITE).setAction("ENABLE",
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        ActivityCompat.requestPermissions(ArMeasureActivity.this,
                                                new String[]{Manifest.permission
                                                        .WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO},
                                                REQUEST_PERMISSIONS);
                                    }
                                }).show();
                    } else {
                        ActivityCompat.requestPermissions(ArMeasureActivity.this,
                                new String[]{Manifest.permission
                                        .WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO},
                                REQUEST_PERMISSIONS);
                    }
                } else {
                    onToggleScreenShare(v);
                }
            }
        });



        mToggleButton2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Globals.y++;

                mMediaRecorder.start();


            }
        });
//        overlayViewForTest = (OverlayView)findViewById(R.id.overlay_for_test);
        tv_result = findViewById(R.id.tv_result);
        tv_result = findViewById(R.id.tv_result2);

        fab = findViewById(R.id.fab);

        for(int i=0; i<cubeIconIdArray.length; i++){
            ivCubeIconList[i] = findViewById(cubeIconIdArray[i]);
            ivCubeIconList[i].setTag(i);
            ivCubeIconList[i].setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View view) {
                    try {
                        int index = Integer.valueOf(view.getTag().toString());
                        logStatus("click index cube: " + index);
                        glSerfaceRenderer.setNowTouchingPointIndex(index);
                        glSerfaceRenderer.showMoreAction();
                    }catch (Exception e){
                        log(e);
                    }
                }
            });
        }

        fab.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                logStatus("click fab");
                PopupWindow popUp = getPopupWindow();
//                popUp.showAsDropDown(v, 0, 0); // show popup like dropdown list
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    float screenWidth = getResources().getDisplayMetrics().widthPixels;
                    float screenHeight = getResources().getDisplayMetrics().heightPixels;
                    popUp.showAtLocation(v, Gravity.NO_GRAVITY, (int)screenWidth/2, (int)screenHeight/2);
                } else {
                    popUp.showAsDropDown(v);
                }
            }
        });
        fab.hide();


        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

        if(CameraPermissionHelper.hasCameraPermission(this)){
            setupRenderer();
        }

        installRequested = false;
    }

    private void setupRenderer(){
        if(surfaceView != null){
            return;
        }
        surfaceView = findViewById(R.id.surfaceview);

        // Set up tap listener.
        gestureDetector = new GestureDetector(this, gestureDetectorListener);
        surfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }
        });
        glSerfaceRenderer = new GLSurfaceRenderer(this);

        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        surfaceView.setRenderer(glSerfaceRenderer);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);



    }


    @Override
    protected void onResume() {
        super.onResume();

        acc = sen.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sen.registerListener(this, acc, SensorManager.SENSOR_DELAY_NORMAL);

        mag = sen.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sen.registerListener(this, mag, SensorManager.SENSOR_DELAY_NORMAL);

        gyro = sen.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sen.registerListener(this, gyro, SensorManager.SENSOR_DELAY_NORMAL);


        logStatus("onResume()");
        if (session == null) {
            Exception exception = null;
            String message = null;
            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }

                session = new Session(/* context= */ this);
            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
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
                showSnackbarMessage(message, true);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }

            // Create default config and check if supported.
            Config config = new Config(session);
            if (!session.isSupported(config)) {
                showSnackbarMessage("This device does not support AR", true);
            }
            session.configure(config);

            setupRenderer();
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }
        surfaceView.onResume();
        displayRotationHelper.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        sen.unregisterListener(this);
        logStatus("onPause()");
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }

//    @Override
//    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
//        logStatus("onRequestPermissionsResult()");
//        Toast.makeText(this, R.string.need_permission, Toast.LENGTH_LONG)
//                .show();
//        if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
//            // Permission denied with checking "Do not ask again".
//            CameraPermissionHelper.launchPermissionSettings(this);
//        }
//        finish();
//    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        logStatus("onWindowFocusChanged()");
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

    private void showSnackbarMessage(String message, boolean finishOnDismiss) {
        messageSnackbar =
                Snackbar.make(
                        ArMeasureActivity.this.findViewById(android.R.id.content),
                        message,
                        Snackbar.LENGTH_INDEFINITE);
        messageSnackbar.getView().setBackgroundColor(0xbf323232);
        if (finishOnDismiss) {
            messageSnackbar.setAction(
                    "Dismiss",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            messageSnackbar.dismiss();
                        }
                    });
            messageSnackbar.addCallback(
                    new BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        @Override
                        public void onDismissed(Snackbar transientBottomBar, int event) {
                            super.onDismissed(transientBottomBar, event);
                            finish();
                        }
                    });
        }
        messageSnackbar.show();
    }

    private void toast(int stringResId){
        Toast.makeText(this, stringResId, Toast.LENGTH_SHORT).show();
    }
    private boolean isVerticalMode = false;
    private PopupWindow popupWindow;
    private PopupWindow getPopupWindow() {

        // initialize a pop up window type
        popupWindow = new PopupWindow(this);

        ArrayList<String> sortList = new ArrayList<>();
        sortList.add(getString(R.string.action_1));
        sortList.add(getString(R.string.action_2));
        sortList.add(getString(R.string.action_3));
        sortList.add(getString(R.string.action_4));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
                sortList);
        // the drop down list is a list view
        ListView listViewSort = new ListView(this);
        // set our adapter and pass our pop up window contents
        listViewSort.setAdapter(adapter);
        listViewSort.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position){
                    case 3:// move vertical axis
                        toast(R.string.action_4_toast);
                        break;
                    case 0:// delete
                        toast(R.string.action_1_toast);
                        break;
                    case 1:// set as first
                        toast(R.string.action_2_toast);
                        break;
                    case 2:// move horizontal axis
                    default:
                        toast(R.string.action_3_toast);
                        break;
                }
                return true;
            }
        });
        // set on item selected
        listViewSort.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position){
                    case 3:// move vertical axis
                        isVerticalMode = true;
                        popupWindow.dismiss();
                        break;
                    case 0:// delete
                        glSerfaceRenderer.deleteNowSelection();
                        popupWindow.dismiss();
                        fab.hide();
                        break;
                    case 1:// set as first
                        glSerfaceRenderer.setNowSelectionAsFirst();
                        popupWindow.dismiss();
                        fab.hide();
                        break;
                    case 2:// move horizontal axis
                    default:
                        isVerticalMode = false;
                        popupWindow.dismiss();
                        break;
                }

            }
        });
        // some other visual settings for popup window
        popupWindow.setFocusable(true);
        popupWindow.setWidth((int)(getResources().getDisplayMetrics().widthPixels * 0.4f));
        // popupWindow.setBackgroundDrawable(getResources().getDrawable(R.drawable.white));
        popupWindow.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
        // set the listview as popup content
        popupWindow.setContentView(listViewSort);
        return popupWindow;
    }
    int orientation=-1;;
    private float x = 0, y = 0, z = 0;

    /*
        array of floats to hold readings of accelerometer and
        magnetic field sensor
     */
    private float[] accelReadings, magReadings;

    double gyroX;
    double gyroY;
    double gyroZ;

    @Override
    public void onSensorChanged(SensorEvent event) {



        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {

            gyroX = Math.toDegrees(event.values[0]);
            gyroY = Math.toDegrees(event.values[1]);
            gyroZ = Math.toDegrees(event.values[2]);

        }

        if(Math.abs(gyroX)< 0.5 &&  Math.abs(gyroY)< 0.5 && Math.abs(gyroZ)< 0.5 ){
            Globals.isStable = true;
            Log.d("Sensor Gyrox","PHONE IS STABLE");

        }

        else
            Globals.isStable = false;

        int sensorType = event.sensor.getType();

        // The sensorEvent object is reused across calls to onSensorChanged().
        // clone() gets a copy so the data doesn't change out from under us
        switch (sensorType) {
            case Sensor.TYPE_ACCELEROMETER:
                mAccelerometerData = event.values.clone();
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                mMagnetometerData = event.values.clone();
                break;
            default:
                return;
        }

        // Compute the rotation matrix: merges and translates the data
        // from the accelerometer and magnetometer, in the device coordinate
        // system, into a matrix in the world's coordinate system.
        //
        // The second argument is an inclination matrix, which isn't
        // used in this example.
        float[] rotationMatrix = new float[9];
        boolean rotationOK = SensorManager.getRotationMatrix(rotationMatrix,
                null, mAccelerometerData, mMagnetometerData);

        // Remap the matrix based on current device/activity rotation.
        float[] rotationMatrixAdjusted = new float[9];
        switch (mDisplay.getRotation()) {
            case Surface.ROTATION_0:
                rotationMatrixAdjusted = rotationMatrix.clone();
                break;
            case Surface.ROTATION_90:
                SensorManager.remapCoordinateSystem(rotationMatrix,
                        SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X,
                        rotationMatrixAdjusted);
                break;
            case Surface.ROTATION_180:
                SensorManager.remapCoordinateSystem(rotationMatrix,
                        SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y,
                        rotationMatrixAdjusted);
                break;
            case Surface.ROTATION_270:
                SensorManager.remapCoordinateSystem(rotationMatrix,
                        SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X,
                        rotationMatrixAdjusted);
                break;
        }

        // Get the orientation of the device (azimuth, pitch, roll) based
        // on the rotation matrix. Output units are radians.
        float orientationValues[] = new float[3];
        if (rotationOK) {
            SensorManager.getOrientation(rotationMatrixAdjusted,
                    orientationValues);
        }

        // Pull out the individual values from the array.
        float azimuth = orientationValues[0];
        float pitch = orientationValues[1];
        float roll = orientationValues[2];

        // Pitch and roll values that are close to but not 0 cause the
        // animation to flash a lot. Adjust pitch and roll to 0 for very
        // small values (as defined by VALUE_DRIFT).
        if (Math.abs(pitch) < VALUE_DRIFT) {
            pitch = 0;
        }
        if (Math.abs(roll) < VALUE_DRIFT) {
            roll = 0;
        }

        // Fill in the string placeholders and set the textview text.
//        mTextSensorAzimuth.setText( Double.toString(Math.toDegrees(azimuth)));

        double roundOffPitch = Math.round(Math.toDegrees(pitch) * 100.0) / 100.0;
        double roundOffRoll = Math.round(Math.toDegrees(roll) * 100.0) / 100.0;


        mTextSensorPitch.setText(Double.toString(roundOffPitch));
        mTextSensorRoll.setText(Double.toString(roundOffRoll));

        if(roundOffPitch == 0 && roundOffRoll == 0 && Globals.click == true && Globals.isStable){

            int[] coordinates = new int[2];
            surfaceView.getLocationOnScreen(coordinates);

// MotionEvent parameters
            long downTime = SystemClock.uptimeMillis();
            long eventTime = SystemClock.uptimeMillis();
            int action = MotionEvent.ACTION_DOWN;
            int y = surfaceView.getHeight()/2;
            int x = surfaceView.getWidth()/2;
            int metaState = 0;


// dispatch the event
            MotionEvent event1 = MotionEvent.obtain(downTime, eventTime, action, x, y, metaState);
            surfaceView.dispatchTouchEvent(event1);
            queuedSingleTaps.offer(event1);

            Globals.click = false;


        }
        // Set spot color (alpha/opacity) equal to pitch/roll.
        // this is not a precise grade (pitch/roll can be greater than 1)
        // but it's close enough for the animation effect.







//
//        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
//            if (event.values[1] < 6.5 && event.values[1] > -6.5) {
//                if (orientation != 1) {
//
//
//                    if (y == 1) {
//                        Log.d("Sensor", "Landscape");
//                        Globals.Rotation = "Landscape";
//
//                    }
//                }
//                orientation = 1;
//            } else {
//                if (orientation != 0) {
//
//                    if (y == 1) {
//                        Globals.Rotation = "Portrait";
//                        Log.d("Sensor", "Portrait");
//                    }
//                }
//                orientation = 0;
//            }
//            float z = event.values[2];
//        if (z >9 && z < 10)
//            Log.d("face", "CAMERA DOWN");
//        else if (z > -10 && z < -9)
//            Log.d("face", "CAMERA UP");
//
//
//        }

//        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
//            System.arraycopy(event.values, 0, mAccelerometerReading,
//                    0, mAccelerometerReading.length);
//        }
//        else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
//            System.arraycopy(event.values, 0, mMagnetometerReading,
//                    0, mMagnetometerReading.length);
//        }
//
//        updateOrientationAngles();
//

//        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
//            accelReadings = event.values;
//        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
//            magReadings = event.values;
//        if (magReadings != null && accelReadings != null) {
//            float R[] = new float[9];
//            float I[] = new float[9];
//            boolean success = SensorManager.getRotationMatrix(R, I, accelReadings, magReadings);
//            if (success) {
//                float orientation[] = new float[3];
//                SensorManager.getOrientation(R, orientation);
//                z = (float) Math.toDegrees(orientation[0]);
//                x = (float) Math.toDegrees(orientation[1]);
//                y = (float) Math.toDegrees(orientation[2]);
//
//                /*
//                    I want the angles to be shown with respect
//                    to the positive axis, so I need to do a little math here.
//
//                    If the angle is negative, I will take the absolute value of it
//                    and subtract it from 360. This way, it shows the equivalent angle
//                    measured
//                 */
//                if (x < 0) {
//                    x = 360 - Math.abs(x);
//                }
//
//                if (y < 0) {
//                    y = 360 - Math.abs(y);
//                }
//
//                if (z < 0) {
//                    z = 360 - Math.abs(z);
//                }
//
//                Log.d("Sensor angle", "X " + String.valueOf(x));
//                Log.d("Sensor angle", "Y " + String.valueOf(y));
//
//                Log.d("Sensor angle", "Z " + String.valueOf(z));
//
//////            Log.d("Sensor Gyroy", String.valueOf(event.values[0]));
//////            Log.d("Sensor Gyroz", String.valueOf(event.values[0]));
//            }
//        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private class GLSurfaceRenderer implements GLSurfaceView.Renderer{
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
        private float[] cameraInitailCoordinates = {0f, 0f, 0f};
        private float[] cameraFinalCoordinates = {0f, 0f, 0f};




        public GLSurfaceRenderer(Context context){
            this.context = context;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            logStatus("onSurfaceCreated()");
            GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

            // Create the texture and pass it to ARCore session to be filled during update().
            backgroundRenderer.createOnGlThread(context);
            if (session != null) {

                session.setCameraTextureName(backgroundRenderer.getTextureId());
            }

            // Prepare the other rendering objects.
            try {
                rectRenderer = new RectanglePolygonRenderer();
                cube.createOnGlThread(context, ASSET_NAME_CUBE_OBJ, ASSET_NAME_CUBE);
                cube.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f);
                cubeSelected.createOnGlThread(context, ASSET_NAME_CUBE_OBJ, ASSET_NAME_CUBE_SELECTED);
                cubeSelected.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f);
            } catch (IOException e) {
                log(TAG, "Failed to read obj file");
            }
            try {
                planeRenderer.createOnGlThread(context, "trigrid.png");
            } catch (IOException e) {
                log(TAG, "Failed to read plane texture");
            }
            pointCloud.createOnGlThread(context);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            if(width <= 0 || height <= 0){
                logStatus("onSurfaceChanged(), <= 0");
                return;
            }
            logStatus("onSurfaceChanged()");

            displayRotationHelper.onSurfaceChanged(width, height);
            GLES20.glViewport(0, 0, width, height);
            viewWidth = width;
            viewHeight = height;
            setNowTouchingPointIndex(DEFAULT_VALUE);
        }

        public void deleteNowSelection(){
            logStatus("deleteNowSelection()");
            int index = nowTouchingPointIndex;
            if (index > -1){
                if(index < anchors.size()) {
                    anchors.remove(index).detach();
                }
                if(index < showingTapPointX.size()) {
                    showingTapPointX.remove(index);
                }
                if(index < showingTapPointY.size()) {
                    showingTapPointY.remove(index);
                }
            }
            setNowTouchingPointIndex(DEFAULT_VALUE);
        }

        public void setNowSelectionAsFirst(){
            logStatus("setNowSelectionAsFirst()");
            int index = nowTouchingPointIndex;
            if (index > -1 && index < anchors.size()) {
                if(index < anchors.size()){
                    for(int i=0; i<index; i++){
                        anchors.add(anchors.remove(0));
                    }
                }
                if(index < showingTapPointX.size()){
                    for(int i=0; i<index; i++){
                        showingTapPointX.add(showingTapPointX.remove(0));
                    }
                }
                if(index < showingTapPointY.size()){
                    for(int i=0; i<index; i++){
                        showingTapPointY.add(showingTapPointY.remove(0));
                    }
                }
            }
            setNowTouchingPointIndex(DEFAULT_VALUE);
        }

        public int getNowTouchingPointIndex(){
            return nowTouchingPointIndex;
        }

        public void setNowTouchingPointIndex(int index){
            nowTouchingPointIndex = index;
            showCubeStatus();
        }

        double initialCameraX = 0.0;
        double initialCameraY = 0.0;
        double initialCameraZ = 0.0;

        int onlyOneStartReading = 0;

        double finalCameraX = 0.0;
        double finalCameraY = 0.0;
        double finalCameraZ = 0.0;

        int onlyOneStopReading = 0;


        int one = 0;
        @Override
        public void onDrawFrame(GL10 gl) {
//            log(TAG, "onDrawFrame(), mTouches.size=" + mTouches.size());
            // Clear screen to notify driver it should not load any pixels from previous frame.
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            if(viewWidth == 0 || viewWidth == 0){
                return;
            }
            if (session == null) {
                return;
            }
            // Notify ARCore session that the view size changed so that the perspective matrix and
            // the video background can be properly adjusted.
            displayRotationHelper.updateSessionIfNeeded(session);

            try {
                session.setCameraTextureName(backgroundRenderer.getTextureId());

                // Obtain the current frame from ARSession. When the configuration is set to
                // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
                // camera framerate.
                Frame frame = session.update();
                Camera camera = frame.getCamera();
                // Draw background.

                backgroundRenderer.draw(frame);

                // If not tracking, don't draw 3d objects.
                if (camera.getTrackingState() == TrackingState.PAUSED ) {
                    return;
                }

//
//                if(notRecording){
//
//                }


                Log.d("CAMERA POSE: " , String.valueOf(camera.getPose().tx()));

                if(Globals.recording && onlyOneStartReading<1){

                    initialCameraX = camera.getPose().tx();
                    initialCameraY = camera.getPose().ty();
                    initialCameraZ = camera.getPose().tz();
                    onlyOneStartReading++;
                    Log.d("CAMERA POSE: ", "here1");
                }


                if(notRecording && onlyOneStartReading == 1 && onlyOneStopReading<1){

                    finalCameraX = camera.getPose().tx();
                    finalCameraY = camera.getPose().ty();
                    finalCameraZ = camera.getPose().tz();
                    onlyOneStopReading++;
                    Log.d("CAMERA POSE: ", "here2");

                }


                if(onlyOneStartReading == 1 && onlyOneStopReading ==1 && one <1){
                    double distance = Math.sqrt(Math.pow((initialCameraX-finalCameraX), 2) +
                            Math.pow((initialCameraY-finalCameraY), 2) +
                            Math.pow((initialCameraZ-finalCameraZ), 2));

                    autoCalculatedDistance = Math.round(distance * 100.0) / 100.0;

                    tv_result2.setText("Auto " +Double.toString( autoCalculatedDistance *100 ) );
                    Log.d("CAMERA POSE: ", "here3");
                    one++;

                }




                // Get projection matrix.
                camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

                // Get camera matrix and draw.
                camera.getViewMatrix(viewmtx, 0);

                // Compute lighting from average intensity of the image.
                final float lightIntensity = frame.getLightEstimate().getPixelIntensity();

                // Visualize tracked points.
                PointCloud pointCloud = frame.acquirePointCloud();
                ArMeasureActivity.this.pointCloud.update(pointCloud);


                // if(!stopPoints )
                ArMeasureActivity.this.pointCloud.draw(viewmtx, projmtx);

                // Application is responsible for releasing the point cloud resources after
                // using it.
                pointCloud.release();

                // Visualize planes.
                planeRenderer.drawPlanes(
                        session.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projmtx);

                // draw cube & line from last frame
                if(anchors.size() < 1){
                    // no point
                    showResult("");
                }else{
                    // draw selected cube
                    if(nowTouchingPointIndex != DEFAULT_VALUE) {
                        drawObj(getPose(anchors.get(nowTouchingPointIndex)), cubeSelected, viewmtx, projmtx, lightIntensity);
                        checkIfHit(cubeSelected, nowTouchingPointIndex);
                    }
                    StringBuilder sb = new StringBuilder();
                    double total = 0;
                    Pose point1;
                    // draw first cube
                    Pose point0 = getPose(anchors.get(0));
                    drawObj(point0, cube, viewmtx, projmtx, lightIntensity);
                    checkIfHit(cube, 0);
                    // draw the rest cube
                    for(int i = 1; i < anchors.size(); i++){
                        point1 = getPose(anchors.get(i));
                        log("onDrawFrame()", "before drawObj()");
                        drawObj(point1, cube, viewmtx, projmtx, lightIntensity);
                        checkIfHit(cube, i);
                        log("onDrawFrame()", "before drawLine()");
                        drawLine(point0, point1, viewmtx, projmtx);

                        float distanceCm = ((int)(getDistance(point0, point1) * 1000))/10.0f;
                        total += distanceCm;
                        sb.append(" + ").append(distanceCm);

                        point0 = point1;
                    }

                    // show result
                    String result = sb.toString().replaceFirst("[+]", "Dot ") + " = " + (((int)(total * 10f))/10f) + "cm";
                    Globals.results =result;

                    if(x%2 == 0)
                        showResult(result);
                }

                // check if there is any touch event
                MotionEvent tap = queuedSingleTaps.poll();
                if(tap != null && camera.getTrackingState() == TrackingState.TRACKING){
                    for (HitResult hit : frame.hitTest(tap)) {
                        // Check if any plane was hit, and if it was hit inside the plane polygon.j
                        Trackable trackable = hit.getTrackable();
                        // Creates an anchor if a plane or an oriented point was hit.
                        if (trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
                                || (trackable instanceof Point
                                && ((Point) trackable).getOrientationMode()
                                == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)) {
                            // Cap the number of objects created. This avoids overloading both the
                            // rendering system and ARCore.
                            if (anchors.size() >= 16) {
                                anchors.get(0).detach();
                                anchors.remove(0);

                                showingTapPointX.remove(0);
                                showingTapPointY.remove(0);
                            }

                            // Adding an Anchor tells ARCore that it should track this position in
                            // space. This anchor will be used in PlaneAttachment to place the 3d model
                            // in the correct position relative both to the world and to the plane.
                            anchors.add(hit.createAnchor());

                            showingTapPointX.add(tap.getX());
                            showingTapPointY.add(tap.getY());
                            nowTouchingPointIndex = anchors.size() - 1;

                            showMoreAction();
                            showCubeStatus();
                            break;
                        }
                    }
                }else{
                    handleMoveEvent(nowTouchingPointIndex);
                }
            } catch (Throwable t) {
                // Avoid crashing the application due to unhandled exceptions.
                Log.e(TAG, "Exception on the OpenGL thread", t);
            }
        }

        private void handleMoveEvent(int nowSelectedIndex){
            try {
                if (showingTapPointX.size() < 1 || queuedScrollDx.size() < 2) {
                    // no action, don't move
                    return;
                }
                if (nowTouchingPointIndex == DEFAULT_VALUE) {
                    // no selected cube, don't move
                    return;
                }
                if (nowSelectedIndex >= showingTapPointX.size()) {
                    // wrong index, don't move.
                    return;
                }
                float scrollDx = 0;
                float scrollDy = 0;
                int scrollQueueSize = queuedScrollDx.size();
                for (int i = 0; i < scrollQueueSize; i++) {
                    scrollDx += queuedScrollDx.poll();
                    scrollDy += queuedScrollDy.poll();
                }

                if (isVerticalMode) {
                    Anchor anchor = anchors.remove(nowSelectedIndex);
                    anchor.detach();
                    setPoseDataToTempArray(getPose(anchor));
//                        log(TAG, "point[" + nowSelectedIndex + "] move vertical "+ (scrollDy / viewHeight) + ", tY=" + tempTranslation[1]
//                                + ", new tY=" + (tempTranslation[1] += (scrollDy / viewHeight)));
                    tempTranslation[1] += (scrollDy / viewHeight);
                    anchors.add(nowSelectedIndex,
                            session.createAnchor(new Pose(tempTranslation, tempRotation)));
                } else {
                    float toX = showingTapPointX.get(nowSelectedIndex) - scrollDx;
                    showingTapPointX.remove(nowSelectedIndex);
                    showingTapPointX.add(nowSelectedIndex, toX);

                    float toY = showingTapPointY.get(nowSelectedIndex) - scrollDy;
                    showingTapPointY.remove(nowSelectedIndex);
                    showingTapPointY.add(nowSelectedIndex, toY);

                    if (anchors.size() > nowSelectedIndex) {
                        Anchor anchor = anchors.remove(nowSelectedIndex);
                        anchor.detach();
                        // remove duplicated anchor
                        setPoseDataToTempArray(getPose(anchor));
                        tempTranslation[0] -= (scrollDx / viewWidth);
                        tempTranslation[2] -= (scrollDy / viewHeight);
                        anchors.add(nowSelectedIndex,
                                session.createAnchor(new Pose(tempTranslation, tempRotation)));
                    }
                }
            } catch (NotTrackingException e) {
                e.printStackTrace();
            }
        }

        private final float[] mPoseTranslation = new float[3];
        private final float[] mPoseRotation = new float[4];
        private Pose getPose(Anchor anchor){
            Pose pose = anchor.getPose();
            pose.getTranslation(mPoseTranslation, 0);
            pose.getRotationQuaternion(mPoseRotation, 0);
            return new Pose(mPoseTranslation, mPoseRotation);
        }
        private void setPoseDataToTempArray(Pose pose){
            pose.getTranslation(tempTranslation, 0);
            pose.getRotationQuaternion(tempRotation, 0);

        }

        private void drawLine(Pose pose0, Pose pose1, float[] viewmtx, float[] projmtx){
            float lineWidth = 0.002f;
            float lineWidthH = lineWidth / viewHeight * viewWidth;
            rectRenderer.setVerts(
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

            rectRenderer.draw(viewmtx, projmtx);
        }

        private void drawObj(Pose pose, ObjectRenderer renderer, float[] cameraView, float[] cameraPerspective, float lightIntensity){
            pose.toMatrix(anchorMatrix, 0);
            renderer.updateModelMatrix(anchorMatrix, 1);
            renderer.draw(cameraView, cameraPerspective, lightIntensity);
        }

        private void checkIfHit(ObjectRenderer renderer, int cubeIndex){
            if(isMVPMatrixHitMotionEvent(renderer.getModelViewProjectionMatrix(), queuedLongPress.peek())){
                // long press hit a cube, show context menu for the cube
                nowTouchingPointIndex = cubeIndex;
                queuedLongPress.poll();
                showMoreAction();
                showCubeStatus();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //fab.performClick();
                    }
                });
            }else if(isMVPMatrixHitMotionEvent(renderer.getModelViewProjectionMatrix(), queuedSingleTaps.peek())){
                nowTouchingPointIndex = cubeIndex;
                queuedSingleTaps.poll();
                showMoreAction();
                showCubeStatus();
            }
        }

        private boolean isMVPMatrixHitMotionEvent(float[] ModelViewProjectionMatrix, MotionEvent event){
            if(event == null){
                return false;
            }
            Matrix.multiplyMV(vertexResult, 0, ModelViewProjectionMatrix, 0, centerVertexOfCube, 0);
            /**
             * vertexResult = [x, y, z, w]
             *
             * coordinates in View
             * ┌─────────────────────────────────────────┐╮
             * │[0, 0]                     [viewWidth, 0]│
             * │       [viewWidth/2, viewHeight/2]       │view height
             * │[0, viewHeight]   [viewWidth, viewHeight]│
             * └─────────────────────────────────────────┘╯
             * ╰                view width               ╯
             *
             * coordinates in GLSurfaceView frame
             * ┌─────────────────────────────────────────┐╮
             * │[-1.0,  1.0]                  [1.0,  1.0]│
             * │                 [0, 0]                  │view height
             * │[-1.0, -1.0]                  [1.0, -1.0]│
             * └─────────────────────────────────────────┘╯
             * ╰                view width               ╯
             */
            // circle hit test
            float radius = (viewWidth / 2) * (cubeHitAreaRadius/vertexResult[3]);
            float dx = event.getX() - (viewWidth / 2) * (1 + vertexResult[0]/vertexResult[3]);
            float dy = event.getY() - (viewHeight / 2) * (1 - vertexResult[1]/vertexResult[3]);
            double distance = Math.sqrt(dx * dx + dy * dy);
//            // for debug
//            overlayViewForTest.setPoint("cubeCenter", screenX, screenY);
//            overlayViewForTest.postInvalidate();
            return distance < radius;
        }

        private double getDistance(Pose pose0, Pose pose1){
            float dx = pose0.tx() - pose1.tx();
            float dy = pose0.ty() - pose1.ty();
            float dz = pose0.tz() - pose1.tz();
            return Math.sqrt(dx * dx + dz * dz + dy * dy);
        }

        private void showResult(final String result){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tv_result.setText(result);
                }

            });
        }

        private void showMoreAction(){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //  fab.show();
                }
            });
        }

        private void hideMoreAction(){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    fab.hide();
                }
            });
        }

        private void showCubeStatus(){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    int nowSelectIndex = glSerfaceRenderer.getNowTouchingPointIndex();
                    for(int i = 0; i<ivCubeIconList.length && i< anchors.size(); i++){
                        ivCubeIconList[i].setEnabled(true);
                        ivCubeIconList[i].setActivated(i == nowSelectIndex);
                    }
                    for(int i = anchors.size(); i<ivCubeIconList.length; i++){
                        ivCubeIconList[i].setEnabled(false);
                    }
                }
            });
        }

    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE) {
            Log.e(TAG, "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode != RESULT_OK) {
            Toast.makeText(this,
                    "Screen Cast Permission Denied", Toast.LENGTH_SHORT).show();
            mToggleButton.setChecked(false);
            return;
        }
        mMediaProjectionCallback = new MediaProjectionCallback();
        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
        mMediaProjection.registerCallback(mMediaProjectionCallback, null);
        mVirtualDisplay = createVirtualDisplay();

        if (y == 1) {
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    mMediaRecorder.start();
                }
            }, 1000);   //5 s
        }
    }

    public void onToggleScreenShare(View view) {
        if (((ToggleButton) view).isChecked()) {
            initRecorder();
            shareScreen();
        } else {
            y++;
            mTextSensorPitch.setVisibility(View.INVISIBLE);
            mMediaRecorder.stop();
            mMediaRecorder.reset();
            Log.v(TAG, "Stopping Recording");
            stopScreenSharing();
        }
    }

    private void shareScreen() {
        if (mMediaProjection == null) {
            startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
            return;
        }
        mVirtualDisplay = createVirtualDisplay();
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                mMediaRecorder.start();
            }
        }, 1000);     }

    private VirtualDisplay createVirtualDisplay() {
        return mMediaProjection.createVirtualDisplay("MainActivity",
                DISPLAY_WIDTH, DISPLAY_HEIGHT, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mMediaRecorder.getSurface(), null /*Callbacks*/, null
                /*Handler*/);
    }

    private void initRecorder() {
        String time = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        try {
            // mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);

            Log.d("WIDTH",         Double.toString(surfaceView.getHeight()));
            Log.d("WIDTH",         Double.toString(surfaceView.getWidth()));
            DISPLAY_WIDTH = surfaceView.getWidth();
            DISPLAY_HEIGHT = surfaceView.getHeight();

//            DISPLAY_WIDTH = 1080;
//            DISPLAY_HEIGHT = 1920;
//


            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mMediaRecorder.setOutputFile(Environment
                    .getExternalStoragePublicDirectory(Environment
                            .DIRECTORY_DOWNLOADS) + "/video_" + time +".mp4");
            mMediaRecorder.setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            // mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mMediaRecorder.setVideoEncodingBitRate(15000000);
            mMediaRecorder.setVideoFrameRate(30);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int orientation = ORIENTATIONS.get(rotation + 90);
            mMediaRecorder.setOrientationHint(orientation);






            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            if (mToggleButton.isChecked()) {
                mToggleButton.setChecked(false);
                mMediaRecorder.stop();
                mMediaRecorder.reset();
                Log.v(TAG, "Recording Stopped");
                y++;
                mTextSensorPitch.setVisibility(View.VISIBLE);


            }
            mMediaProjection = null;
            stopScreenSharing();
        }
    }

    private void stopScreenSharing() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        //mMediaRecorder.release(); //If used: mMediaRecorder object cannot
        // be reused again
        destroyMediaProjection();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destroyMediaProjection();
    }

    private void destroyMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.unregisterCallback(mMediaProjectionCallback);
            mMediaProjection.stop();
            mMediaProjection = null;
            y++;
            mTextSensorPitch.setVisibility(View.VISIBLE);


        }
        Log.i(TAG, "MediaProjection Stopped");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSIONS: {
                if ((grantResults.length > 0) && (grantResults[0] +
                        grantResults[1]) == PackageManager.PERMISSION_GRANTED) {
                    onToggleScreenShare(mToggleButton);
                } else {
                    mToggleButton.setChecked(false);
                    Snackbar.make(findViewById(android.R.id.content), R.string.need_permission,
                            Snackbar.LENGTH_INDEFINITE).setAction("ENABLE",
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    Intent intent = new Intent();
                                    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    intent.addCategory(Intent.CATEGORY_DEFAULT);
                                    intent.setData(Uri.parse("package:" + getPackageName()));
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                                    startActivity(intent);
                                }
                            }).show();
                }
                return;
            }
        }
    }

    public void updateOrientationAngles() {
        // Update rotation matrix, which is needed to update orientation angles.

        sen.getRotationMatrix(mRotationMatrix, null,
                mAccelerometerReading, mMagnetometerReading);

        // "mRotationMatrix" now has up-to-date information.

        sen.getOrientation(mRotationMatrix, mOrientationAngles);

        // "mOrientationAngles" now has up-to-date information.

        for(int i =0; i<mOrientationAngles.length; i++ )
            Log.d("ANGLE", "ANGLEX:" +Math.toDegrees(mOrientationAngles [0]) );
        Log.d("ANGLE", "ANGLEY:" +Math.toDegrees(mOrientationAngles [0]) );

        Log.d("ANGLE", "ANGLEZ:" +Math.toDegrees(mOrientationAngles [0]) );

    }


}

class Globals {
    public static String results = "";
    public static int x = 0;
    public static int y = 0;
    public static String Rotation = "";
    public static boolean isStable = false;
    public static boolean click = false;
    public static boolean stopPoints = false;
    public static boolean notRecording = true;
    public static boolean recording = false;

    public static int rec = 0;

    public static double autoCalculatedDistance = 0;



    ///
}
