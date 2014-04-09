package com.mycompany.myproject.verticles.reverseproxy.model.acl;

public class RootRequest {

	private AclRequest aclRequest;

	public RootRequest() {
		aclRequest = new AclRequest();
	}

	public AclRequest getAclRequest() {
		return aclRequest;
	}

	public void setAclRequest(AclRequest aclRequest) {
		this.aclRequest = aclRequest;
	}
}
