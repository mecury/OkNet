package com.mecury.okhttplibrary;


import com.mecury.okhttplibrary.internal.Dns;
import com.mecury.okhttplibrary.internal.Internal;
import com.mecury.okhttplibrary.internal.Util;
import com.mecury.okhttplibrary.internal.cache.InternalCache;
import com.mecury.okhttplibrary.internal.connection.RealConnection;
import com.mecury.okhttplibrary.internal.connection.RouteDatabase;
import com.mecury.okhttplibrary.internal.connection.StreamAllocation;
import com.mecury.okhttplibrary.internal.platform.Platform;
import com.mecury.okhttplibrary.internal.tls.CertificateChainCleaner;
import com.mecury.okhttplibrary.internal.tls.OkHostnameVerifier;

import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

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

    //传输层版本和连接协议
    private static final List<ConnectionSpec> DEFAULT_CONNECTION_SPECS = Util.immutableList(
            ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT);

    // TODO: 2016/8/22
    static {
        Internal.instance = new Internal() {
            @Override public void addLenient(Headers.Builder builder, String line) {
                builder.addLenient(line);
            }

            @Override public void addLenient(Headers.Builder builder, String name, String value) {
                builder.addLenient(name, value);
            }

            @Override public void setCache(OkHttpClient.Builder builder, InternalCache internalCache) {
                builder.setInternalCache(internalCache);
            }

            @Override public boolean connectionBecameIdle(
                    ConnectionPool pool, RealConnection connection) {
                return pool.connectionBecameIdle(connection);
            }

            @Override public RealConnection get(
                    ConnectionPool pool, Address address, StreamAllocation streamAllocation) {
                return pool.get(address, streamAllocation);
            }

            @Override public void put(ConnectionPool pool, RealConnection connection) {
                pool.put(connection);
            }

            @Override public RouteDatabase routeDatabase(ConnectionPool connectionPool) {
                return connectionPool.routeDatabase;
            }

            @Override public StreamAllocation callEngineGetStreamAllocation(Call call) {
                return ((RealCall) call).streamAllocation();
            }

            @Override
            public void apply(ConnectionSpec tlsConfiguration, SSLSocket sslSocket, boolean isFallback) {
                tlsConfiguration.apply(sslSocket, isFallback);
            }

            @Override public HttpUrl getHttpUrlChecked(String url)
                    throws MalformedURLException, UnknownHostException {
                return HttpUrl.getChecked(url);
            }

            @Override public void setCallWebSocket(Call call) {
                ((RealCall) call).setForWebSocket();
            }
        };
    }

    final Dispatcher dispatcher;  //分发器
    final Proxy proxy;  //代理
    final List<Protocol> protocols; //协议
    final List<ConnectionSpec> connectionSpecs; //传输层版本和连接协议
    final List<Interceptor> interceptors; //拦截器
    final List<Interceptor> networkInterceptors; //网络拦截器
    final ProxySelector proxySelector; //代理选择
    final CookieJar cookieJar; //cookie
    final Cache cache; //缓存
    final InternalCache internalCache;  //内部缓存
    final SocketFactory socketFactory;  //socket 工厂
    final SSLSocketFactory sslSocketFactory; //安全套接层socket 工厂
    final CertificateChainCleaner certificateChainCleaner; // 置验证用于确认响应证书 适用 HTTPS 请求连接的主机名。
    final HostnameVerifier hostnameVerifier;    //  主机名字确认
    final CertificatePinner certificatePinner;  //  证书链
    final Authenticator proxyAuthenticator;     //代理身份验证
    final Authenticator authenticator;      // 本地身份验证
    final ConnectionPool connectionPool;    //连接池,复用连接
    final Dns dns;  //域名
    final boolean followSslRedirects;  //重定向
    final boolean followRedirects;
    final boolean retryOnConnectionFailure; //重试连接失败
    final int connectTimeout;    //连接超时
    final int readTimeout; //read 超时
    final int writeTimeout; //write 超时

    public OkHttpClient() {
        this(new Builder());
    }

    private OkHttpClient(Builder builder) {
        this.dispatcher = builder.dispatcher;
        this.proxy = builder.proxy;
        this.protocols = builder.protocols;
        this.connectionSpecs = builder.connectionSpecs;
        this.interceptors = Util.immutableList(builder.interceptors);
        this.networkInterceptors = Util.immutableList(builder.networkInterceptors);
        this.proxySelector = builder.proxySelector;
        this.cookieJar = builder.cookieJar;
        this.cache = builder.cache;
        this.internalCache = builder.internalCache;
        this.socketFactory = builder.socketFactory;

        boolean isTLS = false;
        for (ConnectionSpec spec : connectionSpecs) {
            isTLS = isTLS || spec.isTls();
        }

        if (builder.sslSocketFactory != null || !isTLS) {
            this.sslSocketFactory = builder.sslSocketFactory;
            this.certificateChainCleaner = builder.certificateChainCleaner;
        } else {
            X509TrustManager trustManager = systemDefaultTrustManager();
            this.sslSocketFactory = systemDefaultSslSocketFactory(trustManager);
            this.certificateChainCleaner = CertificateChainCleaner.get(trustManager);
        }

        this.hostnameVerifier = builder.hostnameVerifier;
        this.certificatePinner = builder.certificatePinner.withCertificateChainCleaner(
                certificateChainCleaner);
        this.proxyAuthenticator = builder.proxyAuthenticator;
        this.authenticator = builder.authenticator;
        this.connectionPool = builder.connectionPool;
        this.dns = builder.dns;
        this.followSslRedirects = builder.followSslRedirects;
        this.followRedirects = builder.followRedirects;
        this.retryOnConnectionFailure = builder.retryOnConnectionFailure;
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.writeTimeout = builder.writeTimeout;
    }

    private X509TrustManager systemDefaultTrustManager() {
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
                throw new IllegalStateException("Unexpected default trust managers:"
                        + Arrays.toString(trustManagers));
            }
            return (X509TrustManager) trustManagers[0];
        } catch (GeneralSecurityException e) {
            throw new AssertionError(); // The system has no TLS. Just give up.
        }
    }

    private SSLSocketFactory systemDefaultSslSocketFactory(X509TrustManager trustManager) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] { trustManager }, null);
            return sslContext.getSocketFactory();
        } catch (GeneralSecurityException e) {
            throw new AssertionError(); // The system has no TLS. Just give up.
        }
    }

    /** Default connect timeout (in milliseconds). */
    public int connectTimeoutMillis() {
        return connectTimeout;
    }

    /** Default read timeout (in milliseconds). */
    public int readTimeoutMillis() {
        return readTimeout;
    }

    /** Default write timeout (in milliseconds). */
    public int writeTimeoutMillis() {
        return writeTimeout;
    }

    public Proxy proxy() {
        return proxy;
    }

    public ProxySelector proxySelector() {
        return proxySelector;
    }

    public CookieJar cookieJar() {
        return cookieJar;
    }

    public Cache cache() {
        return cache;
    }

    InternalCache internalCache() {
        return cache != null ? cache.internalCache : internalCache;
    }

    public Dns dns() {
        return dns;
    }

    public SocketFactory socketFactory() {
        return socketFactory;
    }

    public SSLSocketFactory sslSocketFactory() {
        return sslSocketFactory;
    }

    public HostnameVerifier hostnameVerifier() {
        return hostnameVerifier;
    }

    public CertificatePinner certificatePinner() {
        return certificatePinner;
    }

    public Authenticator authenticator() {
        return authenticator;
    }

    public Authenticator proxyAuthenticator() {
        return proxyAuthenticator;
    }

    public ConnectionPool connectionPool() {
        return connectionPool;
    }

    public boolean followSslRedirects() {
        return followSslRedirects;
    }

    public boolean followRedirects() {
        return followRedirects;
    }

    public boolean retryOnConnectionFailure() {
        return retryOnConnectionFailure;
    }

    public Dispatcher dispatcher() {
        return dispatcher;
    }

    public List<Protocol> protocols() {
        return protocols;
    }

    public List<ConnectionSpec> connectionSpecs() {
        return connectionSpecs;
    }

    /**
     * Returns an immutable list of interceptors that observe the full span of each call: from before
     * the connection is established (if any) until after the response source is selected (either the
     * origin server, cache, or both).
     * 返回一个不可变的拦截器，观察每一个call的在建立连接（如果有）后才响应资源的选择（无论是原始服务器，缓存，或者两者都有）
     */
    public List<Interceptor> interceptors() {
        return interceptors;
    }

    /**
     * Returns an immutable list of interceptors that observe a single network request and response.
     * These interceptors must call {@link Interceptor.Chain#proceed} exactly once: it is an error for
     * a network interceptor to short-circuit or repeat a network request.
     *返回一个不可变的拦截器，观察单一的网络request和response。这些拦截器必须调用一次：这是一个错误，一个网络
     * 拦截器短路或重复进行网络request
     */
    public List<Interceptor> networkInterceptors() {
        return networkInterceptors;
    }

    /**
     * Prepares the {@code request} to be executed at some point in the future.
     * 准备将要被执行的request
     */
    @Override public Call newCall(Request request) {
        return new RealCall(this, request);
    }

    public Builder newBuilder() {
        return new Builder(this);
    }

    public static final class Builder {
        Dispatcher dispatcher;
        Proxy proxy;
        List<Protocol> protocols;
        List<ConnectionSpec> connectionSpecs;
        final List<Interceptor> interceptors = new ArrayList<>();
        final List<Interceptor> networkInterceptors = new ArrayList<>();
        ProxySelector proxySelector;
        CookieJar cookieJar;
        Cache cache;
        InternalCache internalCache;
        SocketFactory socketFactory;
        SSLSocketFactory sslSocketFactory;
        CertificateChainCleaner certificateChainCleaner;
        HostnameVerifier hostnameVerifier;
        CertificatePinner certificatePinner;
        Authenticator proxyAuthenticator;
        Authenticator authenticator;
        ConnectionPool connectionPool;
        Dns dns;
        boolean followSslRedirects;
        boolean followRedirects;
        boolean retryOnConnectionFailure;
        int connectTimeout;
        int readTimeout;
        int writeTimeout;

        public Builder() {
            dispatcher = new Dispatcher();
            protocols = DEFAULT_PROTOCOLS;
            connectionSpecs = DEFAULT_CONNECTION_SPECS;
            proxySelector = ProxySelector.getDefault();
            cookieJar = CookieJar.NO_COOKIES;
            socketFactory = SocketFactory.getDefault();
            hostnameVerifier = OkHostnameVerifier.INSTANCE;
            certificatePinner = CertificatePinner.DEFAULT;
            proxyAuthenticator = Authenticator.NONE;
            authenticator = Authenticator.NONE;
            connectionPool = new ConnectionPool();
            dns = Dns.SYSTEM;
            followSslRedirects = true;
            followRedirects = true;
            retryOnConnectionFailure = true;
            connectTimeout = 10_000;
            readTimeout = 10_000;
            writeTimeout = 10_000;
        }

        Builder(OkHttpClient okHttpClient) {
            this.dispatcher = okHttpClient.dispatcher;
            this.proxy = okHttpClient.proxy;
            this.protocols = okHttpClient.protocols;
            this.connectionSpecs = okHttpClient.connectionSpecs;
            this.interceptors.addAll(okHttpClient.interceptors);
            this.networkInterceptors.addAll(okHttpClient.networkInterceptors);
            this.proxySelector = okHttpClient.proxySelector;
            this.cookieJar = okHttpClient.cookieJar;
            this.internalCache = okHttpClient.internalCache;
            this.cache = okHttpClient.cache;
            this.socketFactory = okHttpClient.socketFactory;
            this.sslSocketFactory = okHttpClient.sslSocketFactory;
            this.certificateChainCleaner = okHttpClient.certificateChainCleaner;
            this.hostnameVerifier = okHttpClient.hostnameVerifier;
            this.certificatePinner = okHttpClient.certificatePinner;
            this.proxyAuthenticator = okHttpClient.proxyAuthenticator;
            this.authenticator = okHttpClient.authenticator;
            this.connectionPool = okHttpClient.connectionPool;
            this.dns = okHttpClient.dns;
            this.followSslRedirects = okHttpClient.followSslRedirects;
            this.followRedirects = okHttpClient.followRedirects;
            this.retryOnConnectionFailure = okHttpClient.retryOnConnectionFailure;
            this.connectTimeout = okHttpClient.connectTimeout;
            this.readTimeout = okHttpClient.readTimeout;
            this.writeTimeout = okHttpClient.writeTimeout;
        }

        /**
         * Sets the default connect timeout for new connections. A value of 0 means no timeout,
         * otherwise values must be between 1 and {@link Integer#MAX_VALUE} when converted to
         * milliseconds.
         * 为新的连接设置默认的连接超时
         */
        public Builder connectTimeout(long timeout, TimeUnit unit) {
            if (timeout < 0) throw new IllegalArgumentException("timeout < 0");
            if (unit == null) throw new NullPointerException("unit == null");
            long millis = unit.toMillis(timeout);
            if (millis > Integer.MAX_VALUE) throw new IllegalArgumentException("Timeout too large.");
            if (millis == 0 && timeout > 0) throw new IllegalArgumentException("Timeout too small.");
            connectTimeout = (int) millis;
            return this;
        }

        /**
         * Sets the default read timeout for new connections. A value of 0 means no timeout, otherwise
         * values must be between 1 and {@link Integer#MAX_VALUE} when converted to milliseconds.
         * 为新的连接设置默认的read连接超时
         */
        public Builder readTimeout(long timeout, TimeUnit unit) {
            if (timeout < 0) throw new IllegalArgumentException("timeout < 0");
            if (unit == null) throw new NullPointerException("unit == null");
            long millis = unit.toMillis(timeout);
            if (millis > Integer.MAX_VALUE) throw new IllegalArgumentException("Timeout too large.");
            if (millis == 0 && timeout > 0) throw new IllegalArgumentException("Timeout too small.");
            readTimeout = (int) millis;
            return this;
        }

        /**
         * Sets the default write timeout for new connections. A value of 0 means no timeout, otherwise
         * values must be between 1 and {@link Integer#MAX_VALUE} when converted to milliseconds.
         * 设置write连接超时
         */
        public Builder writeTimeout(long timeout, TimeUnit unit) {
            if (timeout < 0) throw new IllegalArgumentException("timeout < 0");
            if (unit == null) throw new NullPointerException("unit == null");
            long millis = unit.toMillis(timeout);
            if (millis > Integer.MAX_VALUE) throw new IllegalArgumentException("Timeout too large.");
            if (millis == 0 && timeout > 0) throw new IllegalArgumentException("Timeout too small.");
            writeTimeout = (int) millis;
            return this;
        }

        /**
         * Sets the HTTP proxy that will be used by connections created by this client. This takes
         * precedence over {@link #proxySelector}, which is only honored when this proxy is null (which
         * it is by default). To disable proxy use completely, call {@code setProxy(Proxy.NO_PROXY)}.
         * 为client创建的connection设置 HTTP 代理。这个优先级高于proxySelector，当proxy为null是使用后者（他是默认的）
         * 完全禁用代理时：call设为 Proxy.NO_PROXY.
         */
        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        /**
         * Sets the proxy selection policy to be used if no {@link #proxy proxy} is specified
         * explicitly. The proxy selector may return multiple proxies; in that case they will be tried
         * in sequence until a successful connection is established.
         * 设置代理选择策略，如果proxy没有被明确指定。这个代理选择器可能会返回多个代理：在这种情况，他们将会被
         * 依次尝试，直到有一个成功的连接建立
         *
         * <p>If unset, the {@link ProxySelector#getDefault() system-wide default} proxy selector will
         * be used.
         */
        public Builder proxySelector(ProxySelector proxySelector) {
            this.proxySelector = proxySelector;
            return this;
        }

        /**
         * Sets the handler that can accept cookies from incoming HTTP responses and provides cookies to
         * outgoing HTTP requests.
         * 设置处理程序：能够接受传入的HTTP response和传出的HTTP request 提供cookie
         *
         * <p>If unset, {@linkplain CookieJar#NO_COOKIES no cookies} will be accepted nor provided.
         */
        public Builder cookieJar(CookieJar cookieJar) {
            if (cookieJar == null) throw new NullPointerException("cookieJar == null");
            this.cookieJar = cookieJar;
            return this;
        }

        /** Sets the response cache to be used to read and write cached responses. */
        void setInternalCache(InternalCache internalCache) {
            this.internalCache = internalCache;
            this.cache = null;
        }

        public Builder cache(Cache cache) {
            this.cache = cache;
            this.internalCache = null;
            return this;
        }

        /**
         * Sets the DNS service used to lookup IP addresses for hostnames.
         * 设置DNS服务，用于查找IP地址的主机名
         *
         * <p>If unset, the {@link Dns#SYSTEM system-wide default} DNS will be used.
         */
        public Builder dns(Dns dns) {
            if (dns == null) throw new NullPointerException("dns == null");
            this.dns = dns;
            return this;
        }

        /**
         * Sets the socket factory used to create connections. OkHttp only uses the parameterless {@link
         * SocketFactory#createSocket() createSocket()} method to create unconnected sockets. Overriding
         * this method, e. g., allows the socket to be bound to a specific local address.
         * 设置socket工厂用于创建连接/OKHttp仅使用无参数的createSocket()方法来创建无连接的sockets。
         * 重写这个方法，允许socket绑定到一个特定的本地地址
         *
         * <p>If unset, the {@link SocketFactory#getDefault() system-wide default} socket factory will
         * be used.
         */
        public Builder socketFactory(SocketFactory socketFactory) {
            if (socketFactory == null) throw new NullPointerException("socketFactory == null");
            this.socketFactory = socketFactory;
            return this;
        }

        /**
         * Sets the socket factory used to secure HTTPS connections. If unset, the system default will
         * be used.
         * 设置socket factory 用于创建安全的HTTPS 连接。
         *
         * @deprecated {@code SSLSocketFactory} does not expose(暴露) its {@link X509TrustManager}, which is
         *     a field that OkHttp needs to build a clean certificate chain. This method instead must
         *     use reflection to extract the trust manager. Applications should prefer to call {@link
         *     #sslSocketFactory(SSLSocketFactory, X509TrustManager)}, which avoids such reflection.
         */
        public Builder sslSocketFactory(SSLSocketFactory sslSocketFactory) {
            if (sslSocketFactory == null) throw new NullPointerException("sslSocketFactory == null");
            X509TrustManager trustManager = Platform.get().trustManager(sslSocketFactory);
            if (trustManager == null) {
                throw new IllegalStateException("Unable to extract the trust manager on " + Platform.get()
                        + ", sslSocketFactory is " + sslSocketFactory.getClass());
            }
            this.sslSocketFactory = sslSocketFactory;
            this.certificateChainCleaner = CertificateChainCleaner.get(trustManager);
            return this;
        }

        /**
         * Sets the socket factory and trust manager used to secure HTTPS connections. If unset, the
         * system defaults will be used.
         *
         * <p>Most applications should not call this method, and instead use the system defaults. Those
         * classes include special optimizations that can be lost if the implementations are decorated.
         *大多数应用程序不应该调用此方法，而是使用系统默认值。这些类包括特殊的优化，它可以丢失，如果实现是装饰过的
         *
         * <p>If necessary, you can create and configure the defaults yourself with the following code:
         * 如果必要，你可以创建和设置默认的值使用下面的代码
         *
         * <pre>   {@code
         *
         *   TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
         *       TrustManagerFactory.getDefaultAlgorithm());
         *   trustManagerFactory.init((KeyStore) null);
         *   TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
         *   if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
         *     throw new IllegalStateException("Unexpected default trust managers:"
         *         + Arrays.toString(trustManagers));
         *   }
         *   X509TrustManager trustManager = (X509TrustManager) trustManagers[0];
         *
         *   SSLContext sslContext = SSLContext.getInstance("TLS");
         *   sslContext.init(null, new TrustManager[] { trustManager }, null);
         *   SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
         *
         *   OkHttpClient client = new OkHttpClient.Builder()
         *       .sslSocketFactory(sslSocketFactory, trustManager);
         *       .build();
         * }</pre>
         */
        public Builder sslSocketFactory(
                SSLSocketFactory sslSocketFactory, X509TrustManager trustManager) {
            if (sslSocketFactory == null) throw new NullPointerException("sslSocketFactory == null");
            if (trustManager == null) throw new NullPointerException("trustManager == null");
            this.sslSocketFactory = sslSocketFactory;
            this.certificateChainCleaner = CertificateChainCleaner.get(trustManager);
            return this;
        }

        /**
         * Sets the verifier used to confirm that response certificates apply to requested hostnames for
         * HTTPS connections.
         * 设置验证用于确认响应证书 适用 HTTPS 请求连接的主机名。
         *
         * <p>If unset, a default hostname verifier will be used.
         */
        public Builder hostnameVerifier(HostnameVerifier hostnameVerifier) {
            if (hostnameVerifier == null) throw new NullPointerException("hostnameVerifier == null");
            this.hostnameVerifier = hostnameVerifier;
            return this;
        }

        /**
         * Sets the certificate pinner that constrains which certificates are trusted. By default HTTPS
         * connections rely on only the {@link #sslSocketFactory SSL socket factory} to establish trust.
         * Pinning certificates avoids the need to trust certificate authorities.
         *  设置证书pinner 限制受信任的证书。默认的HTTPS连接仅依赖 sslSocketFactory 建立信任。
         *  证书pinner 避免了需要信任证书颁发机构
         */
        public Builder certificatePinner(CertificatePinner certificatePinner) {
            if (certificatePinner == null) throw new NullPointerException("certificatePinner == null");
            this.certificatePinner = certificatePinner;
            return this;
        }

        /**
         * Sets the authenticator used to respond to challenges from origin servers. Use {@link
         * #proxyAuthenticator} to set the authenticator for proxy servers.
         * 设置用于响应来自源服务器的request的身份验证。使用proxyauthenticator设置身份验证代理服务器。
         *
         * <p>If unset, the {@linkplain Authenticator#NONE no authentication will be attempted}.
         */
        public Builder authenticator(Authenticator authenticator) {
            if (authenticator == null) throw new NullPointerException("authenticator == null");
            this.authenticator = authenticator;
            return this;
        }

        /**
         * Sets the authenticator used to respond to challenges from proxy servers. Use {@link
         * #authenticator} to set the authenticator for origin servers.
         * 设置用于代理server的身份验证。使用authenticator去设置源server的身份验证
         *
         * <p>If unset, the {@linkplain Authenticator#NONE no authentication will be attempted}.
         */
        public Builder proxyAuthenticator(Authenticator proxyAuthenticator) {
            if (proxyAuthenticator == null) throw new NullPointerException("proxyAuthenticator == null");
            this.proxyAuthenticator = proxyAuthenticator;
            return this;
        }

        /**
         * Sets the connection pool used to recycle HTTP and HTTPS connections.
         * 设置连接池用于回收HTTP和HTTPS的连接
         *
         * <p>If unset, a new connection pool will be used.
         */
        public Builder connectionPool(ConnectionPool connectionPool) {
            if (connectionPool == null) throw new NullPointerException("connectionPool == null");
            this.connectionPool = connectionPool;
            return this;
        }

        /**
         * Configure this client to follow redirects from HTTPS to HTTP and from HTTP to HTTPS.
         * 配置client遵循重定向的HTTP到HTTPS和 HTTP到HTTPS。
         *
         *
         * <p>If unset, protocol redirects will be followed. This is different than the built-in {@code
         * HttpURLConnection}'s default.
         */
        public Builder followSslRedirects(boolean followProtocolRedirects) {
            this.followSslRedirects = followProtocolRedirects;
            return this;
        }

        /** Configure this client to follow redirects. If unset, redirects be followed. */
        public Builder followRedirects(boolean followRedirects) {
            this.followRedirects = followRedirects;
            return this;
        }

        /**
         * Configure this client to retry or not when a connectivity problem is encountered. By default,
         * this client silently recovers from the following problems:
         * 配置client 重试 当一个连接活动发生错误。client默认会静默的恢复 若出现下面的问题：
         *
         * <ul>
         *   <li><strong>Unreachable IP addresses.</strong> If the URL's host has multiple IP addresses,
         *       failure to reach any individual IP address doesn't fail the overall request. This can
         *       increase availability of multi-homed services.
         *       不可达的IP地址。如果URL的主机有多个IP地址，所有的IP地址都不可达，整个request失败了，这可以增加多服务器的可用性
         *   <li><strong>Stale pooled connections.</strong> The {@link ConnectionPool} reuses sockets
         *       to decrease request latency, but these connections will occasionally time out.
         *       不新鲜的连接。连接池重复使用socket去降低请求延迟，但是这些连接偶尔会超时
         *   <li><strong>Unreachable proxy servers.</strong> A {@link ProxySelector} can be used to
         *       attempt multiple proxy servers in sequence, eventually falling back to a direct
         *       connection.
         *       不可达的代理服务器。代理服务器选择器能被用来尝试多个代理服务器，最终回退到一个直接的connection
         * </ul>
         *
         * Set this to false to avoid retrying requests when doing so is destructive. In this case the
         * calling application should do its own recovery of connectivity failures.
         * 设置为false,以避免重试请求时这样做是毁灭性的。在这种情况下,调用应用程序应该做自己的恢复连接失败。
         */
        public Builder retryOnConnectionFailure(boolean retryOnConnectionFailure) {
            this.retryOnConnectionFailure = retryOnConnectionFailure;
            return this;
        }

        /**
         * Sets the dispatcher used to set policy and execute asynchronous requests. Must not be null.
         * 设置分发器来设置策略和执行异步的request。不能为null
         */
        public Builder dispatcher(Dispatcher dispatcher) {
            if (dispatcher == null) throw new IllegalArgumentException("dispatcher == null");
            this.dispatcher = dispatcher;
            return this;
        }

        /**
         * Configure the protocols used by this client to communicate with remote servers. By default
         * this client will prefer the most efficient transport available, falling back to more
         * ubiquitous protocols. Applications should only call this method to avoid specific
         * compatibility problems, such as web servers that behave incorrectly when HTTP/2 is enabled.
         * 配置client与server交流的协议。默认，client会选择最有效的存在的传输协议，回调更多的协议。应用应该调用
         * 该方法去避免特定的 兼容性问题，像web 服务器行为错误当使用HTTP/2时
         *
         * <p>The following protocols are currently supported:下面是目前支持的协议
         *
         * <ul>
         *     <li><a href="http://www.w3.org/Protocols/rfc2616/rfc2616.html">http/1.1</a>
         *     <li><a href="http://tools.ietf.org/html/draft-ietf-httpbis-http2-17">h2</a>
         * </ul>
         *
         * <p><strong>This is an evolving set.</strong> Future releases include support for transitional
         * protocols. The http/1.1 transport will never be dropped.
         *
         * <p>If multiple protocols are specified, <a
         * href="http://tools.ietf.org/html/draft-ietf-tls-applayerprotoneg">ALPN</a> will be used to
         * negotiate a transport.
         *
         * <p>{@link Protocol#HTTP_1_0} is not supported in this set. Requests are initiated with {@code
         * HTTP/1.1} only. If the server responds with {@code HTTP/1.0}, that will be exposed by {@link
         * Response#protocol()}.
         *
         * @param protocols the protocols to use, in order of preference. The list must contain {@link
         * Protocol#HTTP_1_1}. It must not contain null or {@link Protocol#HTTP_1_0}.
         */
        public Builder protocols(List<Protocol> protocols) {
            // Create a private copy of the list.
            protocols = new ArrayList<>(protocols);

            // Validate that the list has everything we require and nothing we forbid.
            if (!protocols.contains(Protocol.HTTP_1_1)) {
                throw new IllegalArgumentException("protocols doesn't contain http/1.1: " + protocols);
            }
            if (protocols.contains(Protocol.HTTP_1_0)) {
                throw new IllegalArgumentException("protocols must not contain http/1.0: " + protocols);
            }
            if (protocols.contains(null)) {
                throw new IllegalArgumentException("protocols must not contain null");
            }

            // Remove protocols that we no longer support.
            if (protocols.contains(Protocol.SPDY_3)) {
                protocols.remove(Protocol.SPDY_3);
            }

            // Assign as an unmodifiable list. This is effectively immutable.
            this.protocols = Collections.unmodifiableList(protocols);
            return this;
        }

        public Builder connectionSpecs(List<ConnectionSpec> connectionSpecs) {
            this.connectionSpecs = Util.immutableList(connectionSpecs);
            return this;
        }

        /**
         * Returns a modifiable list of interceptors that observe the full span of each call: from
         * before the connection is established (if any) until after the response source is selected
         * (either the origin server, cache, or both).
         * 返回 观察每个call的全部跨度的拦截器：在建立连接之前（如果有的话），直到响应源被选中（原始服务器，缓存或者两者都有）
         */
        public List<Interceptor> interceptors() {
            return interceptors;
        }

        public Builder addInterceptor(Interceptor interceptor) {
            interceptors.add(interceptor);
            return this;
        }

        /**
         * Returns a modifiable list of interceptors that observe a single network request and response.
         * These interceptors must call {@link Interceptor.Chain#proceed} exactly once: it is an error
         * for a network interceptor to short-circuit or repeat a network request.
         */
        public List<Interceptor> networkInterceptors() {
            return networkInterceptors;
        }

        public Builder addNetworkInterceptor(Interceptor interceptor) {
            networkInterceptors.add(interceptor);
            return this;
        }

        public OkHttpClient build() {
            return new OkHttpClient(this);
        }
    }
}
















