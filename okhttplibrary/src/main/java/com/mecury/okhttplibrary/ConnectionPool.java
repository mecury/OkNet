package com.mecury.okhttplibrary;

import com.mecury.okhttplibrary.internal.Util;
import com.mecury.okhttplibrary.internal.connection.RealConnection;
import com.mecury.okhttplibrary.internal.connection.RouteDatabase;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Manages reuse of HTTP and HTTP/2 connections for reduced network latency(网络延迟). HTTP requests that
 * share the same {@link Address} may share a {@link Connection}. This class implements the policy
 * of which connections to keep open for future use.
 * 管理Http的连接为了降低网络延迟，Http requests 对于拥有相同的地址会分享相同的连接，这个类的作用是
 * 将连接保持开放状态以便未来使用
 */
public class ConnectionPool {

    /**
     * Background threads are used to cleanup expired(过期的) connections. There will be at most a single
     * thread running per connection pool. The thread pool executor permits the pool itself to be
     * garbage collected.
     * 就是新建一个线程池
     */
    private static final Executor executor = new ThreadPoolExecutor(0 /* corePoolSize */,
            Integer.MAX_VALUE /* maximumPoolSize */, 60L /* keepAliveTime */, TimeUnit.SECONDS,
            new SynchronousQueue<Runnable>(), Util.threadFactory("OkHttp ConnectionPool", true));

    /** The maximum number of idle(闲置) connections for each address.
     * 每一个地址的最大空闲连接数量
     */
    private final int maxIdleConnections;
    private final long keepAliveDurationNs;
    private final Runnable cleanupRunnable = new Runnable() {
        @Override
        public void run() {

        }
    };

    // TODO: 2016/8/16 弄清楚这另个在继续 
    private final Deque<RealConnection> connections = new ArrayDeque<>();
    final RouteDatabase routeDatabase = new RouteDatabase();
}



























