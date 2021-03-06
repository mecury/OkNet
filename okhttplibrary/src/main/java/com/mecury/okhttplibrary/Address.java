package com.mecury.okhttplibrary;

import com.mecury.okhttplibrary.internal.Dns;
import com.mecury.okhttplibrary.internal.Util;

import java.net.Proxy;
import java.net.ProxySelector;
import java.util.List;

import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;

import static com.mecury.okhttplibrary.internal.Util.equal;

/**
 * A specification(规范) for a connection to an origin server. For simple connections, this is the
 * server's hostname and port. If an explicit(显式的） proxy is requested (or {@linkplain Proxy#NO_PROXY no
 * proxy} is explicitly requested), this also includes that proxy information. For secure
 * connections the address also includes the SSL socket factory, hostname verifier, and certificate(证书）
 * pinner.
 * 一种连接原始服务器的规范。对于简单的连接，包含server的主机名和端口。如果一个显式的server被请求，它也包含这个
 * 服务器的信息。对于安全的连接，这个address必须包含the SSL socket factory, hostname verifier, and certificate pinner.
 * <p>
 * <p>HTTP requests that share the same {@code Address} may also share the same {@link Connection}.
 */
public class Address {
    final HttpUrl url;
    final Dns dns;
    final SocketFactory socketFactory;
    final Authenticator proxyAuthenticator;
    final List<Protocol> protocols;
    final List<ConnectionSpec> connectionSpecs;
    final ProxySelector proxySelector;
    final Proxy proxy;
    final SSLSocketFactory sslSocketFactory;    //ssl连接工厂
    final HostnameVerifier hostnameVerifier;    //主机名确认
    final CertificatePinner certificatePinner;  //验证证书，包含有一系列的各种引用（platform文件夹为此而生）

    public Address(String uriHost, int uriPort, Dns dns, SocketFactory socketFactory,
                   SSLSocketFactory sslSocketFactory, HostnameVerifier hostnameVerifier,
                   CertificatePinner certificatePinner, Authenticator proxyAuthenticator, Proxy proxy,
                   List<Protocol> protocols, List<ConnectionSpec> connectionSpecs, ProxySelector proxySelector) {
        this.url = new HttpUrl.Builder()
                .scheme(sslSocketFactory != null ? "https" : "http")
                .host(uriHost)
                .port(uriPort)
                .build();

        if (dns == null) throw new NullPointerException("dns == null");
        this.dns = dns;

        if (socketFactory == null) throw new NullPointerException("socketFactory == null");
        this.socketFactory = socketFactory;

        if (proxyAuthenticator == null) {
            throw new NullPointerException("proxyAuthenticator == null");
        }
        this.proxyAuthenticator = proxyAuthenticator;

        if (protocols == null) throw new NullPointerException("protocols == null");
        this.protocols = Util.immutableList(protocols);

        if (connectionSpecs == null) throw new NullPointerException("connectionSpecs == null");
        this.connectionSpecs = Util.immutableList(connectionSpecs);

        if (proxySelector == null) throw new NullPointerException("proxySelector == null");
        this.proxySelector = proxySelector;

        this.proxy = proxy;
        this.sslSocketFactory = sslSocketFactory;
        this.hostnameVerifier = hostnameVerifier;
        this.certificatePinner = certificatePinner;
    }

    /**
     * Returns a URL with the hostname and port of the origin server. The path, query, and fragment of
     * this URL are always empty, since they are not significant(重要) for planning a route.
     * 返回url包括主机名和源服务器的端口号。路径，查询，和url的片段总是空的，因为他们不是重要的计划路线
     */
    public HttpUrl url() {
        return url;
    }

    /**
     * Returns the service that will be used to resolve IP addresses for hostnames.
     * 返回将要被用来解析hostnames的 IP address 的service。
     */
    public Dns dns() {
        return dns;
    }

    /**
     * Returns the socket factory for new connections.
     * 为新的connection 返回socket factory
     */
    public SocketFactory socketFactory() {
        return socketFactory;
    }

    /**
     * Returns the client's proxy authenticator(验证).
     */
    public Authenticator proxyAuthenticator() {
        return proxyAuthenticator;
    }

    /**
     * Returns the protocols the client supports. This method always returns a non-null list that
     * contains minimally {@link Protocol#HTTP_1_1}.
     */
    public List<Protocol> protocols() {
        return protocols;
    }

    public List<ConnectionSpec> connectionSpecs() {
        return connectionSpecs;
    }

    /**
     * Returns this address's proxy selector. Only used if the proxy is null. If none of this
     * selector's proxies are reachable, a direct connection will be attempted.
     * 返回这个address的代理选择器。仅当代理为null时会使用。如果所有的选择器都是不可达的，一个直接连接会重试
     */
    public ProxySelector proxySelector() {
        return proxySelector;
    }

    /**
     * Returns this address's explicitly-specified HTTP proxy, or null to delegate(委托) to the {@linkplain
     * #proxySelector proxy selector}.
     * 返回地址指定的Http proxy，如果为空，将会委托给选择器
     */
    public Proxy proxy() {
        return proxy;
    }

    /** Returns the SSL socket factory, or null if this is not an HTTPS address. */
    public SSLSocketFactory sslSocketFactory(){
        return sslSocketFactory;
    }

    /** Returns the hostname verifier, or null if this is not an HTTPS address. */
    public HostnameVerifier hostnameVerifier(){
        return hostnameVerifier;
    }

    /** Returns this address's certificate pinner, or null if this is not an HTTPS address. */
    public CertificatePinner certificatePinner(){
        return certificatePinner;
    }

    @Override public boolean equals(Object other) {
        if (other instanceof Address) {
            Address that = (Address) other;
            return this.url.equals(that.url)
                    && this.dns.equals(that.dns)
                    && this.proxyAuthenticator.equals(that.proxyAuthenticator)
                    && this.protocols.equals(that.protocols)
                    && this.connectionSpecs.equals(that.connectionSpecs)
                    && this.proxySelector.equals(that.proxySelector)
                    && equal(this.proxy, that.proxy)
                    && equal(this.sslSocketFactory, that.sslSocketFactory)
                    && equal(this.hostnameVerifier, that.hostnameVerifier)
                    && equal(this.certificatePinner, that.certificatePinner);
        }
        return false;
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + url.hashCode();
        result = 31 * result + dns.hashCode();
        result = 31 * result + proxyAuthenticator.hashCode();
        result = 31 * result + protocols.hashCode();
        result = 31 * result + connectionSpecs.hashCode();
        result = 31 * result + proxySelector.hashCode();
        result = 31 * result + (proxy != null ? proxy.hashCode() : 0);
        result = 31 * result + (sslSocketFactory != null ? sslSocketFactory.hashCode() : 0);
        result = 31 * result + (hostnameVerifier != null ? hostnameVerifier.hashCode() : 0);
        result = 31 * result + (certificatePinner != null ? certificatePinner.hashCode() : 0);
        return result;
    }
}




















