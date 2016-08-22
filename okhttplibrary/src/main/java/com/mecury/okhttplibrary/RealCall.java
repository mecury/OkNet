package com.mecury.okhttplibrary;

import com.mecury.okhttplibrary.internal.NamedRunnable;
import com.mecury.okhttplibrary.internal.cache.CacheInterceptor;
import com.mecury.okhttplibrary.internal.connection.CallServerInterceptor;
import com.mecury.okhttplibrary.internal.connection.StreamAllocation;
import com.mecury.okhttplibrary.internal.http.BridgeInterceptor;
import com.mecury.okhttplibrary.internal.http.RealInterceptorChain;
import com.mecury.okhttplibrary.internal.http.RetryAndFollowUpInterceptor;
import com.mecury.okhttplibrary.internal.platform.Platform;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by 海飞 on 2016/8/16.
 */
final class RealCall implements Call {
    private OkHttpClient client;
    private RetryAndFollowUpInterceptor retryAndFollowUpInterceptor;

    private boolean executed;

    /** The application's original request unadulterated by redirects or auth headers.
     * 应用的原始请求，没有 重定向和 身份头
     */
    Request originalRequest;

    protected RealCall(OkHttpClient client, Request originalRequest) {
        this.client = client;
        this.originalRequest = originalRequest;
        this.retryAndFollowUpInterceptor = new RetryAndFollowUpInterceptor(client);
    }

    @Override public Request request() {
        return originalRequest;
    }

    @Override
    public Response execute() throws IOException {
        synchronized (this) {
            if (executed) throw new IllegalStateException("Already Executed");
            executed = true;
        }
        try {
            client.dispatcher.executed(this);
            Response result = getResponseWithInterceptorChain();
            if (result == null) throw new IOException("Canceled");
            return result;
        }finally {
            client.dispatcher.finished(this);
        }
    }

    //判断是否已经执行过
    synchronized void setForWebSocket() {
        if (executed) throw new IllegalStateException("Already Executed");
        this.retryAndFollowUpInterceptor.setForWebSocket(true);
    }

    @Override public void enqueue(Callback responseCallback) {
        synchronized (this) {
            if (executed) throw new IllegalStateException("Already Executed");
            executed = true;
        }
        client.dispatcher().enqueue(new AsyncCall(responseCallback));
    }

    @Override public void cancel() {
        retryAndFollowUpInterceptor.cancel(); //立即关闭socket连接，使用这个去中断任何运行在线程中的request
    }

    @Override public synchronized boolean isExecuted() {
        return executed;
    }

    @Override public boolean isCanceled() {
        return retryAndFollowUpInterceptor.isCanceled();
    }

    @SuppressWarnings("CloneDoesntCallSuperClone") // We are a final type & this saves clearing state.
    @Override public RealCall clone() {
        return new RealCall(client, originalRequest);
    }

    StreamAllocation streamAllocation() {
        return retryAndFollowUpInterceptor.streamAllocation();
    }

    //异步请求
    final class AsyncCall extends NamedRunnable {
        private final Callback responseCallback;

        private AsyncCall(Callback responseCallback) {
            super("OkHttp %s", redactedUrl());
            this.responseCallback = responseCallback;
        }

        String host() {
            return originalRequest.url().host();
        }

        Request request() {
            return originalRequest;
        }

        RealCall get() {
            return RealCall.this;
        }

        @Override protected void execute() {
            boolean signalledCallback = false;
            try {
                Response response = getResponseWithInterceptorChain();
                if (retryAndFollowUpInterceptor.isCanceled()) {
                    signalledCallback = true;
                    responseCallback.onFailure(RealCall.this, new IOException("Canceled"));
                } else {
                    signalledCallback = true;
                    responseCallback.onResponse(RealCall.this, response);
                }
            } catch (IOException e) {
                if (signalledCallback) {
                    // Do not signal the callback twice!
                    Platform.get().log(INFO, "Callback failure for " + toLoggableString(), e);
                } else {
                    responseCallback.onFailure(RealCall.this, e);
                }
            } finally {
                client.dispatcher().finished(this);
            }
        }
    }

    /**
     * Returns a string that describes this call. Doesn't include a full URL as that might contain
     * sensitive information.
     * 返回一段描述请求string。不包括完整的URl
     */
    private String toLoggableString() {
        String string = retryAndFollowUpInterceptor.isCanceled() ? "canceled call" : "call";
        return string + " to " + redactedUrl();
    }

    String redactedUrl() {
        return originalRequest.url().redact().toString();
    }

    //拦截器的责任链。***************这里重要***************
    private Response getResponseWithInterceptorChain() throws IOException {
        // Build a full stack of interceptors.
        List<Interceptor> interceptors = new ArrayList<>();
        interceptors.addAll(client.interceptors());
        interceptors.add(retryAndFollowUpInterceptor);
        interceptors.add(new BridgeInterceptor(client.cookieJar()));
        interceptors.add(new CacheInterceptor(client.internalCache()));
        interceptors.add(new ConnectInterceptor(client));
        if (!retryAndFollowUpInterceptor.isForWebSocket()) {
            interceptors.addAll(client.networkInterceptors());
        }
        interceptors.add(new CallServerInterceptor(
                retryAndFollowUpInterceptor.isForWebSocket()));

        Interceptor.Chain chain = new RealInterceptorChain(
                interceptors, null, null, null, 0, originalRequest);
        return chain.proceed(originalRequest); //运行原始的request
    }
}















