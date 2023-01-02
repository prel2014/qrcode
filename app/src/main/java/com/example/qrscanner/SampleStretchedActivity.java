package com.example.qrscanner;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

public class SampleStretchedActivity extends FragmentActivity {
  boolean torchState = false;

  LinearLayout layoutContent;
  Button mToggleButton;
  BarCodeScannerFragment mScannerFragment;

  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(com.example.qrscanner.R.layout.activity_stretched_sample);

    FragmentManager fm = getSupportFragmentManager();
    mScannerFragment = (BarCodeScannerFragment) fm.findFragmentById(R.id.scanner_fragment);

    layoutContent = (LinearLayout) findViewById(R.id.layout_content);

    final ViewTreeObserver observer = layoutContent.getViewTreeObserver();

    observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
      @Override
      public void onGlobalLayout() {
        // We're assuming that the other layout is under the scanner
        int activityWidth = layoutContent.getWidth();
        int activityHeight = findViewById(R.id.scanner_fragment).getHeight();

        int usableWidth = layoutContent.getWidth();
        int usableHeight = activityHeight - layoutContent.getHeight();

        int desiredHeight = (int) (usableHeight * 0.8);
        int desiredWidth = (int) (usableWidth * 0.75);

        Rect framingRect = new Rect(
            (usableWidth - desiredWidth) / 2, // left
            (usableHeight - desiredHeight) / 2, // top
            (usableWidth - desiredWidth) / 2 + desiredWidth, // right
            (usableHeight - desiredHeight) / 2 + desiredHeight// bottom
        );
        Log.v("RECT", "left: " + framingRect.left + " top: " + framingRect.top + " right: " + framingRect.right + " bottom: " + framingRect.bottom + " activityHeight: " + activityHeight + " activitiWidth: " + activityWidth);
        mScannerFragment.setFramingRect(framingRect);
      }
    });
  }

  private View.OnClickListener createToggleFlashListener() {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        torchState = !torchState;
        mScannerFragment.setTorch(torchState);
      }
    };
  }
}
