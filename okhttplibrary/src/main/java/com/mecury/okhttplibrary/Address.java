package com.mecury.okhttplibrary;

import android.net.Proxy;

import com.mecury.okhttplibrary.internal.Dns;

import java.net.Authenticator;
import java.net.ProxySelector;
import java.net.Socket;
import java.util.List;

import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;

/**
 * A specification(规范) for a connection to an origin server. For simple connections, this is the
 * server's hostname and port. If an explicit(显式的） proxy is requested (or {@linkplain Proxy#NO_PROXY no
 * proxy} is explicitly requested), this also includes that proxy information. For secure
 * connections the address also includes the SSL socket factory, hostname verifier, and certificate(证书）
 * pinner.
 * 一种连接原始服务器的规范。对于简单的连接，包含server的主机名和端口。如果一个显式的server被请求，它也包含这个
 * 服务器的信息。对于安全的连接，这个address必须包含the SSL socket factory, hostname verifier, and certificate pinner.
 *
 * <p>HTTP requests that share the same {@code Address} may also share the same {@link Connection}.
 */
public class Address {
    final HttpUrl url;
    final Dns dns;
    final SocketFactory socketFactory;
    final Authenticator authenticator;
    final List<Protocol> protocols;
    final List<ConnectionSpec> connectionSpecs;
    final ProxySelector proxySelector;
    final Proxy proxy;
    final SSLSocketFactory sslSocketFactory;
    final HostnameVerifier hostnameVerifier;
    final CertificatePinner certificatePinner;

}




















