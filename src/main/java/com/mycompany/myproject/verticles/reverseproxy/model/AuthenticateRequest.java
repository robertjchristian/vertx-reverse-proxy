package com.mycompany.myproject.verticles.reverseproxy.model;


public class AuthenticateRequest {

	private Authentication authentication;
	private String authenticationToken;

	public AuthenticateRequest() {
		this(new Authentication(), null);
	}

	public AuthenticateRequest(Authentication authentication, String authenticationToken) {
		this.authentication = authentication;
		this.authenticationToken = authenticationToken;
	}

	public Authentication getAuthentication() {
		return authentication;
	}

	public void setAuthentication(Authentication authentication) {
		this.authentication = authentication;
	}

	public String getAuthenticationToken() {
		return authenticationToken;
	}

	public void setAuthenticationToken(String authenticationToken) {
		this.authenticationToken = authenticationToken;
	}
}
