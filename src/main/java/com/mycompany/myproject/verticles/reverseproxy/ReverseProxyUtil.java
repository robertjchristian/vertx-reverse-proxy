package com.mycompany.myproject.verticles.reverseproxy;

import java.net.URI;
import java.util.HashMap;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.json.impl.Base64;

import com.google.gson.Gson;

/**
 * Reverse proxy utilities
 * <p/>
 *
 * @author <a href="https://github.com/robertjchristian">Robert Christian</a>
 */
public class ReverseProxyUtil {

	private static final String DEFAULT_COOKIE_DELIMITER = ";";

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
				for (String cookie : headerValue.split(DEFAULT_COOKIE_DELIMITER)) {
					if (cookie.trim().startsWith(name)) {
						String cookieValue = cookie.trim().replace(name + "=", "");
						return cookieValue;
					}
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

	public static String[] getAuthFromBasicAuthHeader(MultiMap headers) {
		String basicAuthHeader = headers.get("Authorization");
		if (basicAuthHeader != null && !basicAuthHeader.isEmpty()) {
			String parsedAuthInfo = basicAuthHeader.replace("Basic", "").trim();
			String decodedAuthInfo = new String(Base64.decode(parsedAuthInfo));
			String[] auth = decodedAuthInfo.split(":");

			if (auth != null && auth.length == 2) {
				return auth;
			}
		}

		return null;
	}

	public static String padWhitespace(String str) {
		while ((str.length() % 16) != 0) {
			str = str.concat(" ");
		}

		return str;
	}
}