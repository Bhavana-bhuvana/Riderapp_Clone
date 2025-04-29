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

import java.util.HashMap;

public class CustomerLoginActivity extends AppCompatActivity {

    private EditText cEmail, cPassword;
    private Button mLogin, mRegistration;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener firebaseAuthListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_login);

        mAuth = FirebaseAuth.getInstance();

        firebaseAuthListener = firebaseAuth -> {
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                Intent i = new Intent(CustomerLoginActivity.this, CustomerMapActivity.class);
                startActivity(i);
                finish();
            }
        };

        cEmail = findViewById(R.id.email);
        cPassword = findViewById(R.id.password);
        mLogin = findViewById(R.id.login);
        mRegistration = findViewById(R.id.registration);

        mRegistration.setOnClickListener(v -> {
            final String email = cEmail.getText().toString();
            final String password = cPassword.getText().toString();

            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(CustomerLoginActivity.this, task -> {
                        if (!task.isSuccessful()) {
                            Toast.makeText(CustomerLoginActivity.this, "Sign up error", Toast.LENGTH_SHORT).show();
                        } else {
                            String user_id = mAuth.getCurrentUser().getUid();
                            DatabaseReference current_user_db = FirebaseDatabase.getInstance()
                                    .getReference().child("Users").child("Customer").child(user_id);

                            HashMap<String, Object> customerInfo = new HashMap<>();
                            customerInfo.put("email", email);
                            customerInfo.put("createdAt", System.currentTimeMillis());

                            current_user_db.setValue(customerInfo);

                            Intent i = new Intent(CustomerLoginActivity.this, CustomerMapActivity.class);
                            startActivity(i);
                            finish();
                        }
                    });
        });

        mLogin.setOnClickListener(v -> {
            final String email = cEmail.getText().toString();
            final String password = cPassword.getText().toString();

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(CustomerLoginActivity.this, task -> {
                        if (!task.isSuccessful()) {
                            Toast.makeText(CustomerLoginActivity.this, "Login error", Toast.LENGTH_SHORT).show();
                        } else {
                            String user_id = mAuth.getCurrentUser().getUid();
                            DatabaseReference current_user_db = FirebaseDatabase.getInstance()
                                    .getReference().child("Users").child("Customer").child(user_id);

                            current_user_db.child("lastLogin").setValue(System.currentTimeMillis());

                            Intent i = new Intent(CustomerLoginActivity.this, CustomerMapActivity.class);
                            startActivity(i);
                            finish();
                        }
                    });
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(firebaseAuthListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (firebaseAuthListener != null) {
            mAuth.removeAuthStateListener(firebaseAuthListener);
        }
    }
}
