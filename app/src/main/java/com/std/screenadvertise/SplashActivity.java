package com.std.screenadvertise;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.content.DialogInterface.OnCancelListener;
import android.support.v7.app.AppCompatActivity;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.gson.Gson;
import com.shiki.okttp.OkHttpUtils;
import com.shiki.okttp.callback.FileCallback;
import com.shiki.okttp.callback.StringCallback;
import com.std.screenadvertise.bean.VersionBean;
import com.std.screenadvertise.util.ApkHelper;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

import okhttp3.Call;

/**
 * Created by Maik on 2016/6/22.
 */
public class SplashActivity extends AppCompatActivity {
    private RelativeLayout rlRoot;
    private Timer timer;
    private VersionBean version;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_splash);
        rlRoot = (RelativeLayout) findViewById(R.id.rl_root);
        AlphaAnimation anim = new AlphaAnimation(0.2f, 1);
        anim.setDuration(2000);
        rlRoot.startAnimation(anim);
        timer = new Timer();
        TimerTask task = new TimerTask() {

            @Override
            public void run() {
                checkVersion();
            }
        };
        timer.schedule(task, 2000);
    }

    private void checkVersion() {
        OkHttpUtils
                .get()
                .url(AdvertiseApi.URL_UPDATE)
                .build()
                .execute(new StringCallback() {

                    @Override
                    public void onError(Call call, Exception e) {
                        Toast.makeText(SplashActivity.this, R.string.network_error, Toast.LENGTH_SHORT).show();
                        enterHome();
                    }

                    @Override
                    public void onResponse(String response) {
                        version = new Gson().fromJson(response, VersionBean.class);
                        if (version == null) {
                            Toast.makeText(SplashActivity.this, R.string.data_error, Toast.LENGTH_SHORT).show();
                            enterHome();
                        } else {
                            if (ApkHelper.getVersionCode(SplashActivity.this) < version.getVersionCode()) {
                                showUpdateDialog();
                            } else {
                                enterHome();
                            }
                        }
                    }
                });
    }

    protected void showUpdateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("发现新版本:" + version.getVersionName());
        builder.setMessage(version.getVersionDesc());
        builder.setPositiveButton("立即更新",
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        downloadAPK();
                    }
                });
        builder.setNegativeButton("以后再说",
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        enterHome();
                    }
                });
        builder.setOnCancelListener(new OnCancelListener() {

            @Override
            public void onCancel(DialogInterface dialog) {
                enterHome();
            }
        });
        builder.show();
    }

    private void downloadAPK() {
        final ProgressDialog dialog = new ProgressDialog(SplashActivity.this);
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setTitle(getString(R.string.downloading));
        dialog.setMessage(getString(R.string.waiting));
        dialog.setProgress(0);
        dialog.setMax(100);
        dialog.show();

        if (ApkHelper.existSDCard() && !"".equals(version.getVersionUrl())) {
            OkHttpUtils.get().url(version.getVersionUrl()).build().execute(new FileCallback(Environment.getExternalStorageDirectory().getAbsolutePath(), AdvertiseApi.APK_NAME) {

                @Override
                public void inProgress(float progress) {
                    dialog.setProgress(Math.round(progress * 100));
                }

                @Override
                public void onError(Call call, Exception e) {
                    dialog.dismiss();
                    Toast.makeText(SplashActivity.this, R.string.network_error, Toast.LENGTH_SHORT).show();
                    enterHome();
                }

                @Override
                public void onResponse(File response) {
                    dialog.dismiss();
                    ApkHelper.install(SplashActivity.this, response);
                }
            });
        }
    }

    private void enterHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timer.cancel();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        enterHome();
    }
}
