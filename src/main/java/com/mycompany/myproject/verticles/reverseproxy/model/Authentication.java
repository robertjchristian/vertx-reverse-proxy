package com.mycompany.myproject.verticles.reverseproxy.model;

import java.util.ArrayList;
import java.util.List;

public class Authentication {

	private List<AuthRequest> authRequestList;

	public Authentication() {
		authRequestList = new ArrayList<AuthRequest>();
	}

	public Authentication(List<AuthRequest> authRequestList) {
		this.authRequestList = authRequestList;
	}

	public List<AuthRequest> getAuthRequestList() {
		return authRequestList;
	}

	public void setAuthRequestList(List<AuthRequest> authRequestList) {
		this.authRequestList = authRequestList;
	}
}