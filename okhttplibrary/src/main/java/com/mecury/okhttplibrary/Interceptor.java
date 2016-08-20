package com.mecury.okhttplibrary;

import java.io.IOException;

/**
 * Observes, modifies, and potentially short-circuits requests going out and the corresponding
 * responses coming back in. Typically interceptors add, remove, or transform headers on the request
 * or response.
 * 观察，修改 和 潜在的短路的 request 发送出去，相应的 接收 response 添加，删除 或移动 典型的 interceptor
 * 对request 或 response
 */
public interface Interceptor {
     Response intercept(Chain chain) throws IOException;

    interface Chain {
        Request request();

        Response proceed(Request request) throws IOException;

        Connection connection();
    }
}
