package com.kobha.ourmap;

import android.content.Intent;
import android.os.Bundle;
import android.view.SubMenu;
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


public class CustomerLoginActivity extends AppCompatActivity {
    private EditText cEmail,cPassword;
    private Button mLogin,mRegistration;
    private FirebaseAuth  mAuth;
    private FirebaseAuth.AuthStateListener firebaseAuthListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_login);
        mAuth= FirebaseAuth.getInstance();
        firebaseAuthListener=new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();
                if(user!=null){
                    Intent i=new Intent(CustomerLoginActivity.this, CustomerMapActivity.class);
                    startActivity(i);
                    finish();
                    return;
                }
            }
        };
        cEmail =(EditText) findViewById(R.id.email);
        cPassword=(EditText) findViewById(R.id.password);
        mLogin =(Button) findViewById(R.id.login);
        mRegistration=(Button) findViewById(R.id.registration);
        mRegistration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String email=cEmail.getText().toString();
                final String password=cPassword.getText().toString();
                mAuth.createUserWithEmailAndPassword(email,password).addOnCompleteListener(CustomerLoginActivity.this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if(!task.isSuccessful()){
                            Toast.makeText(CustomerLoginActivity.this,"sign up error",Toast.LENGTH_SHORT).show();

                        }else{
                            String user_id=mAuth.getCurrentUser().getUid();
                            DatabaseReference current_user_db= FirebaseDatabase.getInstance().getReference().child("Users").child("Customer").child(user_id);
                            current_user_db.setValue(true);
                            Intent i = new Intent(CustomerLoginActivity.this, CustomerMapActivity.class);
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
                final String email=cEmail.getText().toString();
                final String password=cPassword.getText().toString();
                mAuth.signInWithEmailAndPassword(email,password).addOnCompleteListener(CustomerLoginActivity.this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if(!task.isSuccessful()){
                            Toast.makeText(CustomerLoginActivity.this,"sign up error",Toast.LENGTH_SHORT).show();

                        } else{
                            String user_id=mAuth.getCurrentUser().getUid();
                            DatabaseReference current_user_db=FirebaseDatabase.getInstance().getReference().child("Users").child("Customer").child(user_id);
                            current_user_db.setValue(true);
                            Intent i = new Intent(CustomerLoginActivity.this, CustomerMapActivity.class);
                            startActivity(i);
                            finish();
                        }

                    }
                });
            }
        });
    }
}