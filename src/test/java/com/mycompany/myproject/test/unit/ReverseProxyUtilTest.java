package com.mycompany.myproject.test.unit;

import com.mycompany.myproject.verticles.reverseproxy.ReverseProxyUtil;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


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

}
