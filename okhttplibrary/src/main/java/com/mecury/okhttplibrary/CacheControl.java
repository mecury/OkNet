package com.mecury.okhttplibrary;

import com.mecury.okhttplibrary.internal.http.HttpHeaders;

import java.util.concurrent.TimeUnit;

/**
 * A Cache-Control header with cache directives(缓存准则) from a server or client. These directives set policy
 * on what responses can be stored, and which requests can be satisfied(满意) by those stored responses.
 * 一个缓存控制头伴随着来之 client 或者 server 的缓存准则，这些准则规定了哪些 responses 能够被存储 ，哪些请求
 * 能够得到对应的已缓存的 response
 *
 * <p>See <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.9">RFC 2616,
 * 14.9</a>.
 */
public final class CacheControl {
    public static final CacheControl FORCE_NETWORK = new Builder()
            .onlyIfCached()
            .maxStale(Integer.MAX_VALUE, TimeUnit.SECONDS)
            .build();

    private final boolean noCache;
    private final boolean noStore;
    private final int maxAgeSeconds;    //缓存的response的应答时间
    private final int sMaxAgeSeconds;
    private final boolean isPrivate;
    private final boolean isPublic;
    private final boolean mustRevalidate;   //Revalidate:重新验证
    private final int maxStaleSeconds;  //新鲜度
    private final int minFreshSeconds;  //最小保鲜时间
    private final boolean onlyIfCached; //只在缓冲中相应，否者504
    private final boolean noTransform;  //不接受改变过的response

    String headerValue; // Lazily computed, null if absent(不存在).

    public CacheControl(boolean noCache, boolean noStore, int maxAgeSeconds, int sMaxAgeSeconds,
                        boolean isPrivate, boolean isPublic, boolean mustRevalidate, int maxStaleSeconds,
                        int minFreshSeconds, boolean onlyIfCached, boolean noTransform, String headerValue) {
        this.noCache = noCache;
        this.noStore = noStore;
        this.maxAgeSeconds = maxAgeSeconds;
        this.sMaxAgeSeconds = sMaxAgeSeconds;
        this.isPrivate = isPrivate;
        this.isPublic = isPublic;
        this.mustRevalidate = mustRevalidate;
        this.maxStaleSeconds = maxStaleSeconds;
        this.minFreshSeconds = minFreshSeconds;
        this.onlyIfCached = onlyIfCached;
        this.noTransform = noTransform;
        this.headerValue = headerValue;
    }

    public CacheControl(Builder builder){
        this.noCache = builder.noCache;
        this.noStore = builder.noStore;
        this.maxAgeSeconds = builder.maxAgeSeconds;
        this.sMaxAgeSeconds = -1;
        this.isPrivate = false;
        this.isPublic = false;
        this.mustRevalidate = false;
        this.maxStaleSeconds = builder.maxStaleSeconds;
        this.minFreshSeconds = builder.minFreshSeconds;
        this.onlyIfCached = builder.onlyIfCached;
        this.noTransform = builder.noTransform;
    }

    /**
     * In a response, this field's name "no-cache" is misleading(误导). It doesn't prevent us from caching
     * the response; it only means we have to validate the response with the origin server before
     * returning it. We can do this with a conditional GET.
     * response中的”no-cache"代表的是不是用缓存中的response，request的结果必须是最新的server返回的response
     *
     * <p>In a request, it means do not use a cache to satisfy(满足) the request.
     */
    public boolean noCache(){
        return noCache;
    }

    /** If true, this response should not be cached. */
    public boolean noStore(){
        return noStore;
    }

    /**
     * The duration past the response's served date that it can be served without validation.
     * 一个缓存的response响应request的时间
     */
    public int maxAgeSeconds() {
        return maxAgeSeconds;
    }

