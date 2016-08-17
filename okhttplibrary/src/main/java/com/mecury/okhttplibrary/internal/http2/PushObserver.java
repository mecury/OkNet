package com.mecury.okhttplibrary.internal.http2;

import java.io.IOException;
import java.util.List;

import okio.BufferedSource;

/**
 * {@link Protocol#HTTP_2 HTTP/2} only. Processes server-initiated HTTP requests on the client.
 * Implementations must quickly dispatch callbacks to avoid creating a bottleneck(瓶颈).
 * 服务器向client发起Http请求，实现必须快速分发回调，为了避免产生瓶颈
 *
 * <p>While {@link #onReset} may occur at any time, the following callbacks are expected in order,
 * correlated by stream ID.
 *
 * <ul>
 *     <li>{@link #onRequest}</li> <li>{@link #onHeaders} (unless canceled)
 *     <li>{@link #onData} (optional sequence of data frames)
 * </ul>
 *
 * <p>As a stream ID is scoped to a single HTTP/2 connection, implementations which target multiple
 * connections should expect repetition of stream IDs.
 *
 * <p>Return true to request cancellation of a pushed stream.  Note that this does not guarantee
 * future frames won't arrive on the stream ID.
 */
public interface PushObserver {

    /**
     * Describes the request that the server intends to push a response for.
     * 描述request这个server将要应答的
     *
     * @param streamId server-initiated stream ID: an even number.
     * @param requestHeaders minimally includes {@code :method}, {@code :scheme}, {@code :authority},
     * and (@code :path}.
     */
    boolean onRequest(int streamId, List<Header> responseHeaders);

    /**
     * The response headers corresponding(相应的) to a pushed request.  When {@code last} is true, there are
     * no data frames to follow.
     * request对应的response请求头。当last为true，表示没有后续帧
     *
     * @param streamId server-initiated stream ID: an even number.
     * @param responseHeaders minimally includes {@code :status}.
     * @param last when true, there is no response data.
     */
    boolean onHeaders(int streamId, List<Header> responseHeaders, boolean last);

    /**
     * A chunk of response data corresponding to a pushed request.  This data must either be read or
     * skipped.
     * request对应的一部分response。这个数据必须被阅读或者跳过
     *
     * @param streamId server-initiated stream ID: an even number.
     * @param source location of data corresponding with this stream ID.
     * @param byteCount number of bytes to read or skip from the source.
     * @param last when true, there are no data frames to follow.
     */
    boolean onData(int streamId, BufferedSource source, int byteCount, boolean last) throws IOException;

    /** Indicates（表明 the reason why this stream was canceled. */
    void onReset(int streamId, ErrorCode errorCode);

    PushObserver CANCEL = new PushObserver() {
        @Override
        public boolean onRequest(int streamId, List<Header> responseHeaders) {
            return true;
        }

        @Override
        public boolean onHeaders(int streamId, List<Header> responseHeaders, boolean last) {
            return true;
        }

        @Override
        public boolean onData(int streamId, BufferedSource source, int byteCount, boolean last) throws IOException {
            return true;
        }

        @Override
        public void onReset(int streamId, ErrorCode errorCode) {

        }
    };
}
