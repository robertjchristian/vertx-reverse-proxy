package com.mycompany.myproject.verticles.reverseproxy.configuration;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

/**
 * Reverse Proxy Configuration
 *
 * @author robertjchristian
 */
public class ReverseProxyConfiguration {

	enum BinaryPrefix {
		k, m, g
	}

	public SSL ssl;
	public Map<String, RewriteRule> rewriteRules;
	public String[] assets;
	public String defaultService;
	public String maxPayloadSizeBytes;
	public ServiceDependencies serviceDependencies;
	public String resourceRoot;
	public String webRoot;

	public ReverseProxyConfiguration() {

		// "assets" may be accessed without authentication/acl
		assets = new String[] { "ico, png, jpg, jpeg, gif, css, js, txt" };

		ssl = new SSL();

		// ssl (as client)
		ssl.trustStorePath = "../../../server-truststore.jks";
		ssl.trustStorePassword = "password";

		// ssl (as server)
		ssl.proxyHttpsPort = 8989;
		ssl.keyStorePath = "../../../server-keystore.jks";
		ssl.keyStorePassword = "password";

		// service dependencies
		serviceDependencies = new ServiceDependencies();
		Map<String, String> paths = new HashMap<>();
		paths.put("auth", "/auth");
		serviceDependencies.dependencies.put("auth", new ServiceDescriptor("localhost", 8000, paths));

		// rewrite rules
		rewriteRules = new HashMap<String, RewriteRule>();
		rewriteRules.put("sn", new RewriteRule("http", "localhost", 8080));
		rewriteRules.put("acl", new RewriteRule("http", "localhost", 9001));
		rewriteRules.put("um", new RewriteRule("http", "localhost", 9000));
		rewriteRules.put("google", new RewriteRule("http", "google.com", 80));

	}

	public long getMaxPayloadSizeBytesInNumber() {

		if (maxPayloadSizeBytes.matches("\\d*[a-z]")) {
			String binaryPrefix = maxPayloadSizeBytes.substring(maxPayloadSizeBytes.length() - 1, maxPayloadSizeBytes.length());
			String payloadSize = maxPayloadSizeBytes.substring(0, maxPayloadSizeBytes.length() - 1);
			switch (binaryPrefix) {
			case "k":
				return Long.valueOf(payloadSize) * 1024;
			case "m":
				return Long.valueOf(payloadSize) * 1024 * 1024;
			case "g":
				return Long.valueOf(payloadSize) * 1024 * 1024 * 1024;
			default:
				return 512 * 1024;
			}
		}
		else if (maxPayloadSizeBytes.matches("\\d*")) {
			return Long.valueOf(maxPayloadSizeBytes);
		}
		// did not match expected pattern, return default value 512k
		else {
			return 512 * 1024;
		}
	}

	public static void main(String[] args) {

		Gson g = new Gson();

		// test from pojo
		ReverseProxyConfiguration configurationA = new ReverseProxyConfiguration();
		System.out.println(g.toJson(configurationA));

		// test to pojo
		String rawConfig = "{\"ssl_client\":{\"trustStorePath\":\"../../../server-truststore.jks\",\"trustStorePassword\":\"password\"},\"ssl_server\":{\"keyStorePath\":\"../../../server-keystore.jks\",\"proxyHttpsPort\":\"8989\",\"keyStorePassword\":\"password\"},\"rewrite_rules\":{\"sn\":{\"protocol\":\"um\",\"host\":\"localhost\",\"port\":9000},\"google\":{\"protocol\":\"http\",\"host\":\"google.com\",\"port\":80}}}";
		ReverseProxyConfiguration configurationB = g.fromJson(rawConfig, ReverseProxyConfiguration.class);

		// and back to string...
		configurationB.rewriteRules.put("bing", new RewriteRule("http", "bing.com", 80));
		configurationB.rewriteRules.remove("google");
		System.out.println(g.toJson(configurationB));

		configurationB.maxPayloadSizeBytes = "128k";
		System.out.println(configurationB.getMaxPayloadSizeBytesInNumber());
	}
}
