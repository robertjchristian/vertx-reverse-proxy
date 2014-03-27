package com.mycompany.myproject.test.mock.auth.model;

import java.util.Date;

public class Response {

	private String message;
	private String status;
	private String authentication;
	private String authenticationToken;
	private Date sessionDate;

	public Response(String message, String status, String authentication, String authenticationToken, Date sessionDate) {
		super();
		this.message = message;
		this.status = status;
		this.authentication = authentication;
		this.authenticationToken = authenticationToken;
		this.sessionDate = sessionDate;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getAuthentication() {
		return authentication;
	}

	public void setAuthentication(String authentication) {
		this.authentication = authentication;
	}

	public String getAuthenticationToken() {
		return authenticationToken;
	}

	public void setAuthenticationToken(String authenticationToken) {
		this.authenticationToken = authenticationToken;
	}

	public Date getSessionDate() {
		return sessionDate;
	}

	public void setSessionDate(Date sessionDate) {
		this.sessionDate = sessionDate;
	}
}
