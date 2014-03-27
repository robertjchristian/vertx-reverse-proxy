package com.mycompany.myproject.test.mock;

import static org.vertx.testtools.VertxAssert.assertNotNull;
import static org.vertx.testtools.VertxAssert.assertTrue;

import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.testtools.TestVerticle;

public class MockServerTest extends TestVerticle {

	@Test
	public void runServers() {
		container.logger().info("running mock servers");
		while (true) {

		}
	}

	@Override
	public void start() {

		// Make sure we call initialize() - this sets up the assert stuff so assert functionality works correctly
		initialize();

		container.deployVerticle("com.mycompany.myproject.test.mock.auth.AuthVerticle", new AsyncResultHandler<String>() {
			@Override
			public void handle(AsyncResult<String> authResult) {
				if (authResult.failed()) {
					container.logger().error(authResult.cause());
				}
				assertTrue(authResult.succeeded());
				assertNotNull("deploymentID should not be null", authResult.result());

				container.deployVerticle("com.mycompany.myproject.test.mock.acl.AclVerticle", new AsyncResultHandler<String>() {
					@Override
					public void handle(AsyncResult<String> aclResult) {

						// Deployment is asynchronous and this this handler will be called when it's complete (or failed)
						if (aclResult.failed()) {
							container.logger().error(aclResult.cause());
						}
						assertTrue(aclResult.succeeded());
						assertNotNull("deploymentID should not be null", aclResult.result());

						// If deployed correctly then start the tests!
						startTests();
					}
				});
			}
		});
	}
}
