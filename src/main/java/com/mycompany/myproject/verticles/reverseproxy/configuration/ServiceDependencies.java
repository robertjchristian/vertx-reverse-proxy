package com.mycompany.myproject.verticles.reverseproxy.configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Used by Configuration
 *
 * @author hpark
 */
public class ServiceDependencies {

    public Map<String, ServiceDescriptor> dependencies = new HashMap<>();

    public String getHost(String service) {
        for (String key : dependencies.keySet()) {
            if (key.equals(service)) {
                return dependencies.get(key).host;
            }
        }

        return null;
    }

    public Integer getPort(String service) {
        for (String key : dependencies.keySet()) {
            if (key.equals(service)) {
                return dependencies.get(key).port;
            }
        }

        return null;
    }

    public String getRequestPath(String service, String pathKey) {
        for (String key : dependencies.keySet()) {
            if (key.equals(service)) {
                for (String existingPathKey : dependencies.get(key).requestPaths.keySet()) {
                    if (existingPathKey.equals(pathKey)) {
                        return dependencies.get(key).requestPaths.get(existingPathKey);
                    }
                }
            }
        }

        return null;
    }
}
