package com.mycompany.myproject.verticles.reverseproxy.util;

import java.util.List;

import com.google.gson.Gson;
import com.mycompany.myproject.verticles.reverseproxy.model.acl.Domain;
import com.mycompany.myproject.verticles.reverseproxy.model.acl.Platform;
import com.mycompany.myproject.verticles.reverseproxy.model.acl.RootRequest;


/**
 * Multipart Util for Auth Request
 *
 * @author HPark
 */
public class MultipartUtil {

	private MultipartUtil() {

	}

	public static String constructSignRequest(String boundary, String authenticationToken, String sessionDate, String payload) {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("Content-Type: multipart/form-data; boundary=%s\n\n--%s\n", boundary, boundary));
		sb.append(String.format("Content-Disposition: form-data; name=\"AUTHENTICATION_TOKEN\"\n\n%s\n--%s\n", authenticationToken, boundary));
		sb.append(String.format("Content-Disposition: form-data; name=\"UM_SESSION_DATE\"\n\n%s\n--%s\n", sessionDate, boundary));
		sb.append(String.format("Content-Disposition; form-data; name=\"ORIGINAL_PAYLOAD\"\n\n%s\n--%s--", payload, boundary));

		return sb.toString();
	}

	public static String constructManifestRequest(String boundary, String unsignedDocument, String signedDocument, String aclManifest) {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("Content-Type: multipart/form-data; boundary=%s\n\n--%s\n", boundary, boundary));
		sb.append(String.format("%s\n--%s\n", unsignedDocument, boundary));
		sb.append(String.format("Content-Type: text/plain\n\n%s\n--%s\n", signedDocument, boundary));
		sb.append(String.format("Content-Type: text/plain\n\n%s\n--%s--", aclManifest, boundary));

		return sb.toString();
	}

	public static String constructAclRequest(String domainName, List<String> roles) {

		RootRequest rootRequest = new RootRequest();

		Platform platform = new Platform();

		// will platform name always be 'Service-Broker' for SP ?
		platform.setName("Service-Broker");

		Domain domain = new Domain();
		domain.setType("Service");
		domain.setName(domainName);
		domain.setRoles(roles);

		platform.getDomains().add(domain);
		rootRequest.getAclRequest().getPlatforms().add(platform);

		return new Gson().toJson(rootRequest);
	}

	public static String getBoundary(String request) {
		String[] lines = request.split("\n");

		if (lines.length > 0) {
			// first line of the request should have boundary info
			String[] headerInfo = lines[0].split(";");
			if (headerInfo.length > 1) {
				String boundary = lines[0].split(";")[1];
				return boundary.replace("boundary=", "").trim();
			}
		}

		System.out.println("Boundary Not Found");
		return null;
	}

	public static String[] parseMultipartRequest(String request, String boundaryStr) {
		String boundary = "--" + boundaryStr;
		return request.split(boundary);
	}
}
