package com.mycompany.myproject.verticles.reverseproxy.model.acl;

import java.util.ArrayList;
import java.util.List;

public class Platform {

	private String name;
	private List<Domain> domains;

	public Platform() {
		this.domains = new ArrayList<Domain>();
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<Domain> getDomains() {
		return domains;
	}

	public void setDomains(List<Domain> domains) {
		this.domains = domains;
	}
}
