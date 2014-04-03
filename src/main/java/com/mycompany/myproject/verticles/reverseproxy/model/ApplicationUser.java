package com.mycompany.myproject.verticles.reverseproxy.model;

import java.util.ArrayList;
import java.util.List;

public class ApplicationUser {

	private String username;
	private String organization;
	private List<String> roles;

	public ApplicationUser() {
		this(null, null);
	}

	public ApplicationUser(List<String> roles) {
		this(null, null, roles);
	}

	public ApplicationUser(String username, String organization) {
		this(username, organization, new ArrayList<String>());
	}

	public ApplicationUser(String username, String organization, List<String> roles) {
		this.username = username;
		this.organization = organization;
		this.roles = roles;
	}

	public String getUser() {
		return username;
	}

	public void setUser(String username) {
		this.username = username;
	}

	public String getOrganization() {
		return organization;
	}

	public void setOrganization(String organization) {
		this.organization = organization;
	}

	public List<String> getRoles() {
		return roles;
	}

	public void setRoles(List<String> roles) {
		this.roles = roles;
	}


}
