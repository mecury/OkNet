package com.mecury.okhttplibrary;


import android.location.Address;
import android.net.Proxy;

import com.mecury.okhttplibrary.internal.Dns;
import com.mecury.okhttplibrary.internal.Internal;
import com.mecury.okhttplibrary.internal.Util;
import com.mecury.okhttplibrary.internal.cache.InternalCache;
import com.mecury.okhttplibrary.internal.connection.RealConnection;
import com.mecury.okhttplibrary.internal.connection.RouteDatabase;
import com.mecury.okhttplibrary.internal.connection.StreamAllocation;
import com.mecury.okhttplibrary.internal.tls.CertificateChainCleaner;

import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.ProxySelector;
import java.net.UnknownHostException;
import java.util.List;

import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Factory for {@linkplain Call calls}, which can be used to send HTTP requests and read their
 * responses.
 *
 * <h3>OkHttpClients should be shared</h3>
 *
 * <p>OkHttp performs best when you create a single {@code OkHttpClient} instance and reuse it for
 * all of your HTTP calls. This is because each client holds its own connection pool and thread
 * pools. Reusing connections and threads reduces latency and saves memory. Conversely, creating a
 * client for each request wastes resources on idle pools.
 *
 * <p>Use {@code new OkHttpClient()} to create a shared instance with the default settings:
 * <pre>   {@code
 *
 *   // The singleton HTTP client.
 *   public final OkHttpClient client = new OkHttpClient();
 * }</pre>
 *
 * <p>Or use {@code new OkHttpClient.Builder()} to create a shared instance with custom settings:
 * <pre>   {@code
 *
 *   // The singleton HTTP client.
 *   public final OkHttpClient client = new OkHttpClient.Builder()
 *       .addInterceptor(new HttpLoggingInterceptor())
 *       .cache(new Cache(cacheDir, cacheSize))
 *       .build();
 * }</pre>
 *
 * <h3>Customize your client with newBuilder()</h3>
 *
 * <p>You can customize a shared OkHttpClient instance with {@link #newBuilder()}. This builds a
 * client that shares the same connection pool, thread pools, and configuration. Use the builder
 * methods to configure the derived client for a specific purpose.
 *
 * <p>This example shows a call with a short 500 millisecond timeout: <pre>   {@code
 *
 *   OkHttpClient eagerClient = client.newBuilder()
 *       .readTimeout(500, TimeUnit.MILLISECONDS)
 *       .build();
 *   Response response = eagerClient.newCall(request).execute();
 * }</pre>
 *
 * <h3>Shutdown isn't necessary</h3>
 *
 * <p>The threads and connections that are held will be released automatically if they remain idle.
 * But if you are writing a application that needs to aggressively release unused resources you may
 * do so.
 *
 * <p>Shutdown the dispatcher's executor service with {@link ExecutorService#shutdown shutdown()}.
 * This will also cause future calls to the client to be rejected. <pre>   {@code
 *
 *     client.dispatcher().executorService().shutdown();
 * }</pre>
 *
 * <p>Clear the connection pool with {@link ConnectionPool#evictAll() evictAll()}. Note that the
 * connection pool's daemon thread may not exit immediately. <pre>   {@code
 *
 *     client.connectionPool().evictAll();
 * }</pre>
 *
 * <p>If your client has a cache, call {@link Cache#close close()}. Note that it is an error to
 * create calls against a cache that is closed, and doing so will cause the call to crash.
 * <pre>   {@code
 *
 *     client.cache().close();
 * }</pre>
 *
 * <p>OkHttp also uses daemon threads for HTTP/2 connections. These will exit automatically if they
 * remain idle.
 */
public class OkHttpClient implements Cloneable, Call.Factory {

    //默认的连接协议：http/1.0, htp/1.1, spdy/3.1, h2
    private static final List<Protocol> DEFAULT_PROTOCOLS = Util.immutableList(
            Protocol.HTTP_2, Protocol.HTTP_1_1);

    // TODO: 2016/8/15 不懂
    private static final List<ConnectionSpec> DEFAULT_CONNECTION_SPECS = Util.immutableList(
            ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT);

    static {
        Internal.instance = new Internal() {

            @Override
            public void addLenient(Headers.Builder builder, String line) {
                builder.addLenient(line);
            }

            @Override
            public void addLenient(Headers.Builder builder, String name, String value) {
                builder.add(name, value);
            }

            @Override
            public void setCache(OkHttpClient.Builder builder, InternalCache internalCache) {
                builder.setInternalCache(internalCache);
            }



            @Override
            public RealConnection get(ConnectionPool pool, Address address, StreamAllocation streamAllocation) {
                return null;
            }

            @Override
            public void put(ConnectionPool pool, RealConnection connection) {

            }

            @Override
            public boolean connectionBecameIdle(ConnectionPool pool, RealConnection connection) {
                return false;
            }

            @Override
            public RouteDatabase routeDatabase(ConnectionPool connectionPool) {
                return null;
            }

            @Override
            public void apply(ConnectionSpec tlsConfiguration, SSLSocket sslSocket, boolean isFallback) {

            }

            @Override
            public HttpUrl getHttpUrlChecked(String url) throws MalformedURLException, UnknownHostException {
                return null;
            }

            @Override
            public void setCallWebSocket(Call call) {

            }

            @Override
            public StreamAllocation callEngineGetStreamAllocation(Call call) {
                return null;
            }
        };
    }

    final Dispatcher dispatcher;
    final Proxy proxy;
    final List<Protocol> protocols;
    final List<ConnectionSpec> connectionSpecs;
    final List<Interceptor> interceptor;
    final List<Interceptor> networkInterceptors;
    final ProxySelector proxySelector;
    final CookieJar cookieJar;
    final Cache cache;
    final InternalCache internalCache;
    final SocketFactory socketFactory;
    final SSLSocketFactory sslSocketFactory;
    final CertificateChainCleaner certificateChainCleaner;
    final HostnameVerifier hostnameVerifier;
    final CertificatePinner certificatePinner;
    final Authenticator proxyAuthenticator;
    final ConnectionPool connectionPool;
    final Dns dns;
    final boolean followSslRedirects;
    final boolean followRedirects;
    final boolean retryOnConnectionFailuer;
    final int connectionTimeOut;
    final int readTimeout;
    final int writeTimeout;


    @Override
    public Call newCall(Request request) {
        return null;
    }
}
















