package com.mecury.okhttplibrary.internal.http;

import com.mecury.okhttplibrary.Address;
import com.mecury.okhttplibrary.CertificatePinner;
import com.mecury.okhttplibrary.Connection;
import com.mecury.okhttplibrary.HttpUrl;
import com.mecury.okhttplibrary.Interceptor;
import com.mecury.okhttplibrary.OkHttpClient;
import com.mecury.okhttplibrary.Request;
import com.mecury.okhttplibrary.RequestBody;
import com.mecury.okhttplibrary.Response;
import com.mecury.okhttplibrary.Route;
import com.mecury.okhttplibrary.internal.connection.RouteException;
import com.mecury.okhttplibrary.internal.connection.StreamAllocation;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.HttpRetryException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.security.cert.CertificateException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocketFactory;

import static com.mecury.okhttplibrary.internal.Util.closeQuietly;
import static com.mecury.okhttplibrary.internal.http.StatusLine.HTTP_PERM_REDIRECT;
import static com.mecury.okhttplibrary.internal.http.StatusLine.HTTP_TEMP_REDIRECT;
import static java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT;
import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_MULT_CHOICE;
import static java.net.HttpURLConnection.HTTP_PROXY_AUTH;
import static java.net.HttpURLConnection.HTTP_SEE_OTHER;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

/**
 * This interceptor recovers from failures and follows redirects as necessary. It may throw an
 * {@link IOException} if the call was canceled.
 * 这个拦截器负责从失败中恢复并且必要时重定向。如果调用被取消，可能抛出I/O异常
 */
public final class RetryAndFollowUpInterceptor implements Interceptor{
    /**
     * How many redirects and auth challenges should we attempt? Chrome follows 21 redirects; Firefox,
     * curl, and wget follow 20; Safari follows 16; and HTTP/1.0 recommends 5.
     * 我们应该尝试多少次重定向和身份验证
     */
    private static final int MAX_FOLLOW_UPS = 20;

    private final OkHttpClient client;
    private StreamAllocation streamAllocation;
    private boolean forWebSocket;
    private volatile boolean canceled;

    public RetryAndFollowUpInterceptor(OkHttpClient client){
        this.client = client;
    }

    /**
     * Immediately closes the socket connection if it's currently held. Use this to interrupt an
     * in-flight request from any thread. It's the caller's responsibility to close the request body
     * and response body streams; otherwise resources may be leaked.
     * 立即关闭socket连接，使用这个去中断任何运行在线程中的request。关闭请求体和响应的流是调用者的责任。否则会资源泄漏
     *
     * <p>This method is safe to be called concurrently, but provides limited guarantees. If a
     * transport layer connection has been established (such as a HTTP/2 stream) that is terminated.
     * Otherwise if a socket connection is being established, that is terminated.
     */
    public void cancel() {
        canceled = true;
        StreamAllocation streamAllocation  = this.streamAllocation;
        if (streamAllocation!=null) streamAllocation.cancel();
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void setForWebSocket(boolean forWebSocket) {
        this.forWebSocket = forWebSocket;
    }

    public boolean isForWebSocket() {
        return forWebSocket;
    }

    public StreamAllocation streamAllocation() {
        return streamAllocation;
    }

    @Override public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        streamAllocation = new StreamAllocation(
                client.connectionPool(), createAddress(request.url()));

        int followUpCount = 0;
        Response priorResponse = null;
        while (true) {
            if (canceled) {
                streamAllocation.release();
                throw new IOException("Canceled");
            }

            Response response = null;
            boolean releaseConnection = true;
            try {
                response = ((RealInterceptorChain) chain).proceed(request, streamAllocation, null, null);
                releaseConnection = false;
            } catch (RouteException e) {
                // The attempt to connect via a route failed. The request will not have been sent.
                //通过路线连接失败，请求将不会再发送
                if (!recover(e.getLastConnectException(), true, request)) throw e.getLastConnectException();
                releaseConnection = false;
                continue;
            } catch (IOException e) {
                // An attempt to communicate with a server failed. The request may have been sent.
                // 与服务器尝试通信失败，请求不会再发送
                if (!recover(e, false, request)) throw e;
                releaseConnection = false;
                continue;
            } finally {
                // We're throwing an unchecked exception. Release any resources.
                //抛出未检查的异常，释放资源
                if (releaseConnection) {
                    streamAllocation.streamFailed(null);
                    streamAllocation.release();
                }
            }

            // Attach the prior response if it exists. Such responses never have a body.
            // 附加上先前存在的response。这样的response从来没有body
            // TODO: 2016/8/23 这里没赋值，岂不是一直为空？
            if (priorResponse != null) {
                response = response.newBuilder()
                        .priorResponse(priorResponse.newBuilder()
                                .body(null)
                                .build())
                        .build();
            }

            Request followUp = followUpRequest(response); //判断状态码

            if (followUp == null) {
                if (!forWebSocket) {
                    streamAllocation.release();
                }
                return response;
            }

            closeQuietly(response.body());

            if (++followUpCount > MAX_FOLLOW_UPS) {
                streamAllocation.release();
                throw new ProtocolException("Too many follow-up requests: " + followUpCount);
            }

            if (followUp.body() instanceof UnrepeatableRequestBody) {
                throw new HttpRetryException("Cannot retry streamed HTTP body", response.code());
            }

            if (!sameConnection(response, followUp.url())) {
                streamAllocation.release();
                streamAllocation = new StreamAllocation(
                        client.connectionPool(), createAddress(followUp.url()));
            } else if (streamAllocation.codec() != null) {
                throw new IllegalStateException("Closing the body of " + response
                        + " didn't close its backing stream. Bad interceptor?");
            }

            request = followUp;
            priorResponse = response;
        }
    }

