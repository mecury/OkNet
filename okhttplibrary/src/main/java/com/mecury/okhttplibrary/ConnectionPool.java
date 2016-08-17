package com.mecury.okhttplibrary;

import com.mecury.okhttplibrary.internal.Util;
import com.mecury.okhttplibrary.internal.connection.RealConnection;
import com.mecury.okhttplibrary.internal.connection.RouteDatabase;
import com.mecury.okhttplibrary.internal.connection.StreamAllocation;

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

    // TODO: 2016/8/16 RealConnection未完成
    private final Deque<RealConnection> connections = new ArrayDeque<>();
    final RouteDatabase routeDatabase = new RouteDatabase();    //记录失败的Route
    boolean cleanupRunning;

    /**
     * Create a new connection pool with tuning parameters appropriate for a single-user application.
     * The tuning parameters in this pool are subject to change in future OkHttp releases. Currently
     * this pool holds up to 5 idle(空闲的) connections which will be evicted(驱逐) after 5 minutes of inactivity(不活动).
     * 创建一个调优参数适用于单用户程序的连接池。在这个池中的调优参数在将来OkHttp发布的时候会更改。目前这个
     * 连接池有5个空闲连接，它将被停止如果五分钟内不活动
     */
    public ConnectionPool(){
        this(5, 5, TimeUnit.MINUTES);
    }

    public ConnectionPool(int maxIdConnections, long keepAliveDuration, TimeUnit timeUnit){
        this.maxIdleConnections = maxIdConnections;
        this.keepAliveDurationNs = keepAliveDuration;

        // Put a floor on the keep alive duration, otherwise cleanup will spin loop.
        if (keepAliveDuration <= 0){
            throw new IllegalArgumentException("keepAliveDuration <= 0" + keepAliveDuration);
        }
    }

    /** Returns the number of idle connections in the pool.
     * 返回池中的空闲的连接
     */
    public synchronized int idleConnectionCount() {
        int total = 0;
        for (RealConnection connection : connections){
            if (connection.allocations.isEmpty()){
                total++;
            }
        }
        return total;
    }

    /**
     * Returns total number of connections in the pool. Note that prior to OkHttp 2.7 this included
     * only idle connections and HTTP/2 connections. Since OkHttp 2.7 this includes all connections,
     * both active and inactive. Use {@link #idleConnectionCount()} to count connections not currently
     * in use.
     * 返回池中的所有连接。
     */
    public synchronized int connectionCount(){
        return connections.size();
    }

    /** Returns a recycled connection to {@code address}, or null if no such connection exists.
     *  根据地址返回一个已回收的连接，如果没有这个连接就返回null
     */
    RealConnection get(Address address, StreamAllocation streamAllocation){
        assert (Thread.holdsLock(this));
        for (RealConnection connection : connections){


        }
    }
}



























