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

import static org.vertx.testtools.VertxAssert.assertNotNull;
import static org.vertx.testtools.VertxAssert.assertTrue;
import static org.vertx.testtools.VertxAssert.testComplete;

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
	public void dummyTest() {
		// do nothing
		testComplete();
	}

	//@Test
	public void testProxyServer() {

		container.logger().info("Testing proxy server...");

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

	@Override
	public void start() {

		// Make sure we call initialize() - this sets up the assert stuff so assert functionality works correctly
		initialize();

		// Deploy the module - the System property `vertx.modulename` will contain the name of the module so you
		// don't have to hardecode it in your tests

		JsonObject config = new JsonObject();
		config.putString("resourceRoot", "../../../resources/");
		config.putString("webRoot", "../../../resources/web/");

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
