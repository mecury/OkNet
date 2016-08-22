package com.mecury.okhttplibrary;

import com.mecury.okhttplibrary.internal.http.HttpMethod;

import java.net.URL;
import java.util.List;

/**
 * An HTTP request. Instances of this class are immutable(不可变的) if their {@link #body} is null or itself
 * immutable.
 */
public final class Request {
    private final HttpUrl url;  //请求url
    private final String method;    //请求方法
    private final Headers headers;  //请求头部
    private final RequestBody body; //请求体
    private final Object tag;

    private volatile CacheControl cacheControl;

    private Request(Builder builder) {
        this.url = builder.url;
        this.method = builder.method;
        this.headers = builder.headers.build();
        this.body = builder.body;
        this.tag = builder.tag != null ? builder.tag : this;
    }

    public HttpUrl url() {
        return url;
    }

    public String method() {
        return method;
    }

    public Headers headers() {
        return headers;
    }

    public String header(String name) {
        return headers.get(name);
    }

    public List<String> headers(String name) {
        return headers.values(name);
    }

    public RequestBody body() {
        return body;
    }

    public Object tag() {
        return tag;
    }

    public Builder newBuilder() {
        return new Builder(this);
    }

    public CacheControl cacheControl(){
        CacheControl result = cacheControl;
        return result != null ? result : (cacheControl = CacheControl.parse(headers));
    }

    public boolean isHttps(){
        return url.isHttps();
    }

    @Override
    public String toString() {
        return "Request{method="
                + method
                + ", url="
                + url
                + ", tag="
                + (tag != this ? tag : null)
                + '}';
    }

    /**
     * 建造者类
     */
    public static class Builder {
        private HttpUrl url;
        private String method;
        private Headers.Builder headers;
        private RequestBody body;
        private Object tag;

        public Builder() {
            this.method = "GET";
            this.headers = new Headers.Builder();
        }

        public Builder(Request request) {
            this.url = request.url;
            this.method = request.method;
            this.headers = request.headers.newBuilder();
            this.body = request.body;
            this.tag = request.tag;
        }

        public Builder url(HttpUrl url) {
            if (url == null) throw new NullPointerException("url == null");
            this.url = url;
            return this;
        }

        /**
         * Sets the URL target of this request.
         *
         * @throws IllegalArgumentException if {@code url} is not a valid HTTP or HTTPS URL. Avoid this
         *                                  exception by calling {@link HttpUrl#parse}; it returns null for invalid URLs.
         */
        public Builder url(String url) {
            if (url == null) throw new NullPointerException("url == null");

            //判断Http与Https
            if (url.regionMatches(true, 0, "ws:", 0, 4)) {
                url = "http:" + url.substring(3);
            } else if (url.regionMatches(true, 0, "wss:", 0, 4)) {
                url = "https:" + url.substring(4);
            }

            HttpUrl parsed = HttpUrl.parse(url); //将String类型的url转为HttpUrl
            if (parsed == null) throw new IllegalArgumentException("unexpected url:" + url);
            return url(parsed);
        }

        /**
         * Sets the URL target of this request.
         *
         * @throws IllegalArgumentException if the scheme of {@code url} is not {@code http} or {@code
         *                                  https}.
         */
        public Builder url(URL url) {
            if (url == null) throw new NullPointerException("url == null");
            HttpUrl parsed = HttpUrl.get(url);
            if (parsed == null) throw new IllegalArgumentException("unexpected url: " + url);
            return url(parsed);
        }

        /**
         * Sets the header named {@code name} to {@code value}. If this request already has any headers
         * with that name, they are all replaced.
         */
        public Builder header(String name, String value) {
            headers.set(name, value);
            return this;
        }

        /**
         * Adds a header with {@code name} and {@code value}. Prefer this method for multiply-valued
         * headers like "Cookie".
         * 添加一个头部，包含 name 和 value
         * <p>Note that for some headers including {@code Content-Length} and {@code Content-Encoding},
         * OkHttp may replace {@code value} with a header derived(派生的) from the request body.
         */
        public Builder addHeaders(String name, String value){
            headers.add(name, value);
            return this;
        }

        public Builder removeHeader(String name){
            headers.removeAll(name);
            return this;
        }

        /** Removes all headers on this builder and adds {@code headers}. */
        public Builder headers(Headers headers){
            this.headers = headers.newBuilder();
            return this;
        }

        /**
         * Sets this request's {@code Cache-Control} header, replacing any cache control headers already
         * present(现在). If {@code cacheControl} doesn't define any directives(准则), this clears this request's
         * cache-control headers.
         */
        public Builder cacheControl(CacheControl cacheControl){
            String value = cacheControl.toString();
            if (value.isEmpty()) return removeHeader("Cache-Control");
            return  header("Cache-Control", value);
        }

        public Builder get() {
            return method("GET", null);
        }

        public Builder head() {
            return method("HEAD", null);
        }

        public Builder post(RequestBody body) {
            return method("POST", body);
        }

        public Builder delete(RequestBody body) {
            return method("DELETE", body);
        }

        public Builder delete() {
            return delete(RequestBody.create(null, new byte[0]));
        }

        public Builder put(RequestBody body) {
            return method("PUT", body);
        }

        public Builder patch(RequestBody body) {
            return method("PATCH", body);
        }

        public Builder method(String method, RequestBody body){
            if (method == null) throw new NullPointerException("method == null");
            if (method.length() == 0) throw new IllegalArgumentException("method.length() == 0");
            if (body != null && !HttpMethod.requiresRequestBody(method)) {
                throw new IllegalArgumentException("method " + method + " must not have a request body.");
            }
            if (body == null && HttpMethod.requiresRequestBody(method)){
                throw new IllegalArgumentException("method " + method + " must have a request body");
            }
            this.method = method;
            this.body = body;
            return this;
        }

        /**
         * Attaches {@code tag} to the request. It can be used later to cancel the request. If the tag
         * is unspecified or null, the request is canceled by using the request itself as the tag.
         * 作为tag，用于取消request，如果没有指定或为空，取消request只能使用itself
         */
        public Builder tag(Object tag){
            this.tag = tag;
            return this;
        }

        public Request build(){
            if (url == null) throw new IllegalStateException("url == null");
            return new Request(this);
        }
    }
}




























