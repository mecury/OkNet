package com.mecury.okhttplibrary.internal.connection;

import com.mecury.okhttplibrary.Route;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A blacklist of failed routes to avoid when creating a new connection to a target address. This is
 * used so that OkHttp can learn from its mistakes: if there was a failure attempting to connect to
 * a specific IP address or proxy server, that failure is remembered and alternate(备用) routes are
 * preferred(首选).
 * 创建连接时的失败路线的黑名单。这是为了OkHttp能够由失败中学习：如果这是失败的尝试去来凝结一个明确的地址或者代理服务器，
 * 失败的信息会被保存而且备用的路线会作为首选
 */
public class RouteDatabase {

    private final Set<Route> failedRoutes = new LinkedHashSet<>();

    /** Records a failure connecting to {@code failedRoute}. */
    public synchronized void failed(Route failedRoute){
        failedRoutes.add(failedRoute);
    }

    /** Records success connecting to {@code failedRoute}. */
    public synchronized void connected(Route route){
        failedRoutes.remove(route);
    }

    /** Returns true if {@code route} has failed recently and should be avoided. */
    public synchronized boolean shouldPostpone(Route route){
        return failedRoutes.contains(route);
    }
}
