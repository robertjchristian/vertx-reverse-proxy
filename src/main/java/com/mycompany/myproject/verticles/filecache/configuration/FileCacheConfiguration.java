package com.mycompany.myproject.verticles.filecache.configuration;

import com.google.gson.Gson;

/**
 * Configuration
 *
 * @author robertjchristian
 */
public class FileCacheConfiguration {

    // TODO encapsulate
    public long refreshMillis = 300000L; // refresh every 5 minutes by default
    public String baseDir = "."; // default base dir to "."

    public static void main(String[] args) {

        Gson g = new Gson();

        // test from pojo
        FileCacheConfiguration configurationA = new FileCacheConfiguration();
        System.out.println(g.toJson(configurationA));

        // test to pojo
        String rawConfig = "{\"refreshMillis\":300000,\"baseDir\":\".\"}";
        FileCacheConfiguration configurationB = g.fromJson(rawConfig, FileCacheConfiguration.class);

        // and back to string...
        configurationB.baseDir = "/";
        System.out.println(g.toJson(configurationB));

    }

}
