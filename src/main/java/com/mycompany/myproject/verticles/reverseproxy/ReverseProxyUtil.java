package com.mycompany.myproject.verticles.reverseproxy;

import com.google.gson.Gson;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.vertx.java.core.MultiMap;

import java.net.URI;
import java.util.HashMap;
import java.util.List;

/**
 * Reverse proxy utilities
 * <p/>
 *
 * @author <a href="https://github.com/robertjchristian">Robert Christian</a>
 */
public class ReverseProxyUtil {

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