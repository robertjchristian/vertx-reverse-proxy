package com.mycompany.myproject.verticles.reverseproxy;

import static com.mycompany.myproject.verticles.reverseproxy.ReverseProxyVerticle.webRoot;

import java.net.URI;
import java.util.HashMap;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.impl.Base64;
import org.vertx.java.core.logging.Logger;

import com.google.gson.Gson;
import com.mycompany.myproject.verticles.filecache.FileCacheUtil;

/**
 * Reverse proxy utilities
 * <p/>
 *
 * @author robertjchristian
 */
public class ReverseProxyUtil {

	private static final String DEFAULT_COOKIE_DELIMITER = ";";
	public static final String AUTH_ERROR_TEMPLATE_PATH = "auth/authError.html";

	/**
	 * Sends a failure with message and ends connection.
	 *
	 * @param req - HTTP Request
	 * @param msg - Message returned to client
	 */
	public static void sendFailure(Logger log, final HttpServerRequest req, final String msg) {
		log.error(msg);
		req.response().setChunked(true);
		req.response().setStatusCode(500);
		req.response().setStatusMessage("Internal Server Error.");
		req.response().write(msg);
		req.response().end();
	}


	public static void send200OKResponse(Logger log, final HttpServerRequest req, final Buffer response, final String contentType) {
		req.response().putHeader("content-type", contentType);
		req.response().setChunked(true);
		req.response().setStatusCode(200);
		req.response().setStatusMessage("OK");
		req.response().write(response);
		req.response().end();
	}

	public static void sendAuthError(Logger log, final Vertx vertx, final HttpServerRequest req, final int statusCode, final String msg) {
		FileCacheUtil.readFile(vertx.eventBus(), log, webRoot + AUTH_ERROR_TEMPLATE_PATH, new AsyncResultHandler<byte[]>() {

			@Override
			public void handle(AsyncResult<byte[]> data) {
				String template = new String(data.result());
				String templateWithErrorMessage = template.replace("{{error}}", msg);

				req.response().setChunked(true);
				req.response().setStatusCode(statusCode);
				req.response().write(templateWithErrorMessage);
				req.response().end();
			}

		});
	}

	public static <T> T getConfig(final Class<T> clazz, final byte[] fileContents) {
		// TODO mind encoding
		String fileAsString = new String(fileContents);

		Gson g = new Gson();
		T c = g.fromJson(fileAsString, clazz);
		return c;
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

	public static boolean isNullOrEmptyAfterTrim(String str) {
		if (str != null) {
			if (!str.trim().isEmpty()) {
				return false;
			}
		}

		return true;
	}
}