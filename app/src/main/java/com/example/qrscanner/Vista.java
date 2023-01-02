package com.example.qrscanner;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
public class Vista extends AppCompatActivity {
    private Button btn_volver;
    private ListaCodeQR lis;
    private String o[];
    private RecyclerView lista;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.resultado_qr_code);
        btn_volver = (Button) findViewById(R.id.btn_volver);
        btn_volver.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getBaseContext(),MainActivity.class);
                startActivity(intent);
            }
        });
        Intent intent=getIntent();
        String lon = intent.getExtras().getString("lon");
        o=new String[Integer.parseInt(lon)];
        for(int i=0;i<Integer.parseInt(lon);i++){
            o[i] = intent.getExtras().getString("cod_"+i);
        }
        lis=new ListaCodeQR(o);
        lista = findViewById(R.id.lista);
        lista.setLayoutManager(new LinearLayoutManager(this));
        lista.setAdapter(lis);
    }
}