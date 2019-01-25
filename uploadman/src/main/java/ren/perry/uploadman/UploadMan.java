package ren.perry.uploadman;

import android.app.Activity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import top.zibin.luban.Luban;
import top.zibin.luban.OnCompressListener;


/**
 * 多图片压缩上传带进度(建造者模式)
 * Email: pl.w@outlook.com
 * Created by perry on 2017/8/31.
 */

public class UploadMan {

    private Activity activity;
    private String[] fileParas;
    private List<FromDataPart> dataParts;
    private List<File> fileList;
    private String url;
    private OnUploadListener listener;
    private boolean compress;

    private List<File> list = new ArrayList<>();    //压缩后的图片

    private UploadMan(Builder builder) {
        this.activity = builder.activity;
        this.fileParas = builder.fileParas;
        this.dataParts = builder.dataParts;
        this.fileList = builder.fileList;
        this.url = builder.url;
        this.listener = builder.listener;
        this.compress = builder.compress;
    }

    public static class Builder {
        private Activity activity;
        private String[] fileParas;
        private List<FromDataPart> dataParts;
        private List<File> fileList;
        private String url;
        private OnUploadListener listener;
        private boolean compress;

        //如果不调用from,返回结果将在子线程中进行。
        public Builder from(Activity activity) {
            this.activity = activity;
            return this;
        }

        //添加多图片参数，长度应与多文件长度一致
        public Builder fileParameters(String... fileParas) {
            this.fileParas = fileParas;
            return this;
        }

        //添加多参数
        public Builder otherParameters(List<FromDataPart> dataParts) {
            this.dataParts = dataParts;
            return this;
        }

        //多图片文件
        public Builder files(List<File> fileList) {
            this.fileList = fileList;
            return this;
        }

        //单图片文件
        public Builder file(File file) {
            List<File> files = new ArrayList<>();
            files.add(file);
            this.fileList = files;
            return this;
        }

        //上传回调
        public Builder callBack(OnUploadListener listener) {
            this.listener = listener;
            return this;
        }

        //是否压缩
        public Builder isCompress(boolean compress) {
            this.compress = compress;
            return this;
        }

        //传入url，请求上传
        public UploadMan post(String url) {
            this.url = url;
            UploadMan uploadMan = new UploadMan(this);
            uploadMan.upload();
            return uploadMan;
        }
    }

    private void upload() {
        if (compress) {
            for (File file : fileList) {
                Luban.with(activity).load(file).setCompressListener(new OnCompressListener() {
                    @Override
                    public void onStart() {

                    }

                    @Override
                    public void onSuccess(File file) {
                        list.add(file);
                        if (list.size() == fileList.size()) {
                            //所有照片都已经压缩完成
                            request(list);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        list.add(file);
                    }
                }).launch();
            }
        } else {
            request(fileList);
        }
    }

    private void request(List<File> files) {
        MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
        //为参数添加照片文件
        for (int i = 0; i < files.size(); i++) {
            RequestBody body = RequestBody.create(MediaType.parse("multipart/form-data"), files.get(i));
            builder.addPart(MultipartBody.Part.createFormData(fileParas[i], files.get(i).getName(), body));
        }

        //添加其他参数
        for (FromDataPart part : dataParts) {
            builder.addFormDataPart(part.getKey(), part.getValue());
        }
        Request.Builder request = new Request.Builder().url(url).post(new CmlRequestBody(builder.build()) {
            @Override
            public void loading(long current, long total, boolean done) {
                if (listener != null) {
                    if (!done) {
                        activity.runOnUiThread(() -> listener.onProgress((int) current));
                    }
                }
            }
        });

        new OkHttpClient().newBuilder().connectTimeout(5, TimeUnit.MINUTES).build()
                .newCall(request.build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (listener != null) {
                    if (activity != null) {
                        activity.runOnUiThread(() -> listener.onFail(e.getMessage()));
                    } else {
                        listener.onFail(e.getMessage());
                    }
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (listener != null) {
                    if (!response.isSuccessful() || response.body() == null) {
                        if (activity != null) {
                            activity.runOnUiThread(() -> listener.onFail(response.message()));
                        } else {
                            listener.onFail(response.message());
                        }
                    } else {
                        if (activity != null) {
                            activity.runOnUiThread(() -> {
                                try {
                                    listener.onSuccess(response.body().string());
                                } catch (IOException e) {
                                    listener.onFail(e.getMessage());
                                    e.printStackTrace();
                                }
                            });
                        } else {
                            listener.onSuccess(response.body().string());
                        }
                    }
                }
            }
        });
    }

    public interface OnUploadListener {
        void onSuccess(String result);

        void onFail(String errorMsg);

        void onProgress(int progress);
    }
}
