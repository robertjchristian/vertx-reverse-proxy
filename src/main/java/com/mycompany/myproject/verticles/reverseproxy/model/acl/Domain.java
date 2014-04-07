package com.mycompany.myproject.verticles.reverseproxy.model.acl;

import java.util.ArrayList;
import java.util.List;

public class Domain {

	private String type;
	private String name;
	private List<String> roles;

	public Domain() {
		this.roles = new ArrayList<String>();
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<String> getRoles() {
		return roles;
	}

	public void setRoles(List<String> roles) {
		this.roles = roles;
	}
}
