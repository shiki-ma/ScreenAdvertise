package com.shiki.okttp.callback;

import java.io.IOException;

import okhttp3.Response;

/**
 * Created by Maik on 2016/1/19.
 */
public abstract class StringCallback extends Callback<String> {

    @Override
    public String parseNetworkResponse(Response response) throws IOException {
        return response.body().string();
    }
}
