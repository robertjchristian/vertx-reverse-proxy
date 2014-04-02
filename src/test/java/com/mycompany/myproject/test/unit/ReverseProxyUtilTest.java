package com.mycompany.myproject.test.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;

import org.junit.Test;

import com.mycompany.myproject.verticles.reverseproxy.MultipartUtil;
import com.mycompany.myproject.verticles.reverseproxy.ReverseProxyUtil;


public class ReverseProxyUtilTest {


	@Test
	public void testParseTokenFromQueryString() throws MalformedURLException, URISyntaxException {

		// test basic positive case
		URI uri = new URI("http://java.sun.com?forum=2");
		String result = ReverseProxyUtil.parseTokenFromQueryString(uri, "forum");
		assertTrue(result.equals("2"));

		uri = new URI("http://java.sun.com?forum=2&foo=bar");
		result = ReverseProxyUtil.parseTokenFromQueryString(uri, "foo");
		assertTrue(result.equals("bar"));

		// test basic negative case
		uri = new URI("http://java.sun.com?forum=2");
		result = ReverseProxyUtil.parseTokenFromQueryString(uri, "forumXYZ");
		assertNull(result);

	}

	@Test
	public void testMultipartUtil() {
		String signRequest = MultipartUtil.constructSignRequest("testBoundary", "authenticationToken", new Date().toString(), "test payload");
		assertEquals("testBoundary", MultipartUtil.getBoundary(signRequest));

		String invalidRequest = "{ \"invalid\": { \"payload\": \"data\" } }";
		assertNull(MultipartUtil.getBoundary(invalidRequest));

		String[] multiparts = MultipartUtil.parseMultipartRequest(signRequest, "testBoundary");
		assertEquals(5, multiparts.length);
		assertEquals("authenticationToken", multiparts[1].split("\n")[3]);
		assertEquals("test payload", multiparts[3].split("\n")[3]);
	}
}
