package com.mycompany.myproject.test.mock.acl;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.Verticle;

public class AclVerticle extends Verticle {

	private static final Logger log = LoggerFactory.getLogger(AclVerticle.class);

	private final String manifest = "mock/acl/manifest_response.txt";

	public void setManifestResponse(final HttpServerRequest req, final String filePath, final Map<String, String> headers) {

		vertx.fileSystem().readFile(filePath, new AsyncResultHandler<Buffer>() {

			@Override
			public void handle(AsyncResult<Buffer> event) {
				req.response().setStatusCode(200);
				req.response().setChunked(true);
				req.response().headers().add(headers);
				req.response().write(event.result().toString());
				req.response().end();
			}

		});
	}

	public void start() {

		RouteMatcher routeMatcher = new RouteMatcher();
		routeMatcher.post("/verifyAndGetManifest", new Handler<HttpServerRequest>() {
			@Override
			public void handle(final HttpServerRequest req) {

				log.info("manifest request received");

				Map<String, String> headers = new HashMap<String, String>();
				headers.put("Server", "nginx/1.4.4");
				headers.put("Date", new Date().toString());
				headers.put("ContentType", "multipart/form-data; boundary=Boundary_45_1141105189_1395429574052");
				headers.put("MIME-Version", "1.0");

				setManifestResponse(req, manifest, headers);
			}
		});

		vertx.createHttpServer().requestHandler(routeMatcher).listen(9001);
	}
}
