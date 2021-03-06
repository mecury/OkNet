package com.mecury.okhttplibrary.internal.http2;

import com.mecury.okhttplibrary.internal.Util;

import okio.ByteString;

/** HTTP header: the name is an ASCII string, but the value can be UTF-8. */
public class Header {
    public static final ByteString RESPONSE_STATUS = ByteString.encodeUtf8(":status");
    public static final ByteString TARGET_METHOD = ByteString.encodeUtf8(":method");
    public static final ByteString TARGET_PATH = ByteString.encodeUtf8(":path");
    public static final ByteString TARGET_SCHEME = ByteString.encodeUtf8(":scheme");
    public static final ByteString TARGET_AUTHORITY = ByteString.encodeUtf8(":authority");

    /** Name in case-insensitive ASCII encoding.
     * 名字不区分大小写的ASCII编码
     */
    public final ByteString name;

    /** Value in UTF-8 encoding.
     * UTF-8 格式的Value
     */
    public final ByteString value;
    final int hpackSize;

    public Header(String name, String value){
        this(ByteString.encodeUtf8(name), ByteString.encodeUtf8(value));
    }

    public Header(ByteString name, String value) {
        this(name, ByteString.encodeUtf8(value));
    }

    public Header(ByteString name, ByteString value){
        this.name = name;
        this.value = value;
        this.hpackSize = 32 + name.size() + value.size();
    }

    @Override public boolean equals(Object other) {
        if (other instanceof Header) {
            Header that = (Header) other;
            return this.name.equals(that.name)
                    && this.value.equals(that.value);
        }
        return false;
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + name.hashCode();
        result = 31 * result + value.hashCode();
        return result;
    }

    @Override public String toString() {
        return Util.format("%s: %s", name.utf8(), value.utf8());
    }
}
