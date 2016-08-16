package com.mecury.okhttplibrary;


import android.location.Address;

import com.mecury.okhttplibrary.internal.Internal;
import com.mecury.okhttplibrary.internal.Util;
import com.mecury.okhttplibrary.internal.connection.RealConnection;
import com.mecury.okhttplibrary.internal.connection.StreamAllocation;

import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.List;

import javax.net.ssl.SSLSocket;

/**
 * Created by 海飞 on 2016/8/15.
 * Factory for {@linkplain Call calls}, which can be used to send HTTP requests and read their
 * responses.
 */
public class OkHttpClient implements Cloneable, Call.Factory {

    //默认的连接协议：http/1.0, htp/1.1, spdy/3.1, h2
    private static final List<Protocol> DEFAULT_PROTOCOLS = Util.immutableList(
            Protocol.HTTP_2, Protocol.HttP_1_1);

    // TODO: 2016/8/15 不懂
    private static final List<ConnectionSpec> DEFAULT_CONNECTION_SPECS = Util.immutableList(
            ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT);

    static {
        Internal.instance = new Internal() {
            @Override
            public void addLenient(Headers.Builder builder, String line) {

            }

            @Override
            public void addLenient(Headers.Builder builder, String name, String value) {

            }

            @Override
            public void setCache(OkHttpClient.Builder builder, InternalCache internalCache) {

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
            public StreamAllocation callEngineGetStreamAllocation(Call call) {
                return null;
            }

            @Override
            public void setCallWebSocket(Call call) {

            }
        };
    }


    @Override
    public Call newCall(Request request) {
        return null;
    }
}
