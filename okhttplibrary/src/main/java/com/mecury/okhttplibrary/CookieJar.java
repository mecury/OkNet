package com.mecury.okhttplibrary;

import java.util.Collections;
import java.util.List;

/**
 * Provides <strong>policy</strong> and <strong>persistence</strong> for HTTP cookies.
 * 为Http cookies 提供 政策 和 持久性
 *
 * <p>As policy, implementations of this interface are responsible for selecting which cookies to
 * accept and which to reject. A reasonable policy is to reject all cookies, though that may be
 * interfere with session-based authentication schemes that require cookies.
 *
 * <p>As persistence, implementations of this interface must also provide storage of cookies. Simple
 * implementations may store cookies in memory; sophisticated ones may use the file system or
 * database to hold accepted cookies. The <a
 * href="https://tools.ietf.org/html/rfc6265#section-5.3">cookie storage model</a> specifies
 * policies for updating and expiring cookies.
 */
public interface CookieJar {
    /** A cookie jar that never accepts any cookies. */
    CookieJar NO_COOKIES = new CookieJar() {
        @Override public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        }

        @Override public List<Cookie> loadForRequest(HttpUrl url) {
            return Collections.emptyList();
        }
    };

    /**
     * Saves {@code cookies} from an HTTP response to this store according to this jar's policy.
     * 根据 policy 保存 response 中的cookies
     *
     * <p>Note that this method may be called a second time for a single HTTP response if the response
     * includes a trailer. For this obscure HTTP feature, {@code cookies} contains only the trailer's
     * cookies.
     */
    void saveFromResponse(HttpUrl url, List<Cookie> cookies);

    /**
     * Load cookies from the jar for an HTTP request to {@code url}. This method returns a possibly
     * empty list of cookies for the network request.
     * 为一个request 由 jar 中加载cookie。这个方法可能返回一个空的 cookie 列表
     *
     * <p>Simple implementations will return the accepted cookies that have not yet expired and that
     * {@linkplain Cookie#matches match} {@code url}.
     */
    List<Cookie> loadForRequest(HttpUrl url);
}
