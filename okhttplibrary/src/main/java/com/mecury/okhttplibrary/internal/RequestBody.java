package com.mecury.okhttplibrary.internal;

import com.mecury.okhttplibrary.MediaType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import okio.BufferedSink;
import okio.ByteString;
import okio.Okio;
import okio.Source;

/**
 * Created by 海飞 on 2016/8/14.
 */
public abstract class RequestBody {
    /** Returns the Content-Type header for this body. */
    public abstract MediaType contentType();

    /**
     * Returns the number of bytes that will be written to {@code out} in a call to {@link #writeTo},
     * or -1 if that count is unknown.
     * 返回字节流的长度，该字节流将被在 writeTo() 中使用写入到 BufferedSink (Okio)中
     */
    public long contentLength() throws IOException {
        return -1;
    }

    /** Writes the content of this request to {@code out}.
     *  将请求的内容吸写入到 BufferedSink 中
     */
    public abstract void writeTo(BufferedSink sink) throws IOException;

    /**
     * Returns a new request body that transmits(传输) {@code content}. If {@code contentType} is non-null
     * and lacks(缺乏) a charset(字符集), this will use UTF-8.
     */
    public static RequestBody create(MediaType contentType, String content) {
        Charset charset = Util.UTF_8;
        if (contentType != null) {
            charset = contentType.charset();
            if (charset == null) {
                charset = Util.UTF_8;
                contentType = MediaType.parse(contentType + "; charset=utf-8");
            }
        }
        byte[] bytes = content.getBytes(charset);
        return create(contentType, bytes);
    }

    /** Returns a new request body that transmits {@code content}. */
    public static RequestBody create(final MediaType contentType, final ByteString content) {
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return contentType;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                sink.write(content);
            }
        };
    }

    /** Returns a new request body that transmits {@code content}. */
    public static RequestBody create(final MediaType contentType, final byte[] content) {
        return create(contentType, content, 0, content.length);
    }

    /** Returns a new request body that transmits {@code content}. */
    public static RequestBody create(final MediaType contentType, final byte[] content,
                                     final int offset, final int byteCount) {
        if (content == null) throw new NullPointerException("content == null");
        // TODO: 2016/8/15 Util
        Util.checkOffsetAndCount(content.length, offset, byteCount);
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return contentType;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                sink.write(content);
            }
        };
    }

    /** Returns a new request body that transmits {@code content}. */
    public static RequestBody create(final MediaType contentType, final File file){
        if (file == null) throw new NullPointerException("content == null");

        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return contentType;
            }

            @Override
            public long contentLength() throws IOException {
                return file.length();
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                Source source = null;
                try{
                    source = Okio.source(file);
                    sink.writeAll(source);
                }finally{
                    Util.closeQuietly(source);
                }
            }
        };
    }
}
