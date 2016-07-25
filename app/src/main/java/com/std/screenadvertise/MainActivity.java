package com.std.screenadvertise;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Xml;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.orhanobut.logger.Logger;
import com.shiki.okttp.OkHttpUtils;
import com.shiki.okttp.builder.GetBuilder;
import com.std.screenadvertise.bean.MediaBean;
import com.std.screenadvertise.bean.SystemBean;
import com.std.screenadvertise.bean.TaskBean;
import com.std.screenadvertise.util.ApkHelper;
import com.std.screenadvertise.util.FileHelper;
import com.std.screenadvertise.util.QRCodeWriter;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Response;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener {
    private static final int QR_WIDTH = 100;
    private static final int QR_HEIGHT = 100;
    private final String SYSTEM_CONFIG = "system.xml";
    private final String SYSTEM_CONFIG_TMP = "system.xml.tmp";
    private final String MEDIA_TASK = "media.xml";
    private final String MEDIA_TASK_TMP = "media.xml.tmp";

    private volatile boolean isUpdate = false;
    private SystemBean nowSetting = null;
    private TaskBean nowTask = null;
    private SystemBean tmpSetting = null;
    private TaskBean tmpTask = null;
    private String path;
    private String tid;
    private String version;
    private String uuid;
    private Long datetime;
    private String tidDate;
    private SimpleDateFormat sdf;

    private ImageView ivMain;
    private ImageView ivQRCode;
    private SurfaceView svMain;
    private SurfaceHolder shMain;
    private MediaPlayer mPlayer;
    private int mPosition = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_main);
        ivMain = (ImageView) findViewById(R.id.iv_main);
        ivQRCode = (ImageView) findViewById(R.id.iv_qrcode);
        svMain = (SurfaceView) findViewById(R.id.sv_main);
        shMain = svMain.getHolder();
        shMain.addCallback(this);
        mPlayer = new MediaPlayer();
        mPlayer.setOnCompletionListener(this);
        mPlayer.setOnPreparedListener(this);
        Logger.init();
        path = getApplicationContext().getExternalFilesDir(null).getAbsolutePath();
        version = ApkHelper.getVersionName(this);
        final SharedPreferences sp = getSharedPreferences("packet", MODE_PRIVATE);
        tid = sp.getString("tid", "");
        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        tidDate = sdf.format(new Date());
        if (tid.equals("")) {
            final EditText etTid = new EditText(this);
            etTid.setLines(3);
            new AlertDialog.Builder(this).setTitle("请输入终端编号")
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .setView(etTid)
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (!etTid.getText().toString().trim().equals("")) {
                                tid = etTid.getText().toString().trim();
                                SharedPreferences.Editor editor = sp.edit();
                                editor.putString("tid", tid);
                                editor.commit();
                                startPlay();
                            } else {
                                Toast.makeText(MainActivity.this, "终端编号未设置，请重新运行！", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }).show();
        } else {
            startPlay();
        }
    }

    private void startPlay() {
        initMediaConfig();
        createQRImage(tid + "," + tidDate);
    }

    private void initMediaConfig() {
        Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                File systemFile = new File(path + "/xml/" + SYSTEM_CONFIG);
                if (systemFile.exists()) {
                    nowSetting = loadSystemXml(systemFile);
                } else {
                    nowSetting = loadSystemXml(null);
                }
                File taskFile = new File(path + "/xml/" + MEDIA_TASK);
                if (taskFile.exists()) {
                    nowTask = loadMediaXml(taskFile);
                } else {
                    nowTask = loadMediaXml(null);
                }
                startHeartbeat();
                subscriber.onNext("init finish");
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<String>() {
            @Override
            public void call(String s) {
                mediaPlay();
            }
        });
    }

    private void mediaPlay() {
        uuid = null;
        datetime = null;
        if (isUpdate) {
            if (tmpSetting != null && tmpTask != null) {
                nowSetting = tmpSetting;
                nowTask = tmpTask;
                tmpSetting = null;
                tmpTask = null;
                mPosition = 0;
                try {
                    FileHelper.deleteQuietly(new File(path + "/xml/" + SYSTEM_CONFIG));
                    FileHelper.moveFile(new File(path + "/xml/" + SYSTEM_CONFIG_TMP), new File(path + "/xml/" + SYSTEM_CONFIG));
                    FileHelper.deleteQuietly(new File(path + "/xml/" + MEDIA_TASK));
                    FileHelper.moveFile(new File(path + "/xml/" + MEDIA_TASK_TMP), new File(path + "/xml/" + MEDIA_TASK));
                } catch (IOException e) {
                    // ignored
                }
            }
            isUpdate = false;
        }
        if (nowSetting == null || nowTask == null || nowTask.getVideoList().size() == 0) {
            Observable.timer(15, TimeUnit.SECONDS).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
                @Override
                public void call(Long aLong) {
                    mediaPlay();
                }
            });
        } else {
            mPosition = mPosition % nowTask.getVideoList().size();
            MediaBean mediaBean = nowTask.getVideoList().get(mPosition);
            mPosition++;
            if (mediaBean != null) {
                uuid = mediaBean.getUuid();
                String mediaName = mediaBean.getUuid() + (mediaBean.getType().equals("video") ? ".avi" : ".jpg");
                String mediaPath = path + "/media/" + mediaName;
                File mediaFile = new File(mediaPath);
                if (!mediaFile.exists()) {
                    mediaPlay();
                } else {
                    if (getMediaPwd(mediaFile).equals(mediaBean.getPwd())) {
                        if (mediaBean.getType().equals("video")) {
                            try {
                                ivMain.setVisibility(View.GONE);
                                mPlayer.setAudioStreamType(AudioManager.STREAM_SYSTEM);
                                mPlayer.setDataSource(mediaPath);
                                if (svMain.getVisibility() == View.GONE) {
                                    svMain.setVisibility(View.VISIBLE);
                                } else {
                                    mPlayer.prepareAsync();
                                }
                            } catch (IOException e) {
                                // ignored
                            }
                        } else {
                            svMain.setVisibility(View.GONE);
                            ivMain.setVisibility(View.VISIBLE);
                            datetime = (System.currentTimeMillis() + 8 * 60 * 60 * 1000) / 1000;
                            ivMain.setImageBitmap(BitmapFactory.decodeFile(mediaPath));
                            Observable.timer(nowSetting.getJpegshowtime(), TimeUnit.SECONDS).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
                                @Override
                                public void call(Long aLong) {
                                    mediaPlay();
                                }
                            });
                        }
                    } else {
                        mediaPlay();
                    }
                }
            }
        }
    }

    private void startHeartbeat() {
        Observable.interval(0, 30, TimeUnit.SECONDS)
                .observeOn(Schedulers.io())
                .map(new Func1<Long, String>() {
                    @Override
                    public String call(Long execNum) {
                        try {
                            Response response = OkHttpUtils.get().url(AdvertiseApi.HTTP + nowSetting.getServer() + "/" + AdvertiseApi.HEAERBEAT_URL)
                                    .addParams("id", tid)
                                    .addParams("cp", version)
                                    .addParams("cuuid", uuid)
                                    .addParams("datetime", String.valueOf(datetime))
                                    .build().execute();
                            if (response.isSuccessful()) {
                                String responseMsg = response.body().string();
                                response.body().close();
                                Logger.d("心跳次数：" + execNum + ", " + responseMsg);
                                return responseMsg;
                            }
                        } catch (IOException e) {
                            // ignored
                        }
                        return null;
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .filter(new Func1<String, Boolean>() {
                    @Override
                    public Boolean call(String responseMsg) {
                        boolean isConnected = (responseMsg != null);
                        if (isConnected)
                            ivQRCode.setVisibility(View.VISIBLE);
                        else
                            ivQRCode.setVisibility(View.GONE);
                        String curDate = sdf.format(new Date());
                        if (!curDate.equals(tidDate)) {
                            tidDate = curDate;
                            createQRImage(tid + "," + tidDate);
                        }
                        return isConnected && responseMsg.length() == 3;
                    }
                })
                .observeOn(Schedulers.io())
                .map(new Func1<String, String>() {
                    @Override
                    public String call(String responseMsg) {
                        if (responseMsg.charAt(1) == '0') {
                            //更新亮度
                        }
                        return String.valueOf(responseMsg.charAt(2));
                    }
                })
                .filter(new Func1<String, Boolean>() {
                    @Override
                    public Boolean call(String updateFlag) {
                        Calendar calendar = Calendar.getInstance();
                        String[] args = nowSetting.getAutoupdate().split(":");
                        int hour = -1;
                        int minute = -1;
                        int second = calendar.get(Calendar.SECOND);
                        if (args != null && args.length == 2) {
                            hour = Integer.parseInt(args[0]);
                            minute = Integer.parseInt(args[1]);
                        }
                        return (updateFlag.equals("1") || (hour == calendar.get(Calendar.HOUR) && minute == calendar.get(Calendar.MINUTE) && second <= 30)) && !isUpdate;
                    }
                })
                .flatMap(new Func1<String, Observable<Integer>>() {
                    @Override
                    public Observable<Integer> call(String updateFlag) {
                        try {
                            updateConfig();
                        } catch (IOException e) {
                            return Observable.error(e);
                        }
                        return downloadMedias(tmpTask.getVideoList());
                    }
                })
                .subscribe(new Subscriber<Integer>() {
                    @Override
                    public void onCompleted() {
                        // ignored
                    }

                    @Override
                    public void onError(Throwable e) {
                        // ignored
                    }

                    @Override
                    public void onNext(Integer updateResult) {
                        Logger.d("最终结果");
                        try {
                            Response response = OkHttpUtils.get().url(AdvertiseApi.HTTP + nowSetting.getServer() + "/" + AdvertiseApi.UPDATE_URL).addParams("id", tid).addParams("success", String.valueOf(updateResult)).build().execute();
                            if (response.isSuccessful()) {
                                response.body().close();
                            }
                        } catch (IOException e) {
                            // ignored
                        }
                        if (updateResult == 1)
                            isUpdate = true;
                    }
                });
    }

    private Observable<Integer> downloadMedias(List<MediaBean> medias) {
        return Observable.from(medias).map(new Func1<MediaBean, Integer>() {
            @Override
            public Integer call(MediaBean media) {
                final Integer[] isSuccess = new Integer[1];
                isSuccess[0] = 0;
                Observable.just(media)
                        .flatMap(new Func1<MediaBean, Observable<String>>() {
                            @Override
                            public Observable<String> call(MediaBean media) {
                                try {
                                    downloadMedia(media);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    return Observable.error(e);
                                }
                                return Observable.just("");
                            }
                        })
                        .retryWhen(new Func1<Observable<? extends Throwable>, Observable<?>>() {
                            @Override
                            public Observable<?> call(Observable<? extends Throwable> errors) {
                                return errors.zipWith(Observable.range(1, 10), new Func2<Throwable, Integer, Integer>() {
                                    @Override
                                    public Integer call(Throwable throwable, Integer i) {
                                        System.out.println("重试" + i);
                                        return i;
                                    }
                                });
                            }
                        })
                        .subscribe(new Subscriber<Object>() {
                            @Override
                            public void onCompleted() {
                                System.out.println("MediaBean onCompleted");
                            }

                            @Override
                            public void onError(Throwable e) {
                                onCompleted();
                            }

                            @Override
                            public void onNext(Object o) {
                                isSuccess[0] = 1;
                            }
                        });
                return isSuccess[0];
            }
        }).reduce(new Func2<Integer, Integer, Integer>() {
            @Override
            public Integer call(Integer val1, Integer val2) {
                return val1 * val2;
            }
        });
    }

    private void downloadMedia(MediaBean media) throws IOException {
        String fileName = media.getUuid() + (media.getType().equals("video") ? ".avi" : ".jpg");
        String fileUrl = AdvertiseApi.HTTP + tmpSetting.getMedia() + "/upload/" + fileName;
        File mediaFile = new File(path + "/media/" + fileName);
        File tmpFile = new File(path + "/media/" + fileName + ".tmp");
        if (!mediaFile.exists()) {
            long curLength = 0;
            GetBuilder builder = OkHttpUtils.get().url(fileUrl);
            if (tmpFile.exists() && media.getPwd() != getMediaPwd(tmpFile)) {
                curLength = tmpFile.length();
                builder.addHeader("range", "bytes=" + curLength + "-");
            }
            Response response = builder.build().execute();
            if (response.isSuccessful()) {
                File downFile = FileHelper.saveFileWithLog(tid, AdvertiseApi.HTTP + nowSetting.getServer() + "/" + AdvertiseApi.UPDATE_URL, path + "/media", fileName, curLength, response);
                response.body().close();
                if (downFile == null) {
                    //FileHelper.forceDelete(new File(path + "/media/" + fileName + ".tmp"));
                    throw new IOException("文件下载出错");
                } else {
                    FileHelper.moveFile(downFile, mediaFile);
                }
            }
        }
    }

    private void updateConfig() throws IOException {
        Response response = OkHttpUtils.get().url(AdvertiseApi.HTTP + nowSetting.getServer() + "/" + AdvertiseApi.CONFIG_URL).addParams("id", tid).tag(this).build().execute();
        if (response.isSuccessful()) {
            File configFile = FileHelper.saveFile(path + "/xml", SYSTEM_CONFIG_TMP, response);
            response.body().close();
            if (configFile != null)
                tmpSetting = loadSystemXml(configFile);
        }
        if (tmpSetting == null) {
            FileHelper.forceDelete(new File(path + "/xml/" + SYSTEM_CONFIG_TMP));
            throw new IOException("错误的配置文件");
        }
        response = OkHttpUtils.get().url(AdvertiseApi.HTTP + nowSetting.getServer() + "/" + AdvertiseApi.MEDIA_URL).addParams("id", tid).tag(this).build().execute();
        if (response.isSuccessful()) {
            File mediaFile = FileHelper.saveFile(path + "/xml", MEDIA_TASK_TMP, response);
            response.body().close();
            if (mediaFile != null)
                tmpTask = loadMediaXml(mediaFile);
        }
        if (tmpTask == null) {
            FileHelper.forceDelete(new File(path + "/xml/" + MEDIA_TASK_TMP));
            throw new IOException("错误的配置文件");
        }
    }

    private SystemBean loadSystemXml(File systemFile) {
        SystemBean setting = new SystemBean();
        XmlPullParser parser;
        try {
            Field field = null;
            if (systemFile == null)
                parser = getResources().getXml(R.xml.system);
            else {
                parser = Xml.newPullParser();
                parser.setInput(new FileInputStream(systemFile), "UTF-8");
            }
            while (parser.getEventType() != XmlResourceParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlResourceParser.START_TAG) {
                    if (!parser.getName().equals("root")) {
                        field = setting.getClass().getDeclaredField(parser.getName());
                    }
                } else if (parser.getEventType() == XmlPullParser.TEXT) {
                    field.setAccessible(true);
                    if (field.getType().equals(int.class)) {
                        field.set(setting, Integer.parseInt(parser.getText()));
                    } else {
                        field.set(setting, parser.getText());
                    }
                }
                parser.next();
            }
            if (setting.getAutoupdate() == null || setting.getJpegshowtime() == 0 || setting.getMedia() == null
                    || setting.getServer() == null)
                return null;
            else
                return setting;
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private TaskBean loadMediaXml(File mediaFile) {
        TaskBean task = new TaskBean();
        task.setVideoList(new ArrayList<MediaBean>());
        task.setPicList(new ArrayList<MediaBean>());
        XmlPullParser parser;
        try {
            if (mediaFile == null)
                parser = getResources().getXml(R.xml.media);
            else {
                parser = Xml.newPullParser();
                parser.setInput(new FileInputStream(mediaFile), "UTF-8");
            }
            while (parser.getEventType() != XmlResourceParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlResourceParser.START_TAG) {
                    if (parser.getName().equals("task")) {
                        task.setStyle(parser.getAttributeValue(null, "style"));
                    } else if (parser.getName().equals("uuid")) {
                        MediaBean media = new MediaBean();
                        media.setType(parser.getAttributeValue(null, "type"));
                        media.setPwd(parser.getAttributeValue(null, "pwd"));
                        parser.next();
                        media.setUuid(parser.getText());
                        if (media.getType() == null || media.getType().equals("")) {
                            continue;
                        }
                        if (media.getPwd() == null || media.getPwd().equals("")) {
                            continue;
                        }
                        if (media.getUuid() == null || media.getUuid().equals("")) {
                            continue;
                        }
                        if (task.getStyle() != null && task.getStyle().equals("3")) {
                            task.getVideoList().add(media);
                        } else if (task.getStyle() != null && task.getStyle().equals("4")) {
                            if (media.getType().equals("video"))
                                task.getVideoList().add(media);
                            else if (media.getType().equals("jpeg"))
                                task.getPicList().add(media);
                        }
                    }
                }
                parser.next();
            }
            if (task.getStyle() == null || task.getVideoList() == null || task.getPicList() == null || task.getVideoList().size() == 0)
                return null;
            else
                return task;
        } catch (Exception e) {
            // ignored
        }
        return null;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mPlayer.setDisplay(holder);
        mPlayer.prepareAsync();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // ignored
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // ignored
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        mPlayer.reset();
        mediaPlay();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        datetime = (System.currentTimeMillis() + 8 * 60 * 60 * 1000) / 1000;
        mPlayer.start();
    }

    private String getMediaPwd(File mediaFile) {
        String str = "";
        try {
            RandomAccessFile randomAccess = new RandomAccessFile(mediaFile, "r");
            str = String.valueOf(mediaFile.length() + randomAccess.read());
            randomAccess.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return str;
    }

    private void createQRImage(String url) {
        try {
            if (url == null || "".equals(url) || url.length() < 1) {
                return;
            }
            Hashtable<EncodeHintType, Object> hints = new Hashtable<EncodeHintType, Object>();
            hints.put(EncodeHintType.CHARACTER_SET, "utf-8");
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix bitMatrix = new QRCodeWriter().encode(url, QR_WIDTH, QR_HEIGHT, hints);
            int[] pixels = new int[QR_WIDTH * QR_HEIGHT];
            for (int y = 0; y < bitMatrix.getHeight(); y++) {
                for (int x = 0; x < bitMatrix.getWidth(); x++) {
                    if (bitMatrix.get(x, y)) {
                        pixels[y * QR_WIDTH + x] = 0xff000000;
                    } else {
                        pixels[y * QR_WIDTH + x] = 0xffffffff;
                    }
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(QR_WIDTH, QR_HEIGHT, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, QR_WIDTH, 0, 0, QR_WIDTH, QR_HEIGHT);
            ivQRCode.setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }
}
