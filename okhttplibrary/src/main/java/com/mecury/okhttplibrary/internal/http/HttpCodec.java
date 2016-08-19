package com.mecury.okhttplibrary.internal.http;

import com.mecury.okhttplibrary.Request;
import com.mecury.okhttplibrary.Response;

import java.io.IOException;

import okio.Sink;

/** Encodes HTTP requests and decodes HTTP responses.
 * 编码HTTP requests 解码 HTTP responses
 */
public interface HttpCodec {

    /**
     * The timeout to use while discarding a stream of input data. Since this is used for connection
     * reuse, this timeout should be significantly less than the time it takes to establish a new
     * connection.
     * 超时使用而对其的输入数据流。因为这是用于连接重新使用，这个超时时间应该明显的小于建立一个新的连接
     */
    int DISCARD_STREAM_TIMEOUT_MILLIS = 100;

    /** Returns an output stream where the request body can be streamed.
     * 返回一个输出流，他的请求体能够被流传输
     * */
    Sink createRequestBody(Request request, long currentLength);

    /** This should update the HTTP engine's sentRequestMillis field.
     * 更新HTTP发送的请求
     * */
    void writeRequestHeaders(Request request) throws IOException;

    /** Flush the request to the underlying socket. */
    void finishRequest() throws IOException;

    /** Read and return response headers. */
    Response.Builder readResponseHeaders() throws IOException;

    /** Returns a stream that reads the response body. */
    ResponseBody openResponseBody(Response response) throws IOException;

    /**
     * Cancel this stream. Resources held by this stream will be cleaned up, though not synchronously.
     * That may happen later by the connection pool thread.
     */
    void cancel();
}
