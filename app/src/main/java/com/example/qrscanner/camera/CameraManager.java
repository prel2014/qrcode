/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.qrscanner.camera;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;

import com.example.qrscanner.camera.open.OpenCameraManager;
import com.google.zxing.PlanarYUVLuminanceSource;

import java.io.IOException;

/**
 * This object wraps the Camera service object and expects to be the only one talking to it. The
 * implementation encapsulates the steps needed to take preview-sized images, which are used for
 * both preview and decoding.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class CameraManager {

  private static final String TAG = CameraManager.class.getSimpleName();

  private static final int MIN_FRAME_WIDTH = 240;
  private static final int MIN_FRAME_HEIGHT = 240;
  private static final int MAX_FRAME_WIDTH = 2000; // = 1920/2
  private static final int MAX_FRAME_HEIGHT = 2000; // = 1080/2

  private final Context context;
  private final View view;
  private final CameraConfigurationManager configManager;
  private Camera camera;
  private AutoFocusManager autoFocusManager;
  private Rect framingRect;
  private Rect framingRectInPreview;
  private boolean initialized;
  private boolean previewing;
  private int requestedFramingRectWidth = 0;
  private int requestedFramingRectHeight = 0;
  private int requestedFramingRectLeftOffset = 0;
  private int requestedFramingRectTopOffset = 0;

  private final PreviewCallback previewCallback;

  public CameraManager(Context context, View view) {
    this.context = context;
    this.view = view;
    this.configManager = new CameraConfigurationManager(context, view);
    previewCallback = new PreviewCallback(configManager);
  }

  public void setFramingRectCoords(int width, int height, int leftOffset, int topOffset) {
    requestedFramingRectWidth = width;
    requestedFramingRectHeight = height;
    requestedFramingRectLeftOffset = leftOffset;
    requestedFramingRectTopOffset = topOffset;
  }

  /**
   * Opens the camera driver and initializes the hardware parameters.
   *
   * @param holder The surface object which the camera will draw preview frames into.
   * @throws IOException Indicates the camera driver failed to open.
   */
  public synchronized void openDriver(SurfaceHolder holder) throws IOException {
    Camera theCamera = camera;
    if (theCamera == null) {
      theCamera = new OpenCameraManager().build().open();
      if (theCamera == null) {
        throw new IOException();
      }
      camera = theCamera;
    }
    theCamera.setPreviewDisplay(holder);

    if (!initialized) {
      initialized = true;
      configManager.initFromCameraParameters(theCamera);
      if (requestedFramingRectWidth > 0 && requestedFramingRectHeight > 0) {
        if (requestedFramingRectLeftOffset > 0 && requestedFramingRectTopOffset > 0) {
          setManualFramingRect(requestedFramingRectWidth, requestedFramingRectHeight, requestedFramingRectLeftOffset, requestedFramingRectTopOffset);
        } else {
          setManualFramingRect(requestedFramingRectWidth, requestedFramingRectHeight);
        }
        requestedFramingRectWidth = 0;
        requestedFramingRectHeight = 0;
        requestedFramingRectLeftOffset = 0;
        requestedFramingRectTopOffset = 0;
      }
    }

    Camera.Parameters parameters = theCamera.getParameters();
    String parametersFlattened = parameters == null ? null : parameters.flatten(); // Save these, temporarily
    try {
      configManager.setDesiredCameraParameters(theCamera, false);
    } catch (RuntimeException re) {
      // Driver failed
      Log.w(TAG, "Camera rejected parameters. Setting only minimal safe-mode parameters");
      Log.i(TAG, "Resetting to saved camera params: " + parametersFlattened);
      // Reset:
      if (parametersFlattened != null) {
        parameters = theCamera.getParameters();
        parameters.unflatten(parametersFlattened);
        try {
          theCamera.setParameters(parameters);
          configManager.setDesiredCameraParameters(theCamera, true);
        } catch (RuntimeException re2) {
          // Well, darn. Give up
          Log.w(TAG, "Camera rejected even safe-mode parameters! No configuration");
        }
      }
    }

  }

  public synchronized boolean isOpen() {
    return camera != null;
  }

  /**
   * Closes the camera driver if still in use.
   */
  public synchronized void closeDriver() {
    if (camera != null) {
      camera.release();
      camera = null;
      // Make sure to clear these each time we close the camera, so that any scanning rect
      // requested by intent is forgotten.
      framingRect = null;
      framingRectInPreview = null;
    }
  }

  /**
   * Asks the camera hardware to begin drawing preview frames to the screen.
   */
  public synchronized void startPreview() {
    Camera theCamera = camera;
    if (theCamera != null && !previewing) {
      theCamera.startPreview();
      previewing = true;
      autoFocusManager = new AutoFocusManager(context, camera);
    }
  }

  /**
   * Tells the camera to stop drawing preview frames.
   */
  public synchronized void stopPreview() {
    if (autoFocusManager != null) {
      autoFocusManager.stop();
      autoFocusManager = null;
    }
    if (camera != null && previewing) {
      camera.stopPreview();
      previewCallback.setHandler(null, 0);
      previewing = false;
    }
  }


  public synchronized void setTorch(boolean newSetting) {
    if (newSetting != configManager.getTorchState(camera)) {
      if (camera != null) {
        if (autoFocusManager != null) {
          autoFocusManager.stop();
        }
        configManager.setTorch(camera, newSetting);
        if (autoFocusManager != null) {
          autoFocusManager.start();
        }
      }
    }
  }

  /**
   * A single preview frame will be returned to the handler supplied. The data will arrive as byte[]
   * in the message.obj field, with width and height encoded as message.arg1 and message.arg2,
   * respectively.
   *
   * @param handler The handler to send the message to.
   * @param message The what field of the message to be sent.
   */
  public synchronized void requestPreviewFrame(Handler handler, int message) {
    Camera theCamera = camera;
    if (theCamera != null && previewing) {
      previewCallback.setHandler(handler, message);
      theCamera.setOneShotPreviewCallback(previewCallback);
    }
  }

  /**
   * Calculates the framing rect which the UI should draw to show the user where to place the
   * barcode. This target helps with alignment as well as forces the user to hold the device
   * far enough away to ensure the image will be in focus.
   *
   * @return The rectangle to draw on screen in window coordinates.
   */
  public synchronized Rect getFramingRect() {
    if (framingRect == null) {
      if (camera == null) {
        return null;
      }
//      Point screenResolution = configManager.getScreenResolution();
      Point screenResolution = new Point(view.getWidth(), view.getHeight());
      if (screenResolution == null) {
        // Called early, before init even finished
        return null;
      }

      int width = findDesiredDimensionInRange(screenResolution.x, MIN_FRAME_WIDTH, MAX_FRAME_WIDTH);
      int height = findDesiredDimensionInRange(screenResolution.y, MIN_FRAME_HEIGHT, MAX_FRAME_HEIGHT);

      int leftOffset = (screenResolution.x - width) / 2;
      int topOffset = (screenResolution.y - height) / 2;

      framingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
      Log.d(TAG, "Calculated framing rect: " + framingRect);
    }

    return framingRect;
  }
  private static int findDesiredDimensionInRange(int resolution, int hardMin, int hardMax) {
    int dim = resolution * 3 / 4; // Target 75% of each dimension
    if (dim < hardMin) {
      return resolution < hardMin ? resolution : hardMin;
    }
    if (dim > hardMax) {
      return hardMax;
    }
    return dim;
  }
  public synchronized Rect getFramingRectInPreview() {
    if (framingRectInPreview == null) {
      Rect framingRect = getFramingRect();
      if (framingRect == null) {
        return null;
      }
      Rect rect = new Rect(framingRect);
      Point cameraResolution = configManager.getCameraResolution();
//      Point screenResolution = configManager.getScreenResolution();
      Point screenResolution = configManager.getViewResolution();
      if (cameraResolution == null || screenResolution == null) {
        // Called early, before init even finished
        return null;
      }

      if (rect.width() > rect.height()) {
        rect.left = rect.left * cameraResolution.y / screenResolution.x;
        rect.right = rect.right * cameraResolution.y / screenResolution.x;
        rect.top = rect.top * cameraResolution.x / screenResolution.y;
        rect.bottom = rect.bottom * cameraResolution.x / screenResolution.y;
      } else {
        rect.left = rect.left * cameraResolution.x / screenResolution.x;
        rect.right = rect.right * cameraResolution.x / screenResolution.x;
        rect.top = rect.top * cameraResolution.y / screenResolution.y;
        rect.bottom = rect.bottom * cameraResolution.y / screenResolution.y;
      }
      framingRectInPreview = rect;
    }

    return framingRectInPreview;
  }
  public synchronized void setManualFramingRect(int width, int height, int leftOffset, int topOffset) {
    if (initialized) {
      Point screenResolution = configManager.getScreenResolution();
      if (width > screenResolution.x) {
        width = screenResolution.x;
      }
      if (height > screenResolution.y) {
        height = screenResolution.y;
      }
      framingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
      Log.d(TAG, "Calculated manual framing rect: " + framingRect);
      framingRectInPreview = null;
    } else {
      requestedFramingRectWidth = width;
      requestedFramingRectHeight = height;
      requestedFramingRectLeftOffset = leftOffset;
      requestedFramingRectTopOffset = topOffset;
    }
  }

  public synchronized void setManualFramingRect(Rect rect) {
    setManualFramingRect(rect.width(), rect.height(), rect.left, rect.top);
  }

  public synchronized void setManualFramingRect(int width, int height) {
    Point screenResolution = configManager.getScreenResolution();
    setManualFramingRect(width, height, (screenResolution.x - width) / 2, (screenResolution.y - height) / 2);
  }

  public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height) {
    Rect rect = getFramingRectInPreview();
    if (rect == null) {
      return null;
    }
    if (view.getWidth() < view.getHeight()) {
        Log.d(TAG, "rotating: width="+height+" height="+width);
        byte[] rotatedData = new byte[data.length];
    	for (int y = 0; y < height; y++) {
    		for (int x = 0; x < width; x++)
    			rotatedData[x * height + height - y - 1] = data[x + y * width];
        }
        return new PlanarYUVLuminanceSource(rotatedData, height, width, rect.top, rect.left, rect.height(), rect.width(), false);
    } else {
      return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top, rect.width(), rect.height(), false);
    }
  }

}
