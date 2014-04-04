package com.mycompany.myproject.verticles.reverseproxy.configuration;

import java.util.Map;

public class ServiceDependencyConfiguration {

	public Map<String, ServiceDependency> serviceDependencyConfigs;


	public String getHost(String service) {
		for (String key : serviceDependencyConfigs.keySet()) {
			if (key.equals(service)) {
				return serviceDependencyConfigs.get(key).host;
			}
		}

		return null;
	}

	public Integer getPort(String service) {
		for (String key : serviceDependencyConfigs.keySet()) {
			if (key.equals(service)) {
				return serviceDependencyConfigs.get(key).port;
			}
		}

		return null;
	}

	public String getRequestPath(String service, String pathKey) {
		for (String key : serviceDependencyConfigs.keySet()) {
			if (key.equals(service)) {
				for (String existingPathKey : serviceDependencyConfigs.get(key).requestPaths.keySet()) {
					if (existingPathKey.equals(pathKey)) {
						return serviceDependencyConfigs.get(key).requestPaths.get(existingPathKey);
					}
				}
			}
		}

		return null;
	}
}
