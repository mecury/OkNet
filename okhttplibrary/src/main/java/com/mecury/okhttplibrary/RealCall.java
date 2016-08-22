package com.mecury.okhttplibrary;

import com.mecury.okhttplibrary.internal.http.RetryAndFollowUpInterceptor;

/**
 * Created by 海飞 on 2016/8/16.
 */
final class RealCall implements Call {
    private OkHttpClient client;
    private RetryAndFollowUpInterceptor mRetryAndFollowUpIntercepter;

}
