package com.example.qrscanner;

import android.os.Bundle;
import android.widget.Toast;

import com.google.zxing.Result;

public class SampleStretchedFragment extends BarCodeScannerFragment {

  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    this.setmCallBack(new IResultCallback() {
      @Override
      public void result(Result lastResult) {
        Toast.makeText(getViewfinderView().getContext(), "Scan: " + lastResult.toString(), Toast.LENGTH_SHORT).show();
      }
    });
    //cameraManager.setManualFramingRect(450, 349, 100, 50);
  }

  public SampleStretchedFragment() {

  }
}