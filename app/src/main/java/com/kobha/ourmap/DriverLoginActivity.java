package com.kobha.ourmap;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class DriverLoginActivity extends AppCompatActivity {
private EditText mEmail,mPassword;
    private Button mLogin,mRegistration;
private FirebaseAuth  mAuth;
    private FirebaseAuth.AuthStateListener firebaseAuthListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_login);
        mAuth=FirebaseAuth.getInstance();
        firebaseAuthListener=new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();
                if(user!=null){
                    Intent i=new Intent(DriverLoginActivity.this,DriverMapActivity.class);
                    startActivity(i);
                    finish();
                    return;
                }
            }
        };
        mEmail =(EditText) findViewById(R.id.email);
        mPassword=(EditText) findViewById(R.id.password);
        mLogin =(Button) findViewById(R.id.login);
        mRegistration=(Button) findViewById(R.id.registration);
mRegistration.setOnClickListener(new View.OnClickListener() {
    @Override
    public void onClick(View v) {
       final String email=mEmail.getText().toString();
        final String password=mPassword.getText().toString();
        mAuth.createUserWithEmailAndPassword(email,password).addOnCompleteListener(DriverLoginActivity.this, new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if(!task.isSuccessful()){
                    Toast.makeText(DriverLoginActivity.this,"sign up error",Toast.LENGTH_SHORT).show();

                }else{
                    String user_id=mAuth.getCurrentUser().getUid();
                    DatabaseReference current_user_db=FirebaseDatabase.getInstance().getReference().child("Users").child("Driver").child(user_id);
                    current_user_db.setValue(true);
                    Intent i = new Intent(DriverLoginActivity.this, DriverMapActivity.class);
                    startActivity(i);
                    finish();
                }
            }
        });


    }
});
mLogin.setOnClickListener(new View.OnClickListener() {
    @Override
    public void onClick(View v) {
        final String email=mEmail.getText().toString();
        final String password=mPassword.getText().toString();
        mAuth.signInWithEmailAndPassword(email,password).addOnCompleteListener(DriverLoginActivity.this, new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if(!task.isSuccessful()){
                    Toast.makeText(DriverLoginActivity.this,"sign up error",Toast.LENGTH_SHORT).show();

                } else{
                    String user_id=mAuth.getCurrentUser().getUid();
                    DatabaseReference current_user_db=FirebaseDatabase.getInstance().getReference().child("Users").child("Driver").child(user_id);
                    current_user_db.setValue(true);
                    Intent i = new Intent(DriverLoginActivity.this, DriverMapActivity.class);
                    startActivity(i);
                    finish();
                }

            }
        });
    }
});
    }

}