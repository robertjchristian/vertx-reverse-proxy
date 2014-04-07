package com.mycompany.myproject.verticles.reverseproxy.model.acl;

import java.util.ArrayList;
import java.util.List;

public class AclRequest {

	private List<Platform> platforms;

	public AclRequest() {
		this.platforms = new ArrayList<Platform>();
	}

	public List<Platform> getPlatforms() {
		return platforms;
	}

	public void setPlatforms(List<Platform> platforms) {
		this.platforms = platforms;
	}
}
