package com.example.qrscanner;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

/**
 * Created by joyarzun on 2/5/14.
 */

public class BarCodeContainerFragment extends Fragment {
  public BarCodeContainerFragment() {

  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_nested_fragment, container, false);
  }
}