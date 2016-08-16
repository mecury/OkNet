package com.mecury.okhttplibrary.internal;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * A domain name service that resolves IP addresses for host names. Most applications will use the
 * {@linkplain #SYSTEM system DNS service}, which is the default. Some applications may provide
 * their own implementation to use a different DNS server, to prefer IPv6 addresses, to prefer IPv4
 * addresses, or to force a specific known IP address.
 * 一个域名服务解决IP地址的主机名，大部分的情况都是默认的，一些情况下，他们会提供他们自己的实现为了使用不同的域名服务器
 *
 * <p>Implementations of this interface must be safe for concurrent use.
 */
public interface Dns {

    Dns SYSTEM = new Dns() {
        @Override
        public List<InetAddress> lookup(String hostname) throws UnknownHostException {
            if (hostname == null) throw new NullPointerException("hostname == null");
            return Arrays.asList(InetAddress.getAllByName(hostname));
        }
    };

    /**
     * Returns the IP addresses of {@code hostname}, in the order they will be attempted(尝试) by OkHttp. If
     * a connection to an address fails, OkHttp will retry the connection with the next address until
     * either a connection is made, the set of IP addresses is exhausted(耗尽), or a limit is exceeded.
     * 返回hostname的 IP 地址 ,OkHttp将会顺序尝试连接这些地址。如果连接一个地址失败了，OkHttp将会重连下一个地址
     * 知道connection的 IP地址耗尽，或超过限制
     */
    List<InetAddress> lookup(String hostname) throws UnknownHostException;
}
