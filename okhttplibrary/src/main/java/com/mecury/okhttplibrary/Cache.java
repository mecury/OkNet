package com.mecury.okhttplibrary;

import com.mecury.okhttplibrary.internal.cache.CacheStrategy;
import com.mecury.okhttplibrary.internal.cache.DiskLruCache;
import com.mecury.okhttplibrary.internal.cache.InternalCache;
import com.mecury.okhttplibrary.internal.io.FileSystem;

import java.io.Closeable;
import java.io.File;
import java.io.Flushable;
import java.io.IOException;

/**
 * Caches HTTP and HTTPS responses to the filesystem so they may be reused, saving time and
 * bandwidth.
 * 缓存Http 和 HTTPS 的responses 在 文件中 以便他们能够重复利用， 保存时间和带宽
 *
 * <h3>Cache Optimization(优化)</h3>
 *
 * <p>To measure cache effectiveness, this class tracks three statistics:
 * 为了保证 缓存的有效性 ，这个类 跟踪三个统计
 * <ul>
 *     <li><strong>{@linkplain #requestCount() Request Count:}</strong> the number of HTTP
 *         requests issued since this cache was created.缓存创建以来的请求次数
 *     <li><strong>{@linkplain #networkCount() Network Count:}</strong> the number of those
 *         requests that required network use. 请求要求使用网络的的次数
 *     <li><strong>{@linkplain #hitCount() Hit Count:}</strong> the number of those requests
 *         whose responses were served by the cache. responses使用缓存的次数
 * </ul>
 *
 * Sometimes a request will result in a conditional cache hit. If the cache contains a stale copy of
 * the response, the client will issue a conditional {@code GET}. The server will then send either
 * the updated response if it has changed, or a short 'not modified' response if the client's copy
 * is still valid. Such responses increment both the network count and hit count.
 * 有时一个请求将导致一个有条件的缓存命中。如果这个缓存包含一个 过期的 response 的复制，client 将会发出GET请求
 * 如果responses 已经改变，server将会更新 response；如果一个短的没有修改的客户端的response的副本仍然是有效的
 * 这样的response将会增加 网络的流量和命中
 *
 * <p>The best way to improve the cache hit rate is by configuring the web server to return
 * cacheable responses. Although this client honors all <a
 * href="http://tools.ietf.org/html/rfc7234">HTTP/1.1 (RFC 7234)</a> cache headers, it doesn't cache
 * partial responses.
 *
 * <h3>Force a Network Response</h3>
 *
 * 一些情况下，我们需要跳过缓存
 * <p>In some situations, such as after a user clicks a 'refresh' button, it may be necessary to
 * skip the cache, and fetch data directly from the server. To force a full refresh, add the {@code
 * no-cache} directive: <pre>   {@code
 *
 *   Request request = new Request.Builder()
 *       .cacheControl(new CacheControl.Builder().noCache().build())
 *       .url("http://publicobject.com/helloworld.txt")
 *       .build();
 * }</pre>
 *
 * 缓存的 response 需要server 的 验证
 * If it is only necessary to force a cached response to be validated by the server, use the more
 * efficient {@code max-age=0} directive(指令) instead: <pre>   {@code
 *
 *   Request request = new Request.Builder()
 *       .cacheControl(new CacheControl.Builder()
 *           .maxAge(0, TimeUnit.SECONDS)
 *           .build())
 *       .url("http://publicobject.com/helloworld.txt")
 *       .build();
 * }</pre>
 *
 * <h3>Force a Cache Response</h3>
 *
 * 有时你会想显示一些资源 如果 他们是立即可用的。当APP加载最新的数据时，他们能够被使用
 * <p>Sometimes you'll want to show resources if they are available immediately, but not otherwise.
 * This can be used so your application can show <i>something</i> while waiting for the latest data
 * to be downloaded. To restrict a request to locally-cached resources, add the {@code
 * only-if-cached} directive: <pre>   {@code
 *
 *     Request request = new Request.Builder()
 *         .cacheControl(new CacheControl.Builder()
 *             .onlyIfCached()
 *             .build())
 *         .url("http://publicobject.com/helloworld.txt")
 *         .build();
 *     Response forceCacheResponse = client.newCall(request).execute();
 *     if (forceCacheResponse.code() != 504) {
 *       // The resource was cached! Show it.
 *     } else {
 *       // The resource was not cached.
 *     }
 * }</pre>
 *
 * 当一个老的缓存比没有response好时
 * This technique works even better in situations where a stale response is better than no response.
 * To permit stale cached responses, use the {@code max-stale} directive with the maximum staleness
 * in seconds: <pre>   {@code
 *
 *   Request request = new Request.Builder()
 *       .cacheControl(new CacheControl.Builder()
 *           .maxStale(365, TimeUnit.DAYS)
 *           .build())
 *       .url("http://publicobject.com/helloworld.txt")
 *       .build();
 * }</pre>
 *
 * <p>The {@link CacheControl} class can configure request caching directives and parse response
 * caching directives. It even offers convenient constants {@link CacheControl#FORCE_NETWORK} and
 * {@link CacheControl#FORCE_CACHE} that address the use cases above.
 */
public final class Cache implements Closeable, Flushable {
    private static final int VERSION = 201105;
    private static final int ENTRY_METADATA = 0;
    private static final int ENTRY_BODY = 1;
    private static final int ENTRY_COUNT = 2;

    final InternalCache internalCache = new InternalCache() {
        @Override
        public Response get(Request request) throws IOException {
            return Cache.this.get(request);
        }

        @Override
        public void remove(Request request) throws IOException {

        }

        @Override
        public void update(Response cached, Response network) {

        }

        @Override
        public void trackConditionalCacheHit() {

        }

        @Override
        public void trackResponse(CacheStrategy cacheStrategy) {

        }
    };

    /* read and write statistics(统计数据), all guarded by 'this' */
    private int writeSuccessCount;
    private int writeAbortCount;
    private int networkCount;
    private int hitCount;
    private int requestCount;

    public Cache(File directory, long maxSize) {
        this(directory, maxSize, FileSystem.SYSTEM);
    }

    Cache(File directory, long maxSize, FileSystem fileSystem) {
        this.cache = DiskLruCache.create(fileSystem, directory, VERSION, ENTRY_COUNT, maxSize);
    }


    @Override
    public void close() throws IOException {

    }

    @Override
    public void flush() throws IOException {

    }
}



