    private Address createAddress(HttpUrl url) {
        SSLSocketFactory sslSocketFactory = null;
        HostnameVerifier hostnameVerifier = null;
        CertificatePinner certificatePinner = null;
        if (url.isHttps()) {
            sslSocketFactory = client.sslSocketFactory();
            hostnameVerifier = client.hostnameVerifier();
            certificatePinner = client.certificatePinner();
        }

        return new Address(url.host(), url.port(), client.dns(), client.socketFactory(),
                sslSocketFactory, hostnameVerifier, certificatePinner, client.proxyAuthenticator(),
                client.proxy(), client.protocols(), client.connectionSpecs(), client.proxySelector());
    }

    /**
     * Report and attempt to recover from a failure to communicate with a server. Returns true if
     * {@code e} is recoverable, or false if the failure is permanent. Requests with a body can only
     * be recovered if the body is buffered.
     * 报告 和 尝试从server交流失败中恢复。如果是可恢复的返回true，返回false如果失败是永久的。带有body的
     * request能被恢复如果body被缓存
     */
    private boolean recover(IOException e, boolean routeException, Request userRequest) {
        streamAllocation.streamFailed(e);

        // The application layer has forbidden retries.应用程序禁止重试
        if (!client.retryOnConnectionFailure()) return false;

        // We can't send the request body again.//不能再次发送请求体
        if (!routeException && userRequest.body() instanceof UnrepeatableRequestBody) return false;

        // This exception is fatal.异常是致命的
        if (!isRecoverable(e, routeException)) return false;

        // No more routes to attempt. 没有更多的路由线路去尝试
        if (!streamAllocation.hasMoreRoutes()) return false;

        // For failure recovery, use the same route selector with a new connection.
        //对于失败恢复，使用相同的路由选择器和新的connection
        return true;
    }

    private boolean isRecoverable(IOException e, boolean routeException) {
        // If there was a protocol problem, don't recover.
        //如果是协议问题不恢复
        if (e instanceof ProtocolException) {
            return false;
        }

        // If there was an interruption don't recover, but if there was a timeout connecting to a route
        // we should try the next route (if there is one).
        // 如果是一个中断，不恢复，但是如果是一个线路超时连接，我们应该尝试另一条路线
        if (e instanceof InterruptedIOException) {
            return e instanceof SocketTimeoutException && routeException;
        }

        // Look for known client-side or negotiation errors that are unlikely to be fixed by trying
        // again with a different route.
        // 查看已知的客户端和协商错误是否能被修复通过尝试一条不同的路线
        if (e instanceof SSLHandshakeException) {
            // If the problem was a CertificateException from the X509TrustManager,
            // do not retry.
            //如果问题是证书错误，不再尝试
            if (e.getCause() instanceof CertificateException) {
                return false;
            }
        }
        if (e instanceof SSLPeerUnverifiedException) {
            // e.g. a certificate pinning error.
            return false;
        }

        // An example of one we might want to retry with a different route is a problem connecting to a
        // proxy and would manifest as a standard IOException. Unless it is one we know we should not
        // retry, we return true and try a new route.
        return true;
    }

