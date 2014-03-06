package com.mycompany.myproject.configuration;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

/**
 * Serializable Configuration
 *
 * This is the POJO representation of conf.json.  When changing the configuration
 * model, please do so here, then output an example and update the conf.json appropriately.
 *
 * The application will fail to bootstrap if "Configuration" cannot be built from conf.json.
 *
 */
public class Configuration {

    private final Map<String, RewriteRule> rewriteRules;

    public Map<String, RewriteRule> getRewriteRules() {
        return rewriteRules;
    }

    public Configuration(Map<String, RewriteRule> rewriteRules) {
        this.rewriteRules = rewriteRules;

    }


    public static Configuration buildExampleConfiguration() {
        HashMap<String, RewriteRule> rewriteRules = new HashMap<String, RewriteRule>();
        rewriteRules.put("google", new RewriteRule("http", "google.com", 8080));
        rewriteRules.put("yahoo", new RewriteRule("http", "yahoo.com", 8080));
        rewriteRules.put("bing", new RewriteRule("http", "bing.com", 8080));
        return new Configuration(rewriteRules);
    }

    public static void main(String[] args) {
        // generate an example json configuration
        System.out.println(new Gson().toJson(buildExampleConfiguration()));
    }


}