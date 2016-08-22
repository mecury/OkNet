package com.mecury.okhttplibrary;

import com.mecury.okhttplibrary.internal.Util;
import com.mecury.okhttplibrary.internal.connection.RealConnection;
import com.mecury.okhttplibrary.internal.connection.RouteDatabase;
import com.mecury.okhttplibrary.internal.connection.StreamAllocation;
import com.mecury.okhttplibrary.internal.platform.Platform;

import java.lang.ref.Reference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.mecury.okhttplibrary.internal.Util.closeQuietly;
import static com.mecury.okhttplibrary.internal.platform.Platform.WARN;

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
            while (true) {
                long waitNanos = cleanup(System.nanoTime());
                if (waitNanos == -1) return;
                if (waitNanos > 0) {
                    long waitMillis = waitNanos / 1000000L;
                    waitNanos -= (waitMillis * 1000000L);
                    synchronized (ConnectionPool.this) {
                        try {
                            ConnectionPool.this.wait(waitMillis, (int) waitNanos);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            }
        }
    };

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
        for (RealConnection connection : connections) {
            if (connection.allocations.size() < connection.allocationLimit
                    && address.equals(connection.route().address)
                    && !connection.noNewStreams) {
                streamAllocation.acquire(connection);
                return connection;
            }
        }
        return null;
    }

    void put(RealConnection connection) {
        assert (Thread.holdsLock(this));
        if (!cleanupRunning) {
            cleanupRunning = true;
            executor.execute(cleanupRunnable);
        }
        connections.add(connection);
    }

    /**
     * Notify this pool that {@code connection} has become idle. Returns true if the connection has
     * been removed from the pool and should be closed.
     * 通知pool这个connection空闲了。返回true如果连接已经被pool移除 ，他也应该被关闭
     */
    boolean connectionBecameIdle(RealConnection connection) {
        assert (Thread.holdsLock(this));
        if (connection.noNewStreams || maxIdleConnections == 0) {
            connections.remove(connection);
            return true;
        } else {
            notifyAll(); // Awake the cleanup thread: we may have exceeded the idle connection limit.
            return false;
        }
    }

    /** Close and remove all idle connections in the pool. */
    public void evictAll() {
        List<RealConnection> evictedConnections = new ArrayList<>();
        synchronized (this) {
            for (Iterator<RealConnection> i = connections.iterator(); i.hasNext(); ) {
                RealConnection connection = i.next();
                if (connection.allocations.isEmpty()) {
                    connection.noNewStreams = true;
                    evictedConnections.add(connection);
                    i.remove();
                }
            }
        }

        for (RealConnection connection : evictedConnections) {
            closeQuietly(connection.socket());
        }
    }

    /**
     * Performs maintenance on this pool, evicting the connection that has been idle the longest if
     * either it has exceeded the keep alive limit or the idle connections limit.
     * 维护该连接池。清除空闲时间最长的连接，如果他超过了保活时间 或 空闲连接设置
     *
     * <p>Returns the duration in nanos to sleep until the next scheduled call to this method. Returns
     * -1 if no further cleanups are required.
     */
    long cleanup(long now) {
        int inUseConnectionCount = 0;
        int idleConnectionCount = 0;
        RealConnection longestIdleConnection = null;
        long longestIdleDurationNs = Long.MIN_VALUE;

        // Find either a connection to evict, or the time that the next eviction is due.
        synchronized (this) {
            for (Iterator<RealConnection> i = connections.iterator(); i.hasNext(); ) {
                RealConnection connection = i.next();

                // If the connection is in use, keep searching.
                if (pruneAndGetAllocationCount(connection, now) > 0) {
                    inUseConnectionCount++;
                    continue;
                }

                idleConnectionCount++;

                // If the connection is ready to be evicted, we're done.
                long idleDurationNs = now - connection.idleAtNanos;
                if (idleDurationNs > longestIdleDurationNs) {
                    longestIdleDurationNs = idleDurationNs;
                    longestIdleConnection = connection;
                }
            }

            if (longestIdleDurationNs >= this.keepAliveDurationNs
                    || idleConnectionCount > this.maxIdleConnections) {
                // We've found a connection to evict. Remove it from the list, then close it below (outside
                // of the synchronized block).
                connections.remove(longestIdleConnection);
            } else if (idleConnectionCount > 0) {
                // A connection will be ready to evict soon.
                return keepAliveDurationNs - longestIdleDurationNs;
            } else if (inUseConnectionCount > 0) {
                // All connections are in use. It'll be at least the keep alive duration 'til we run again.
                return keepAliveDurationNs;
            } else {
                // No connections, idle or in use.
                cleanupRunning = false;
                return -1;
            }
        }

        closeQuietly(longestIdleConnection.socket());

        // Cleanup again immediately.
        return 0;
    }

    /**
     * Prunes any leaked allocations and then returns the number of remaining live allocations on
     * {@code connection}. Allocations are leaked if the connection is tracking them but the
     * application code has abandoned them. Leak detection is imprecise and relies on garbage
     * collection.
     * 清除泄漏的连接，然后返回剩下的能够分配的connection数量。分配泄漏发生在应用已经抛弃了该分配，
     * 但是连接还在监视他。泄漏检测是不精确的且依赖垃圾回收
     */
    private int pruneAndGetAllocationCount(RealConnection connection, long now) {
        List<Reference<StreamAllocation>> references = connection.allocations;
        for (int i = 0; i < references.size(); ) {
            Reference<StreamAllocation> reference = references.get(i);

            if (reference.get() != null) {
                i++;
                continue;
            }

            // We've discovered a leaked allocation. This is an application bug.
            Platform.get().log(WARN, "A connection to " + connection.route().address().url()
                    + " was leaked. Did you forget to close a response body?", null);
            references.remove(i);
            connection.noNewStreams = true;

            // If this was the last allocation, the connection is eligible for immediate eviction.
            if (references.isEmpty()) {
                connection.idleAtNanos = now - keepAliveDurationNs;
                return 0;
            }
        }

        return references.size();
    }
}



























