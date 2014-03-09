package com.mycompany.myproject;

import com.google.gson.Gson;
import com.mycompany.myproject.configuration.Configuration;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.vertx.java.core.MultiMap;
import org.vertx.java.platform.Container;

import java.net.URI;
import java.util.HashMap;
import java.util.List;

/**
 * robertjchristian
 */
public class ReverseProxyUtil {

    // TODO want to pick these up dynamically
    public static Configuration getConfiguration(Container container) {
        Gson g = new Gson();
        final String rawConfig = container.config().toString();
        final Configuration config = g.fromJson(rawConfig, Configuration.class);
        return config;
    }

    public static String getHeadersAsJSON(MultiMap headers, String filter) {
        java.util.Map<String, String> matched = new HashMap<String, String>();
        for (String key : headers.names()) {
            if (key.equals(filter)) {
                matched.put(key, headers.get(key));
            }
        }
        return new Gson().toJson(matched);
    }

    public static String getHeadersAsJSON(MultiMap headers) {
        return new Gson().toJson(headers);
    }

    public static String getCookieHeadersAsJSON(MultiMap headers) {
       return getHeadersAsJSON(headers, "Cookie");
    }

    public static String getCookieValue(MultiMap headers, String name) {

        for (String key : headers.names()) {
            if (key.equals("Cookie")) {
                String headerValue = headers.get(key);
                System.out.println("Cookie --> " + headerValue);
                if (headerValue.startsWith(name)) {
                    String cookieValue = headerValue.replace(name + "=", "");
                    return cookieValue;
                }
            }
        }
        return null;
    }

    public static String parseTokenFromQueryString(URI uri, String key) {
        List<NameValuePair> nameValuePairs = URLEncodedUtils.parse(uri, "utf-8");
        for (NameValuePair nvp : nameValuePairs) {
            if (nvp.getName().equals(key)) {
                return nvp.getValue();
            }
        }
        return null;
    }


}


/*
  // target server
        vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
            public void handle(final HttpServerRequest req) {

                System.out.println("Target server processing request: " + req.uri());
                req.response().setStatusCode(200);
                req.response().setChunked(true);
                req.response().write("foo");
                req.response().end();
            }
        }).listen(8282);

*/