package com.mycompany.myproject.verticles.reverseproxy.model;


public class AuthenticateRequest {

	private Authentication authentication;

	public AuthenticateRequest() {
		this.authentication = new Authentication();
	}

	public AuthenticateRequest(Authentication authentication) {
		this.authentication = authentication;
	}

	public Authentication getAuthentication() {
		return authentication;
	}

	public void setAuthentication(Authentication authentication) {
		this.authentication = authentication;
	}
}