    /**
     * Figures out the HTTP request to make in response to receiving {@code userResponse}. This will
     * either add authentication headers, follow redirects or handle a client request timeout. If a
     * follow-up is either unnecessary or not applicable, this returns null.
     * 找出HTTP request 接收 user Response 做出的相应，这将正价两种认证头，跟着重定向 或者 处理client请求超时
     * 如果后续是不必要的或不适用，返回null
     */
    private Request followUpRequest(Response userResponse) throws IOException {
        if (userResponse == null) throw new IllegalStateException();
        Connection connection = streamAllocation.connection();
        Route route = connection != null
                ? connection.route()
                : null;
        int responseCode = userResponse.code();

        final String method = userResponse.request().method();
        switch (responseCode) {
            case HTTP_PROXY_AUTH:
                Proxy selectedProxy = route != null
                        ? route.proxy()
                        : client.proxy();
                if (selectedProxy.type() != Proxy.Type.HTTP) {
                    throw new ProtocolException("Received HTTP_PROXY_AUTH (407) code while not using proxy");
                }
                return client.proxyAuthenticator().authenticate(route, userResponse);

            case HTTP_UNAUTHORIZED:
                return client.authenticator().authenticate(route, userResponse);

            case HTTP_PERM_REDIRECT:
            case HTTP_TEMP_REDIRECT:
                // "If the 307 or 308 status code is received in response to a request other than GET
                // or HEAD, the user agent MUST NOT automatically redirect the request"
                if (!method.equals("GET") && !method.equals("HEAD")) {
                    return null;
                }
                // fall-through
            case HTTP_MULT_CHOICE:
            case HTTP_MOVED_PERM:
            case HTTP_MOVED_TEMP:
            case HTTP_SEE_OTHER:
                // Does the client allow redirects?
                //client 允许重定向？
                if (!client.followRedirects()) return null;

                String location = userResponse.header("Location");
                if (location == null) return null;
                HttpUrl url = userResponse.request().url().resolve(location);

                // Don't follow redirects to unsupported protocols.
                if (url == null) return null;

                // If configured, don't follow redirects between SSL and non-SSL.
                boolean sameScheme = url.scheme().equals(userResponse.request().url().scheme());
                if (!sameScheme && !client.followSslRedirects()) return null;

                // Most redirects don't include a request body.
                Request.Builder requestBuilder = userResponse.request().newBuilder();
                if (HttpMethod.permitsRequestBody(method)) {
                    final boolean maintainBody = HttpMethod.redirectsWithBody(method);
                    if (HttpMethod.redirectsToGet(method)) {
                        requestBuilder.method("GET", null);
                    } else {
                        RequestBody requestBody = maintainBody ? userResponse.request().body() : null;
                        requestBuilder.method(method, requestBody);
                    }
                    if (!maintainBody) {
                        requestBuilder.removeHeader("Transfer-Encoding");
                        requestBuilder.removeHeader("Content-Length");
                        requestBuilder.removeHeader("Content-Type");
                    }
                }

                // When redirecting across hosts, drop all authentication headers. This
                // is potentially annoying to the application layer since they have no
                // way to retain them.
                if (!sameConnection(userResponse, url)) {
                    requestBuilder.removeHeader("Authorization");
                }

                return requestBuilder.url(url).build();

            case HTTP_CLIENT_TIMEOUT:
                // 408's are rare in practice, but some servers like HAProxy use this response code. The
                // spec says that we may repeat the request without modifications. Modern browsers also
                // repeat the request (even non-idempotent ones.)
                if (userResponse.request().body() instanceof UnrepeatableRequestBody) {
                    return null;
                }

                return userResponse.request();

            default:
                return null;
        }
    }

    /**
     * Returns true if an HTTP request for {@code followUp} can reuse the connection used by this
     * engine.
     * 如果下一个request能够重复利用当前的连接
     */
    private boolean sameConnection(Response response, HttpUrl followUp) {
        HttpUrl url = response.request().url();
        return url.host().equals(followUp.host())
                && url.port() == followUp.port()
                && url.scheme().equals(followUp.scheme());
    }
}






















