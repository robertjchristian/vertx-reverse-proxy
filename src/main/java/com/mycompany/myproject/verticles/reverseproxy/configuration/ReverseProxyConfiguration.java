package com.mycompany.myproject.verticles.reverseproxy.configuration;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Configuration
 *
 * @author Robert Christian
 */
public class ReverseProxyConfiguration {


    public SSL ssl;  // TODO split into client and server (client should be able to change truststore location/pass dynamically)
    public Map<String, RewriteRule> rewriteRules;
    public String[] assets;

    public ReverseProxyConfiguration() {

        // "assets" may be accessed without authentication/acl
        assets = new String[] {"ico, png, jpg, jpeg, gif, css, js, txt"};

        ssl = new SSL();

        // ssl (as client)
        ssl.trustStorePath = "../../../server-truststore.jks";
        ssl.trustStorePassword = "password";

        // ssl (as server)
        ssl.proxyHttpsPort = 8989;
        ssl.keyStorePath = "../../../server-keystore.jks";
        ssl.keyStorePassword = "password";

        // rewrite rules
        rewriteRules = new HashMap<String, RewriteRule>();
        rewriteRules.put("sn", new RewriteRule("http", "localhost", 8080));
        rewriteRules.put("acl", new RewriteRule("http", "localhost", 9001));
        rewriteRules.put("um", new RewriteRule("http", "localhost", 9000));
        rewriteRules.put("google", new RewriteRule("http", "google.com", 80));

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

    }

}
