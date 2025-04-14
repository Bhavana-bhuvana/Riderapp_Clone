package com.kobha.ourmap;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class MainActivity extends AppCompatActivity {
private Button mDriver,mcustomer;
FirebaseDatabase firebaseDatabase;
DatabaseReference firebaseReference;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mDriver=(Button) findViewById(R.id.driver);
        mcustomer=(Button) findViewById(R.id.customer);
         mDriver.setOnClickListener(new View.OnClickListener() {
             @Override
             public void onClick(View v) {
                 Intent i=new Intent(MainActivity.this,DriverLoginActivity.class);
                 startActivity(i);
                 finish();
                 return;

             }
         });
        mcustomer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i=new Intent(MainActivity.this,CustomerLoginActivity.class);
                startActivity(i);
                finish();
                return;

            }
        });

    }
}