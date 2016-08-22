package com.mecury.okhttplibrary.internal.connection;

import com.mecury.okhttplibrary.Address;
import com.mecury.okhttplibrary.ConnectionPool;
import com.mecury.okhttplibrary.OkHttpClient;
import com.mecury.okhttplibrary.Route;
import com.mecury.okhttplibrary.internal.Internal;
import com.mecury.okhttplibrary.internal.Util;
import com.mecury.okhttplibrary.internal.http.HttpCodec;
import com.mecury.okhttplibrary.internal.http1.Http1Codec;
import com.mecury.okhttplibrary.internal.http2.ErrorCode;
import com.mecury.okhttplibrary.internal.http2.Http2Codec;
import com.mecury.okhttplibrary.internal.http2.StreamResetException;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * This class coordinates the relationship between three entities:
 * 这个类依赖三个实体间的关系：
 *
 * <ul>
 *     <li><strong>Connections:</strong> physical socket connections to remote servers. These are
 *         potentially slow to establish so it is necessary to be able to cancel a connection
 *         currently being connected.
 *         物理Socket连接远程server。因为建立连接比较缓慢因此暂停当前的连接是必要的
 *     <li><strong>Streams:</strong> logical HTTP request/response pairs that are layered on
 *         connections. Each connection has its own allocation limit, which defines how many
 *         concurrent streams that connection can carry. HTTP/1.x connections can carry 1 stream
 *         at a time, HTTP/2 typically carry multiple.
 *         在HTTP链接中，request/response的流是分层的连接。每一个连接有他自己的分配限制，它定义了连接能够有
 *         多少并发的流。
 *     <li><strong>Calls:</strong> a logical sequence of streams, typically an initial request and
 *         its follow up requests. We prefer to keep all streams of a single call on the same
 *         connection for better behavior and locality.
 *         streams的逻辑序列，典型的是一个request跟随者其他requests。我们为了更好的行为和局部性更喜欢在同样的
 *         链接中保持单一的调用者
 * </ul>
 *
 * <p>Instances of this class act on behalf of the call, using one or more streams over one or more
 * connections. This class has APIs to release each of the above resources:
 *
 * <ul>
 *     <li>{@link #noNewStreams()} prevents the connection from being used for new streams in the
 *         future. Use this after a {@code Connection: close} header, or when the connection may be
 *         inconsistent.
 *         防止连接被用于新的流。在关闭连接时或者连接不一致是使用
 *     <li>{@link #streamFinished streamFinished()} releases the active stream from this allocation.
 *         Note that only one stream may be active at a given time, so it is necessary to call
 *         {@link #streamFinished streamFinished()} before creating a subsequent stream with {@link
 *         #newStream newStream()}.
 *         释放分配的stream，因为同一时间只能有一个stream保持活跃，因此在创建一个后续的stream之前调用是有必要的
 *     <li>{@link #release()} removes the call's hold on the connection. Note that this won't
 *         immediately free the connection if there is a stream still lingering. That happens when a
 *         call is complete but its response body has yet to be fully consumed.
 *         移除占用连接的调用者。这里不会立即释放，如果stream 一直缓慢消失，这种情况发生在当一个调用完成，但是他的
 *         响应体还没有完全完成
 * </ul>
 *
 * <p>This class supports {@linkplain #cancel asynchronous canceling}. This is intended to have the
 * smallest blast radius possible. If an HTTP/2 stream is active, canceling will cancel that stream
 * but not the other streams sharing its connection. But if the TLS handshake is still in progress
 * then canceling may break the entire connection.
 * 这个类支持异步取消。如果HTTP/2的流是活动的，取消会取消这个流而不会取消分享这个链接的其他流。但是如果TLS握手
 * 依然在线程中，它可能会中断整个连接
 */
public class StreamAllocation {
    public final Address address;
    private Route route;
    private final ConnectionPool connectionPool;

    // State guarded by connectionPool.连接池负责
    private final RouteSelector routeSelector;
    private int refusedStreamCount;
    private RealConnection connection;
    private boolean released;
    private boolean canceled;
    private HttpCodec codec;

    public StreamAllocation(ConnectionPool connectionPool, Address address) {
        this.connectionPool = connectionPool;
        this.address = address;
        this.routeSelector = new RouteSelector(address, routeDatabase());
    }

    public HttpCodec newStream(OkHttpClient client, boolean doExtensiveHealthChecks) {
        int connectTimeout = client.connectTimeoutMillis();
        int readTimeout = client.readTimeoutMillis();
        int writeTimeout = client.writeTimeoutMillis();
        boolean connectionRetryEnabled = client.retryOnConnectionFailure();

        try {
            RealConnection resultConnection = findHealthyConnection(connectTimeout, readTimeout,
                    writeTimeout, connectionRetryEnabled, doExtensiveHealthChecks);

            HttpCodec resultCodec;
            if (resultConnection.http2Connection != null) {
                resultCodec = new Http2Codec(client, this, resultConnection.http2Connection);
            } else {
                resultConnection.socket().setSoTimeout(readTimeout);
                resultConnection.source.timeout().timeout(readTimeout, MILLISECONDS);
                resultConnection.sink.timeout().timeout(writeTimeout, MILLISECONDS);
                resultCodec = new Http1Codec(
                        client, this, resultConnection.source, resultConnection.sink);
            }

            synchronized (connectionPool) {
                codec = resultCodec;
                return resultCodec;
            }
        } catch (IOException e) {
            throw new RouteException(e);
        }
    }

    /**
     * Finds a connection and returns it if it is healthy. If it is unhealthy the process is repeated
     * until a healthy connection is found.
     * 发现并返回健康的连接。如果他是不健康的，线程会重复寻找直到找到
     */
    private RealConnection findHealthyConnection(int connectTimeout, int readTimeout,
                                                 int writeTimeout, boolean connectionRetryEnabled, boolean doExtensiveHealthChecks)
            throws IOException {
        while (true) {
            RealConnection candidate = findConnection(connectTimeout, readTimeout, writeTimeout,
                    connectionRetryEnabled);

            // If this is a brand new connection, we can skip the extensive health checks.
            //全新的连接可以跳过健康检查
            synchronized (connectionPool) {
                if (candidate.successCount == 0) {
                    return candidate;
                }
            }

            // Do a (potentially slow) check to confirm that the pooled connection is still good. If it
            // isn't, take it out of the pool and start again.
            //检查确定连接池依然是好的，如果不是，从池中清除如果再次开始
            if (!candidate.isHealthy(doExtensiveHealthChecks)) {
                noNewStreams();
                continue;
            }

            return candidate;
        }
    }

    /**
     * Returns a connection to host a new stream. This prefers the existing connection if it exists,
     * then the pool, finally building a new connection.
     * 返回连接到主机新的流。它偏向于已经存在的连接，然后是pool， 最后是建立一个新的连接
     */
    private RealConnection findConnection(int connectTimeout, int readTimeout, int writeTimeout,
                                          boolean connectionRetryEnabled) throws IOException {
        Route selectedRoute;
        synchronized (connectionPool) {
            if (released) throw new IllegalStateException("released");
            if (codec != null) throw new IllegalStateException("codec != null");
            if (canceled) throw new IOException("Canceled");

            RealConnection allocatedConnection = this.connection;
            if (allocatedConnection != null && !allocatedConnection.noNewStreams) {
                return allocatedConnection;
            }

            // Attempt to get a connection from the pool.
            RealConnection pooledConnection = Internal.instance.get(connectionPool, address, this);
            if (pooledConnection != null) {
                this.connection = pooledConnection;
                return pooledConnection;
            }

            selectedRoute = route;
        }

        if (selectedRoute == null) {
            selectedRoute = routeSelector.next();
            synchronized (connectionPool) {
                route = selectedRoute;
                refusedStreamCount = 0;
            }
        }
        RealConnection newConnection = new RealConnection(selectedRoute);

        synchronized (connectionPool) {
            acquire(newConnection);
            Internal.instance.put(connectionPool, newConnection);
            this.connection = newConnection;
            if (canceled) throw new IOException("Canceled");
        }

        newConnection.connect(connectTimeout, readTimeout, writeTimeout, address.connectionSpecs(),
                connectionRetryEnabled);
        routeDatabase().connected(newConnection.route());

        return newConnection;
    }


    public void streamFinished(boolean noNewStreams, HttpCodec codec) {
        synchronized (connectionPool) {
            if (codec == null || codec != this.codec) {
                throw new IllegalStateException("expected " + this.codec + " but was " + codec);
            }
            if (!noNewStreams) {
                connection.successCount++;
            }
        }
        deallocate(noNewStreams, false, true);
    }

    public HttpCodec codec() {
        synchronized (connectionPool) {
            return codec;
        }
    }

    private RouteDatabase routeDatabase() {
        return Internal.instance.routeDatabase(connectionPool);
    }

    public synchronized RealConnection connection() {
        return connection;
    }

    public void release() {
        deallocate(false, true, false);
    }

    /** Forbid new streams from being created on the connection that hosts this allocation.
     * 禁止在host分配的连接上创建新流，
     */
    public void noNewStreams() {
        deallocate(true, false, false);
    }

    /**
     * Releases resources held by this allocation. If sufficient resources are allocated, the
     * connection will be detached or closed.
     * 释放分配的资源。如果有足够的资源分配，这个连接将会 分离或关闭
     */
    private void deallocate(boolean noNewStreams, boolean released, boolean streamFinished) {
        RealConnection connectionToClose = null;
        synchronized (connectionPool) {
            if (streamFinished) {
                this.codec = null;
            }
            if (released) {
                this.released = true;
            }
            if (connection != null) {
                if (noNewStreams) {
                    connection.noNewStreams = true;
                }
                if (this.codec == null && (this.released || connection.noNewStreams)) {
                    release(connection);
                    if (connection.allocations.isEmpty()) {
                        connection.idleAtNanos = System.nanoTime();
                        if (Internal.instance.connectionBecameIdle(connectionPool, connection)) {
                            connectionToClose = connection;
                        }
                    }
                    connection = null;
                }
            }
        }
        if (connectionToClose != null) {
            Util.closeQuietly(connectionToClose.socket());
        }
    }

    public void cancel() {
        HttpCodec codecToCancel;
        RealConnection connectionToCancel;
        synchronized (connectionPool) {
            canceled = true;
            codecToCancel = codec;
            connectionToCancel = connection;
        }
        if (codecToCancel != null) {
            codecToCancel.cancel();
        } else if (connectionToCancel != null) {
            connectionToCancel.cancel();
        }
    }

    public void streamFailed(IOException e) {
        boolean noNewStreams = false;

        synchronized (connectionPool) {
            if (e instanceof StreamResetException) {
                StreamResetException streamResetException = (StreamResetException) e;
                if (streamResetException.errorCode == ErrorCode.REFUSED_STREAM) {
                    refusedStreamCount++;
                }
                // On HTTP/2 stream errors, retry REFUSED_STREAM errors once on the same connection. All
                // other errors must be retried on a new connection.
                if (streamResetException.errorCode != ErrorCode.REFUSED_STREAM || refusedStreamCount > 1) {
                    noNewStreams = true;
                    route = null;
                }
            } else if (connection != null && !connection.isMultiplexed()) {
                noNewStreams = true;

                // If this route hasn't completed a call, avoid it for new connections.
                if (connection.successCount == 0) {
                    if (route != null && e != null) {
                        routeSelector.connectFailed(route, e);
                    }
                    route = null;
                }
            }
        }

        deallocate(noNewStreams, false, true);
    }

    /**
     * Use this allocation to hold {@code connection}. Each call to this must be paired with a call to
     * {@link #release} on the same connection.
     */
    public void acquire(RealConnection connection) {
        assert (Thread.holdsLock(connectionPool));
        connection.allocations.add(new WeakReference<>(this));
    }

    /** Remove this allocation from the connection's list of allocations. */
    private void release(RealConnection connection) {
        for (int i = 0, size = connection.allocations.size(); i < size; i++) {
            Reference<StreamAllocation> reference = connection.allocations.get(i);
            if (reference.get() == this) {
                connection.allocations.remove(i);
                return;
            }
        }
        throw new IllegalStateException();
    }

    public boolean hasMoreRoutes() {
        return route != null || routeSelector.hasNext();
    }

    @Override public String toString() {
        return address.toString();
    }
}



















