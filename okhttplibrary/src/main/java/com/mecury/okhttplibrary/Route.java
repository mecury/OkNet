package com.mecury.okhttplibrary;

import android.location.Address;
import android.net.Proxy;

import java.net.InetSocketAddress;

/**
 * The concrete route used by a connection to reach an abstract origin server. When creating a
 * connection the client has many options:
 * 这个具体的路线是用来使一个connection到达抽象原始的server：当创建连接时，client必须设置明确的代理服务器
 * 和 IP address
 * <p>
 * <ul>
 * <li><strong>HTTP proxy:</strong> a proxy server may be explicitly configured for the client.
 * Otherwise the {@linkplain java.net.ProxySelector proxy selector} is used. It may return
 * multiple proxies to attempt.
 * <li><strong>IP address:</strong> whether connecting directly to an origin server or a proxy,
 * opening a socket requires an IP address. The DNS server may return multiple IP addresses
 * to attempt.
 * </ul>
 * <p>
 * <p>Each route is a specific selection of these options.
 */
public final class Route {
    final Address address;
    final Proxy proxy;
    final InetSocketAddress inetsocketAddress;

    public Route(Address address, Proxy proxy, InetSocketAddress inetsocketAddress) {
        if (address == null) {
            throw new NullPointerException("address == null");
        }
        if (proxy == null) {
            throw new NullPointerException("proxy == null");
        }
        if (inetsocketAddress == null) {
            throw new NullPointerException("inetSocketAddress == null");
        }
        this.address = address;
        this.proxy = proxy;
        this.inetsocketAddress = inetsocketAddress;
    }

    public Address address(){
        return address;
    }

    /**
     * Returns the {@link Proxy} of this route.
     * 返回这条路线的代理
     *
     * <strong>Warning:</strong> This may disagree with {@link Address#proxy} when it is null. When
     * the address's proxy is null, the proxy selector is used.
     */
    public Proxy proxy(){
        return proxy;
    }

    public boolean requiresTunnel(){
        return address.sslSocketFactory
    }
}






















