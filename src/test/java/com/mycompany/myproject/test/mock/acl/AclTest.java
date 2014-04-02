package com.mycompany.myproject.test.mock.acl;

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
import org.vertx.testtools.TestVerticle;

public class AclTest extends TestVerticle {

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
		String content = vertx.fileSystem().readFileSync("mock/acl/manifest_request.txt").toString();
		request.setChunked(true);
		request.write(content);
		request.end();
	}

	@Override
	public void start() {

		// Make sure we call initialize() - this sets up the assert stuff so assert functionality works correctly
		initialize();

		container.deployVerticle("com.mycompany.myproject.test.mock.acl.AclVerticle", new AsyncResultHandler<String>() {
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