    /**
     * The "s-maxage" directive(指令) is the max age for shared caches. Not to be confused with "max-age"
     * for non-shared caches, As in Firefox and Chrome, this directive is not honored by this cache.
     */
    public int sMaxAgeSeconds() {
        return sMaxAgeSeconds;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public boolean mustRevalidate() {
        return mustRevalidate;
    }

    public int maxStaleSeconds() {
        return maxStaleSeconds;
    }

    public int minFreshSeconds() {
        return minFreshSeconds;
    }

    /**
     * This field's name "only-if-cached" is misleading. It actually means "do not use the network".
     * It is set by a client who only wants to make a request if it can be fully satisfied by the
     * cache. Cached responses that would require validation (ie. conditional gets) are not permitted
     * if this header is set.
     */
    public boolean onlyIfCached() {
        return onlyIfCached;
    }

    public boolean noTransform() {
        return noTransform;
    }

    public static CacheControl parse(Headers headers){
        boolean noCache = false;
        boolean noStore = false;
        int maxAgeSeconds = -1;
        int sMaxAgeSeconds = -1;
        boolean isPrivate = false;
        boolean isPublic = false;
        boolean mustRevalidate = false;
        int maxStaleSeconds = -1;
        int minFreshSeconds = -1;
        boolean onlyIfCached = false;
        boolean noTransform = false;

        boolean canUseHeaderValue = true;
        String headerValue = null;

        for (int i = 0, size = headers.size(); i < size; i++){
            String name = headers.name(i);
            String value = headers.value(i);

            if (name.equalsIgnoreCase("Cache-Control")){
                if (headerValue != null){
                    // Multiple cache-control headers means we can't use the raw value(原始值，默认值).
                    canUseHeaderValue = false;
                }else{
                    headerValue = value;
                }
            }else if (name.equalsIgnoreCase("Pragma")){
                // Might specify additional cache-control params. We invalidate(无效) just in case.
                canUseHeaderValue = false;
            }else {
                continue;
            }

            //skipUntil()方法是 返回第一个String参数，第一次出现第三个参数字符串的位置，位置用pos表示
            int pos = 0;
            while (pos < value.length()){
                int tokenStart = pos;
                pos = HttpHeaders.skipUntil(value, pos, "=,;");
                String directive = value.substring(tokenStart, pos).trim();
                String parameter;

                if (pos == value.length() || value.charAt(pos) == ',' || value.charAt(pos) == ';'){
                    pos++;// consume ',' or ';' (if necessary)
                    parameter = null;
                }else {
                    pos++;  // consume '='
                    pos = HttpHeaders.skipWhitespace(value, pos);

                    // quoted string
                    if (pos < value.length() && value.charAt(pos) == '\"'){
                        pos++;  // consume '"' open quote
                        int parameterStart = pos;
                        pos = HttpHeaders.skipUntil(value, pos, String.valueOf('\"'));
                        parameter = value.substring(parameterStart, pos);
                        pos++;  // consume '"' close quote (if necessary)

                    }else{
                        int parameterStart = pos;
                        pos = HttpHeaders.skipUntil(value, pos, ",;");
                        parameter = value.substring(parameterStart, pos).trim();
                    }
                }

                if ("no-cache".equalsIgnoreCase(directive)) {
                    noCache = true;
                } else if ("no-store".equalsIgnoreCase(directive)) {
                    noStore = true;
                } else if ("max-age".equalsIgnoreCase(directive)) {
                    maxAgeSeconds = HttpHeaders.parseSeconds(parameter, -1);
                } else if ("s-maxage".equalsIgnoreCase(directive)) {
                    sMaxAgeSeconds = HttpHeaders.parseSeconds(parameter, -1);
                } else if ("private".equalsIgnoreCase(directive)) {
                    isPrivate = true;
                } else if ("public".equalsIgnoreCase(directive)) {
                    isPublic = true;
                } else if ("must-revalidate".equalsIgnoreCase(directive)) {
                    mustRevalidate = true;
                } else if ("max-stale".equalsIgnoreCase(directive)) {
                    maxStaleSeconds = HttpHeaders.parseSeconds(parameter, Integer.MAX_VALUE);
                } else if ("min-fresh".equalsIgnoreCase(directive)) {
                    minFreshSeconds = HttpHeaders.parseSeconds(parameter, -1);
                } else if ("only-if-cached".equalsIgnoreCase(directive)) {
                    onlyIfCached = true;
                } else if ("no-transform".equalsIgnoreCase(directive)) {
                    noTransform = true;
                }
            }
        }

        if (!canUseHeaderValue){
            headerValue = null;
        }
        return new CacheControl(noCache, noStore, maxAgeSeconds, sMaxAgeSeconds, isPrivate, isPublic,
                mustRevalidate, maxStaleSeconds, minFreshSeconds, onlyIfCached, noTransform, headerValue);
    }

    /**
     * 建造者模式
     */
    public static final class Builder {
        boolean noCache;
        boolean noStore;
        int maxAgeSeconds = -1;
        int maxStaleSeconds = -1;
        int minFreshSeconds = -1;
        boolean onlyIfCached;
        boolean noTransform;

        /** Don't accept an unvalidated cached response.
         *  不接受一个无效的缓存 response
         */
        public Builder noCache(){
            this.noCache = true;
            return this;
        }

        /** Don't store the server's response in any cache. */
        public Builder noStore(){
            this.noStore = true;
            return this;
        }

        /**
         * Sets the maximum age of a cached response. If the cache response's age exceeds {@code
         * maxAge}, it will not be used and a network request will be made.
         * 设置缓存响应的时间，如果超过这个时间缓存还没有响应，就会执行网络请求
         *
         * @param maxAge a non-negative integer. This is stored and transmitted with {@link
         * TimeUnit#SECONDS} precision; finer precision will be lost.
         */
        public Builder maxAge(int maxAge, TimeUnit timeUnit){
            if (maxAge < 0) throw new IllegalArgumentException("maxAge < 0: " + maxAge);
            long maxAgeSecondsLong = timeUnit.toSeconds(maxAge); //将mexAge转为分钟,Integer.MAX_VALUE:2147483647
            this.maxAgeSeconds = maxAgeSecondsLong > Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : (int) maxAgeSecondsLong;
            return this;
        }

        /**
         * Accept cached responses that have exceeded their freshness lifetime by up to {@code
         * maxStale}. If unspecified(未指明的), stale(不新鲜的) cache responses will not be used.
         * 缓存的response都有一个保鲜期，随着时间的流逝，maxStale会升高，如果过了保鲜期，缓存的responses将不会被使用
         *
         * @param maxStale a non-negative integer. This is stored and transmitted with {@link
         * TimeUnit#SECONDS} precision(精确的); finer precision will be lost.
         */
        public Builder maxStale(int maxStale, TimeUnit timeUnit){
            if (maxStale < 0) throw new IllegalArgumentException("maxStale < 0:" + maxStale);
            long maxStaleSecondsLong = timeUnit.toSeconds(maxStale);
            this.maxStaleSeconds = maxStaleSecondsLong > Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : (int) maxStaleSecondsLong;
            return this;
        }

        /**
         * Sets the minimum number of seconds that a response will continue to be fresh for. If the
         * response will be stale when {@code minFresh} have elapsed(经过，流逝), the cached response will not be
         * used and a network request will be made.
         * 设置response的保鲜时间，当新鲜度（stale）超过最小保鲜时间(minFresh)，这个缓存的reponse将会不再使用，
         * 网络请求将会执行
         *
         * @param minFresh a non-negative integer. This is stored and transmitted with {@link
         * TimeUnit#SECONDS} precision; finer precision will be lost.
         */
        public Builder minFresh (int minFresh, TimeUnit timeUnit){
            if (minFresh < 0) throw new IllegalArgumentException("minfresh < 0:" + minFresh);
            long minFreshScondsLong = timeUnit.toSeconds(minFresh);
            this.minFreshSeconds = minFreshScondsLong > Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : (int) minFreshScondsLong;
            return this;
        }

        /**
         * Only accept the response if it is in the cache. If the response isn't cached, a {@code 504
         * Unsatisfiable Request} response will be returned.
         * 只缓存已经缓存过的 response ，如果response没有被缓存， 将会返回 504
         */
        public Builder onlyIfCached(){
            this.onlyIfCached = true;
            return this;
        }

        /** Don't accept a transformed(改变) response. */
        public Builder onTransform(){
            this.noTransform = true;
            return this;
        }

        public CacheControl build(){
            return new CacheControl(this);
        }
    }
}


































