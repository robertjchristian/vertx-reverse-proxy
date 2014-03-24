package com.mycompany.myproject.test.integration.java;/*
* Copyright 2013 Red Hat, Inc.
*
* Red Hat licenses this file to you under the Apache License, version 2.0
* (the "License"); you may not use this file except in compliance with the
* License.  You may obtain a copy of the License at:
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
* WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
* License for the specific language governing permissions and limitations
* under the License.
*
* @author <a href="http://tfox.org">Tim Fox</a>
*/

import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.JsonObject;
import org.vertx.testtools.TestVerticle;

import static org.vertx.testtools.VertxAssert.*;

/**
 * Example Java integration test that deploys the module that this project builds.
 * <p/>
 * Quite often in integration tests you want to deploy the same module for all tests and you don't want tests
 * to start before the module has been deployed.
 * <p/>
 * This test demonstrates how to do that.
 */
public class ModuleIntegrationTest extends TestVerticle {

	// TODO pick up config on the fly (changes currently require restart of verticle)

	@Test
	public void testProxyServer() {

		container.logger().info("Testing proxy server...");

		try {
			Thread.sleep(5000);
		}
		catch (InterruptedException e) {
			// do nothing
		}

		// create ssl client. trust all for testing purpose
		final HttpClient client = vertx.createHttpClient()
				.setSSL(true)
				.setTrustAll(true)
				.setVerifyHost(false)
				.setHost("localhost")
				.setPort(8989)
				.setConnectTimeout(5);

		HttpClientRequest request = client.get("/google", new Handler<HttpClientResponse>() {
			public void handle(final HttpClientResponse resp) {

				// WARN:  Body Handler consumes to memory first... so large enough responses
				// will break this... http://vertx.io/core_manual_java.html
				resp.bodyHandler(new Handler<Buffer>() {
					public void handle(Buffer body) {

						// The entire response body has been received
						//container.logger().info("The total body received was " + body.length() + " bytes");
						container.logger().info("Got a response: \n" + resp.statusCode() + " " + resp.statusMessage() + " - \n" + body.toString());

						// close the client
						client.close();

						// tell event bus we are done
						testComplete();

					}
				});

			}
		});
		request.end();
	}

	@Test
	public void testUserManagementServerAuth() {
		container.logger().info("Testing authentication in UserManagement server...");

		final HttpClient client = vertx.createHttpClient().setHost("localhost").setPort(9000).setConnectTimeout(5);

		HttpClientRequest request = client.post("/authenticate", new Handler<HttpClientResponse>() {
			public void handle(final HttpClientResponse resp) {

				resp.bodyHandler(new Handler<Buffer>() {
					public void handle(Buffer body) {

						container.logger().info("Got a response: \n" + resp.statusCode() + " " + resp.statusMessage() + " - \n" + body.toString());
						client.close();
						testComplete();

					}
				});

			}
		});
		// sample basic auth header
		request.putHeader("Authorization", "Basic ZGVtb2ZvcmFjbEBsaWFpc29uLmRldjpVMjl1YW1GMllURXlNdz09");
		request.end();
	}

	@Test
	public void testUserManagementServerSign() {
		container.logger().info("Testing signing in UserManagement server...");

		final HttpClient client = vertx.createHttpClient().setHost("localhost").setPort(9000).setConnectTimeout(5);

		HttpClientRequest request = client.post("/sign", new Handler<HttpClientResponse>() {
			public void handle(final HttpClientResponse resp) {

				resp.bodyHandler(new Handler<Buffer>() {
					public void handle(Buffer body) {

						container.logger().info("Got a response: \n" + resp.statusCode() + " " + resp.statusMessage() + " - \n" + body.toString());
						client.close();
						testComplete();

					}
				});

			}
		});
		String content = vertx.fileSystem().readFileSync("usermanagement/payload_sign_request.txt").toString();
		request.setChunked(true);
		request.write(content);
		request.end();
	}

	@Test
	public void testAclServerGetManifest() {
		container.logger().info("Testing getting manifest from Mock ACL server...");

		final HttpClient client = vertx.createHttpClient().setHost("localhost").setPort(9001).setConnectTimeout(5);

		HttpClientRequest request = client.post("/verifyAndGetManifest", new Handler<HttpClientResponse>() {
			public void handle(final HttpClientResponse resp) {

				resp.bodyHandler(new Handler<Buffer>() {
					public void handle(Buffer body) {

						container.logger().info("Got a response: \n" + resp.statusCode() + " " + resp.statusMessage() + " - \n" + body.toString());
						client.close();
						testComplete();

					}
				});

			}
		});
		String content = vertx.fileSystem().readFileSync("usermanagement/payload_sign_request.txt").toString();
		request.setChunked(true);
		request.write(content);
		request.end();
	}

	@Override
	public void start() {

		// Make sure we call initialize() - this sets up the assert stuff so assert functionality works correctly
		initialize();

		// Deploy the module - the System property `vertx.modulename` will contain the name of the module so you
		// don't have to hardecode it in your tests

		JsonObject config = new JsonObject();
		config.putNumber("proxyHttpsPort", 8989);
		// these configurations need to be changed if there are changes in conf.json 
		config.putString("configFilePath", "../../../conf.json");
		config.putString("keyStorePath", "../../../server-keystore.jks");
		config.putString("keyStorePassword", "password");

		container.deployVerticle("com.mycompany.myproject.mock.UserManagementVerticle");
		container.deployVerticle("com.mycompany.myproject.mock.AclVerticle");

		container.deployModule(System.getProperty("vertx.modulename"), config, new AsyncResultHandler<String>() {
			@Override
			public void handle(AsyncResult<String> asyncResult) {
				// Deployment is asynchronous and this this handler will be called when it's complete (or failed)
				if (asyncResult.failed()) {
					container.logger().error(asyncResult.cause());
				}
				assertTrue(asyncResult.succeeded());
				assertNotNull("deploymentID should not be null", asyncResult.result());

				// If deployed correctly then start the tests!
				startTests();
			}
		});
	}
}
