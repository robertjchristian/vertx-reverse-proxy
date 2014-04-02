package com.mycompany.myproject.test.mock.engine;

import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.platform.Verticle;

import com.google.gson.Gson;
import com.mycompany.myproject.verticles.reverseproxy.model.ApplicationUser;
import com.mycompany.myproject.verticles.reverseproxy.model.ApplicationUserList;

public class EngineVerticle extends Verticle {


	public void start() {
		String rawUserList = vertx.fileSystem().readFileSync("mock/engine/application_user_list.json").toString();
		final Gson gson = new Gson();
		final ApplicationUserList userList = gson.fromJson(rawUserList, ApplicationUserList.class);

		RouteMatcher routeMatcher = new RouteMatcher();
		routeMatcher.post("/roles", new Handler<HttpServerRequest>() {

			@Override
			public void handle(final HttpServerRequest req) {
				req.dataHandler(new Handler<Buffer>() {

					@Override
					public void handle(Buffer data) {
						ApplicationUser appUser = gson.fromJson(data.toString(), ApplicationUser.class);
						boolean found = false;
						for (ApplicationUser existingAppUser : userList.getApplicationUserList()) {
							if (existingAppUser.getUser().equals(appUser.getUser()) && existingAppUser.getOrganization().equals(appUser.getOrganization())) {
								found = true;
								req.response().setChunked(true);
								req.response().write(gson.toJson(existingAppUser.getRoles()));
								req.response().end();
							}
						}
						if (!found) {
							req.response().setChunked(true);
							req.response().write(String.format("unable to find user %s@%s", appUser.getUser(), appUser.getOrganization()));
							req.response().end();
						}
					}
				});
				req.endHandler(new VoidHandler() {

					@Override
					public void handle() {
					}
				});
			}
		});

		vertx.createHttpServer().requestHandler(routeMatcher).listen(9002);
	}
}
