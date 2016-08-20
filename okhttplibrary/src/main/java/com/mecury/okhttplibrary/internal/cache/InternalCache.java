package com.mecury.okhttplibrary.internal.cache;

import com.mecury.okhttplibrary.Request;
import com.mecury.okhttplibrary.Response;

import java.io.IOException;

/**
 * OkHttp's internal cache interface. Applications shouldn't implement this: instead use {@link
 * okhttp3.Cache}.
 * OkHttp的内部缓存接口
 */
public interface InternalCache {

    Response get(Request request) throws IOException;

    /**
     * Remove any cache entries for the supplied {@code request}. This is invoked when the client
     * invalidates the cache, such as when making POST requests.
     * 根据request来移除缓存的实例。当client的缓存无效时调用，如当 POSt请求时
     */
    void remove(Request request) throws IOException;

    /**
     * Handles a conditional request hit by updating the stored cache response with the headers from
     * {@code network}. The cached response body is not updated. If the stored response has changed
     * since {@code cached} was returned, this does nothing.
     * 处理 请求命中 通过用网络得到的 headers 更新存储的response的 headers。如果发现存储的response已经改变，
     * 它什么也不做
     */
    void update(Response cached, Response network);

    /** Track an conditional GET that was satisfied by this cache.
     * 跟踪满足cache的 有条件 的GET
     * */
    void trackConditionalCacheHit();

    /** Track an HTTP response being satisfied with {@code cacheStrategy}. */
    void trackResponse(CacheStrategy cacheStrategy);
}
