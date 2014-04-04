package com.mycompany.myproject.verticles.reverseproxy.configuration;

import java.util.Map;

public class ServiceDependency {

	public String host;
	public Integer port;
	public Map<String, String> requestPaths;
}
