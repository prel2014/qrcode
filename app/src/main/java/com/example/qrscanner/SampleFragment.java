package com.example.qrscanner;

import android.os.Bundle;
import android.widget.Toast;
import com.google.zxing.Result;

public class SampleFragment extends BarCodeScannerFragment {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setmCallBack(new IResultCallback() {
            @Override
            public void result(Result lastResult) {
                codigos.add(lastResult.toString());
                Toast.makeText(getViewfinderView().getContext(), "Scan: " + lastResult.toString(), Toast.LENGTH_SHORT).show();
                stopScan();
            }
        });
    }

    public SampleFragment() {

    }
}