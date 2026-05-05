package com.dlight.feature.user;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dlight.R;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ImageView btnBack = findViewById(R.id.btn_back);
        TextView tvVersion = findViewById(R.id.tv_version);
        View btnLogout = findViewById(R.id.btn_logout);

        tvVersion.setText("v" + getAppVersionName());

        btnBack.setOnClickListener(v -> finish());

        btnLogout.setOnClickListener(v -> {
            getApplicationContext().deleteSharedPreferences("user_prefs");
            Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private String getAppVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName != null ? info.versionName : "1.0.0";
        } catch (Exception e) {
            return "1.0.0";
        }
    }
}
