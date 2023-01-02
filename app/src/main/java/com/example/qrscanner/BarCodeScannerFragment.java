package com.example.qrscanner;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import androidx.fragment.app.Fragment;
import com.example.qrscanner.camera.CameraManager;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BarCodeScannerFragment extends Fragment implements SurfaceHolder.Callback, IConstants {

    private static final String TAG = BarCodeScannerFragment.class.getSimpleName();
    Set<String> codigos;
    private static final long DEFAULT_INTENT_RESULT_DURATION_MS = 1500L;
    private static final long BULK_MODE_SCAN_DELAY_MS = 1000L;
    public static final int HISTORY_REQUEST_CODE = 0x0000bacc;
    private CameraManager cameraManager;
    private BarCodeScannerHandler handler;
    private Result savedResultToShow;
    private ViewfinderView viewfinderView;
    private Result lastResult;
    private boolean hasSurface;
    private Collection<BarcodeFormat> decodeFormats;
    private Map<DecodeHintType, ?> decodeHints;
    private String characterSet;
    private InactivityTimer inactivityTimer;
    private BeepManager beepManager;
    private AmbientLightManager ambientLightManager;
    private IResultCallback mCallBack;
    private Rect customFramingRect;

    ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    public Handler getHandler() {
        return handler;
    }

    CameraManager getCameraManager() {
        return cameraManager;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        codigos=new HashSet<String>();
        hasSurface = false;
        inactivityTimer = new InactivityTimer(this.getActivity());
        beepManager = new BeepManager(this.getActivity());
        ambientLightManager = new AmbientLightManager(this.getActivity());

        PreferenceManager.setDefaultValues(this.getActivity(), R.xml.preferences, false);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstance) {
        View view = inflater.inflate(R.layout.capture, container, false);

        Window window = getActivity().getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        startScan();
    }

    @Override
    public void onPause() {
        stopScan();

        super.onPause();
    }
    public void startScan() {
        if (cameraManager != null) {
            Log.e(TAG, "startScan: scan already started.");
            return;
        }

        cameraManager = new CameraManager(this.getActivity().getApplication(), getView());

        viewfinderView = (ViewfinderView) getView().findViewById(R.id.viewfinder_view);
        viewfinderView.setCameraManager(cameraManager);

        handler = null;
        lastResult = null;

        SurfaceView surfaceView = (SurfaceView) getView().findViewById(R.id.preview_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            initCamera(surfaceHolder);
        } else {
            surfaceHolder.addCallback(this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        if (customFramingRect!=null) {
          cameraManager.setManualFramingRect(customFramingRect);
        }

        beepManager.updatePrefs();
        ambientLightManager.start(cameraManager);

        inactivityTimer.onResume();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

        decodeFormats = null;
        characterSet = null;
    }

    public void stopScan() {
        if (cameraManager == null) {
            Log.e(TAG, "stopScan: scan already stopped");
            return;
        }

        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        inactivityTimer.onPause();
        ambientLightManager.stop();
        cameraManager.closeDriver();
        cameraManager = null;

        if (!hasSurface) {
            SurfaceView surfaceView = (SurfaceView) getView().findViewById(R.id.preview_view);
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.removeCallback(this);
        }
    }

    public void setTorch(boolean state) {
      if (cameraManager!=null) {
        cameraManager.setTorch(state);
      }
    }

    public void setTorchOn() {
      setTorch(true);
    }

    public void setTorchOff() {
      setTorch(false);
    }

    @Override
    public void onDestroy() {
        inactivityTimer.shutdown();
        super.onDestroy();
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_FOCUS:
            case KeyEvent.KEYCODE_CAMERA:
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                setTorchOff();
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
                setTorchOn();
                return true;
        }
        return false; //super.onKeyDown(keyCode, event);
    }

    private void decodeOrStoreSavedBitmap(Bitmap bitmap, Result result) {
        // Bitmap isn't used yet -- will be used soon
        if (handler == null) {
            savedResultToShow = result;
        } else {
            if (result != null) {
                savedResultToShow = result;
            }
            if (savedResultToShow != null) {
                Message message = Message.obtain(handler, DECODE_SUCCEDED, savedResultToShow);
                handler.sendMessage(message);
            }
            savedResultToShow = null;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (holder == null) {
            Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!");
        }
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    public void setmCallBack(IResultCallback mCallBack) {
        this.mCallBack = mCallBack;
    }

    public void setFramingRect(int width, int height, int left, int top) {
      setFramingRect(new Rect(left, top, left + width, top + height));
    }

    public void setFramingRect(Rect rect) {
      this.customFramingRect = rect;
      if (cameraManager!=null) {
        cameraManager.setManualFramingRect(rect);
      }
    }

    public interface IResultCallback {
        void result(Result lastResult);
    }

    public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor) {
        inactivityTimer.onActivity();
        lastResult = rawResult;

        beepManager.playBeepSoundAndVibrate();
        drawResultPoints(barcode, scaleFactor, rawResult);

        if (mCallBack != null) {
            mCallBack.result(rawResult);
        }

        restartPreviewAfterDelay(500L);
    }
    
    private boolean isPortrait() {
    	if (getView().getWidth() < getView().getHeight()) 
    		return true;
    	else
    		return false;
    }

    private void drawResultPoints(Bitmap barcode, float scaleFactor, Result rawResult) {
        ResultPoint[] points = rawResult.getResultPoints();
        if (points != null && points.length > 0 && barcode != null) {
            Canvas canvas = new Canvas(barcode);
            Paint paint = new Paint();
            paint.setColor(getResources().getColor(R.color.result_points));
            
            if (points.length == 2) {
                paint.setStrokeWidth(4.0f);
                drawLine(canvas, paint, points[0], points[1], scaleFactor);
            } else if (points.length == 4 &&
                    (rawResult.getBarcodeFormat() == BarcodeFormat.UPC_A ||
                            rawResult.getBarcodeFormat() == BarcodeFormat.EAN_13)) {
                // Hacky special case -- draw two lines, for the barcode and metadata
                drawLine(canvas, paint, points[0], points[1], scaleFactor);
                drawLine(canvas, paint, points[2], points[3], scaleFactor);
            } else {
                paint.setStrokeWidth(10.0f);
                for (ResultPoint point : points) {
                    canvas.drawPoint(scaleFactor * point.getX(), scaleFactor * point.getY(), paint);
                }
            }
            
            if (isPortrait()) {
            	Log.d(TAG, "rotating results canvas");
            	canvas.rotate(90);
            }
        }
    }

    private static void drawLine(Canvas canvas, Paint paint, ResultPoint a, ResultPoint b, float scaleFactor) {
        if (a != null && b != null) {
            canvas.drawLine(scaleFactor * a.getX(),
                    scaleFactor * a.getY(),
                    scaleFactor * b.getX(),
                    scaleFactor * b.getY(),
                    paint);
        }
    }

    private void sendReplyMessage(int id, Object arg, long delayMS) {
        Message message = Message.obtain(handler, id, arg);
        if (delayMS > 0L) {
            handler.sendMessageDelayed(message, delayMS);
        } else {
            handler.sendMessage(message);
        }
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        if (cameraManager.isOpen()) {
            Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?");
            return;
        }
        try {
            cameraManager.openDriver(surfaceHolder);
            // Creating the handler starts the preview, which can also throw a RuntimeException.
            if (handler == null) {
                handler = new BarCodeScannerHandler(this, decodeFormats, decodeHints, characterSet, cameraManager);
            }
            decodeOrStoreSavedBitmap(null, null);
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
        } catch (RuntimeException e) {
            // Barcode Scanner has seen crashes in the wild of this variety:
            // java.?lang.?RuntimeException: Fail to connect to camera service
            Log.w(TAG, "Unexpected error initializing camera", e);
        }

    }

    public void restartPreviewAfterDelay(long delayMS) {
        if (handler != null) {
            handler.sendEmptyMessageDelayed(RESTART_PREVIEW, delayMS);
        }
    }

    public void drawViewfinder() {
        viewfinderView.drawViewfinder();
    }
}
