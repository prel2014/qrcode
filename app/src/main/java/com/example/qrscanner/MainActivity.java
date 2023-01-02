package com.example.qrscanner;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import java.util.Iterator;

public class MainActivity extends AppCompatActivity {
    private TextView txt_mensaje;
    private Button btn_escaner;
    boolean torchState = false;
    private Button btn_ver_codigos;
    private Button btn_scan;
    Button mToggleButton;
    BarCodeScannerFragment mScannerFragment;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.example.qrscanner.R.layout.activity_sample);
        FragmentManager fm = getSupportFragmentManager();
        mScannerFragment = (BarCodeScannerFragment) fm.findFragmentById(R.id.scanner_fragment);
        btn_ver_codigos = (Button)findViewById(R.id.btn_ver_codigos);
        btn_ver_codigos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent myIntent = new Intent(getBaseContext(), Vista.class);
                Iterator<String> ite=mScannerFragment.codigos.iterator();
                int i=0;
                myIntent.putExtra("lon", String.valueOf(mScannerFragment.codigos.size()));
                while(ite.hasNext()){
                    //myIntent.putExtra("hora_")
                    myIntent.putExtra("cod_"+String.valueOf(i), ite.next());
                    i++;
                }
                startActivity(myIntent);
            }
        });
        btn_scan = (Button)findViewById(R.id.btn_scan);
        btn_scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mScannerFragment.startScan();
            }
        });
        mToggleButton = (Button) findViewById(R.id.button_flash);
        mToggleButton.setOnClickListener(createToggleFlashListener());
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
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        IntentResult result=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);
        if(result != null){
            if(result.getContents() == null){
                Toast.makeText(this, "lectura cancelada", Toast.LENGTH_LONG).show();
            }else{
                Toast.makeText(this, result.getContents(), Toast.LENGTH_LONG).show();
                txt_mensaje.setText(result.getContents());
            }
        }else{
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}