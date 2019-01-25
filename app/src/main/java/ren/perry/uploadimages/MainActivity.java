package ren.perry.uploadimages;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.tbruyelle.rxpermissions.RxPermissions;
import com.yalantis.ucrop.UCrop;
import com.zhihu.matisse.Matisse;
import com.zhihu.matisse.MimeType;
import com.zhihu.matisse.engine.impl.GlideEngine;
import com.zhihu.matisse.internal.entity.CaptureStrategy;

import org.json.JSONObject;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import ren.perry.library.GlideMan;
import ren.perry.uploadman.FromDataPart;
import ren.perry.uploadman.UploadMan;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    public static final int REQUEST_CODE_CHOOSE = 23;

    private PhotoView photoView;
    private FloatingActionButton fab;

    private MaterialDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        photoView = findViewById(R.id.photoView);
        fab = findViewById(R.id.fab);
        fab.setOnClickListener(this);

        progressDialog = new MaterialDialog.Builder(this)
                .title("上传头像")
                .content("正在上传...")
                .contentGravity(GravityEnum.CENTER)
                .progress(false, 100, true)
                .cancelable(false)
                .canceledOnTouchOutside(false)
                .build();
    }

    @Override
    public void onClick(View v) {
        new RxPermissions(this).request(Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe(aBoolean -> {
                    if (aBoolean) {
                        chooseImage();
                    } else {
                        showHint();
                    }
                });
    }

    /**
     * 拍照或选择图片
     */
    private void chooseImage() {
        Matisse.from(this)
                .choose(MimeType.of(MimeType.JPEG, MimeType.PNG))
                .theme(R.style.Matisse_Zhihu)
                .capture(true)
                .thumbnailScale(0.85f)
                .captureStrategy(new CaptureStrategy(true, getPackageName() + ".fileprovider"))
                .countable(false)
                .maxSelectable(1)
                .imageEngine(new GlideEngine())
                .forResult(REQUEST_CODE_CHOOSE);
    }

    private void showHint() {
        new MaterialDialog.Builder(this).title("需要权限")
                .content("执行的操作需要打开相关权限，点击“设置”—>“权限管理”—>打开相关权限即可。")
                .positiveText("设置")
                .negativeText("取消")
                .canceledOnTouchOutside(false)
                .cancelable(false)
                .onPositive((dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                }).show();
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /**
     * 选择图片和剪裁后的回调都在这里哦
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_CHOOSE && resultCode == RESULT_OK) {
            cropImage(data);
        }

        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            final Uri resultUri = UCrop.getOutput(data);
            try {
                assert resultUri != null;
                File file = new File(new URI(resultUri.toString()));
                uploadImage(file);
            } catch (URISyntaxException e) {
                showToast("图片上传失败");
                e.printStackTrace();
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            showToast("图片剪裁失败");
        }
    }

    /**
     * 请求上传图片到服务器的操作
     *
     * @param file 图片文件
     */
    private void uploadImage(File file) {
        List<FromDataPart> parts = new ArrayList<>();
        FromDataPart bean = new FromDataPart("phone", "18244267955");
        parts.add(bean);
        new UploadMan.Builder().from(this)
                .file(file)
                .fileParameters("avatar")//为图片添加参数，长度要与上传的文件数一致
                .otherParameters(parts)//添加额外的参数
                .isCompress(true)//是否压缩（这里用的是Luban压缩）
                .callBack(new UploadMan.OnUploadListener() {
                    @Override
                    public void onSuccess(String result) {
                        try {
                            JSONObject joResult = new JSONObject(result);
                            int code = joResult.getInt("code");
                            String msg = joResult.getString("msg");
                            JSONObject data = joResult.getJSONObject("data");
                            switch (code) {
                                case 1:
                                    String url = "http://172.16.9.170:8080" + data.getString("url");
                                    loadImage(url);
                                    if (progressDialog.isShowing()) progressDialog.dismiss();
                                    showToast("上传成功！");
                                    break;
                                default:
                                    if (progressDialog.isShowing()) progressDialog.dismiss();
                                    showToast("上传失败：" + msg);
                                    break;
                            }
                        } catch (Exception e) {
                            showToast("错误：" + e.getMessage());
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onFail(String errorMsg) {
                        if (progressDialog.isShowing()) progressDialog.dismiss();
                        showToast("上传失败：" + errorMsg);
                    }

                    @Override
                    public void onProgress(int progress) {
                        final int progressFull = 100;
                        runOnUiThread(() -> {
                            if (progress == progressFull) {
                                progressDialog.setContent("已完成");
                                new Handler().postDelayed(progressDialog::dismiss, 500);
                            } else {
                                progressDialog.setContent("正在上传...");
                                progressDialog.setProgress(progress);
                            }
                            if (!progressDialog.isShowing()) progressDialog.show();
                        });
                    }
                }).post("http://172.16.9.170:8080/xkd/uploadAvatar");
    }

    private void loadImage(String url) {
        new GlideMan.Builder().load(url).into(photoView);
    }

    /**
     * 剪裁 + 压缩
     */
    private void cropImage(Intent data) {
        UCrop.Options options = new UCrop.Options();
        options.setMaxScaleMultiplier(5);
        options.setImageToCropBoundsAnimDuration(666);
        options.setShowCropFrame(false);
        options.setCompressionQuality(90);
        options.setMaxBitmapSize(800);
        UCrop.of(Matisse.obtainResult(data).get(0),
                Uri.fromFile(new File(getCacheDir(), "CropImage.jpeg")))
                .withOptions(options)
                .withAspectRatio(1, 1)
                .start(this);
    }
}
