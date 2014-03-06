package com.mycompany.myproject;

import org.vertx.java.core.http.HttpServerRequest;

/**
 * robertjchristian
 */
public class ReverseProxyUtil {

    // debug
    public static void printHeaders(HttpServerRequest req) {
        for (String key : req.headers().names()) {
            System.out.println(key + " --> " + req.headers().get(key));
        }
    }

    // debug
    public static void printHeaders(HttpServerRequest req, String filter) {
        for (String key : req.headers().names()) {
            if (key.equals(filter)) {
                System.out.println(key + " --> " + req.headers().get(key));
            }
        }
    }

    // debug
    public static void printCookies(HttpServerRequest req) {
        printHeaders(req, "Cookie");
    }

    public static String getCookieValue(HttpServerRequest req, String name) {
        for (String key : req.headers().names()) {
            if (key.equals("Cookie")) {

                String headerValue = req.headers().get(key);

                System.out.println("headerValue -->" + headerValue);

                if (headerValue.startsWith(name)) {
                    String cookieValue = headerValue.replace(name + "=", "");
                    return cookieValue;
                }
                System.out.println(key + " --> " + req.headers().get(key));
            }
        }
        return null;
    }


}
