package com.mycompany.myproject.test.mock;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

import org.bouncycastle.cms.CMSException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.impl.Base64;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.Verticle;

import com.google.gson.Gson;
import com.liaison.commons.security.pkcs7.signandverify.DigitalSignature;
import com.mycompany.myproject.test.mock.usermanagement.model.AuthenticationResponse;
import com.mycompany.myproject.test.mock.usermanagement.model.Response;
import com.mycompany.myproject.test.mock.usermanagement.model.User;
import com.mycompany.myproject.test.mock.usermanagement.model.UserList;

public class UserManagementVerticle extends Verticle {

	private static final Logger log = LoggerFactory.getLogger(UserManagementVerticle.class);

	private void constructResponse(final HttpServerRequest req, String message, String authentication, String authenticationToken, Date sessionDate) {
		Gson gson = new Gson();

		Response response = new Response(message, "success", authentication, authenticationToken, sessionDate);
		AuthenticationResponse authResponse = new AuthenticationResponse(response);

		String responseString = gson.toJson(authResponse);

		setResponse(req, 200, responseString);
	}

	private void setResponse(final HttpServerRequest req, int status, String message) {
		req.response().setStatusCode(status);
		req.response().setChunked(true);
		req.response().write(message);
		req.response().end();
	}

	public void start() {

		String rawUserList = vertx.fileSystem().readFileSync("usermanagement/userList.json").toString();
		Gson gson = new Gson();
		final UserList userList = gson.fromJson(rawUserList, UserList.class);

		RouteMatcher routeMatcher = new RouteMatcher();
		routeMatcher.post("/authenticate", new Handler<HttpServerRequest>() {
			@Override
			public void handle(final HttpServerRequest req) {

				//TODO move this to ReverseProxyVerticle
				String authInfo = req.headers().get("Authorization");
				String parsedAuthInfo = authInfo.replace("Basic", "").trim();
				String decodedAuthInfo = new String(Base64.decode(parsedAuthInfo));
				String[] auth = decodedAuthInfo.split(":");

				if (auth != null && auth.length == 2) {
					boolean found = false;
					for (User user : userList.getUserList()) {
						if (user.getUserId().equals(auth[0])) {
							found = true;
							if (user.getPassword().equals(auth[1])) {
								constructResponse(req, "Account authenticated successfully.", "success", user.getAuthenticationToken(), new Date());
							}
							else {
								constructResponse(req,
										"Account authentication failed.The client passed either an incorrect DN or password, or the password is incorrect because it has expired, intruder detection has locked the account, or another similar reason.",
										"failure",
										null,
										null);
							}
							break;
						}
					}
					if (!found) {
						constructResponse(req, "Account authentication failed.No USER ACCOUNT available in the system.", "failure", null, null);
					}
				}
				else {
					constructResponse(req, "Account authentication failed.No USER ACCOUNT available in the system.", "failure", null, null);
				}

			}
		});

		routeMatcher.post("/sign", new Handler<HttpServerRequest>() {
			@Override
			public void handle(final HttpServerRequest req) {

				Security.addProvider(new BouncyCastleProvider());

				try {
					final X509Certificate cert = readCertificate(vertx.fileSystem().readFileSync("usermanagement/key/proxy.p7b"));
					final PrivateKey privateKey = readPrivateKey(vertx.fileSystem().readFileSync("usermanagement/key/proxy_test_key"));

					req.dataHandler(new Handler<Buffer>() {

						@Override
						public void handle(Buffer data) {
							try {
								InputStream is = new ByteArrayInputStream(data.getBytes());
								DigitalSignature digitalSignature = new DigitalSignature();
								String signed = digitalSignature.signBase64(is, cert, privateKey);
								setResponse(req, 200, signed);
							}
							catch (IOException | OperatorCreationException | CMSException | CertificateException e) {
								setResponse(req, 500, e.getMessage());
							}
						}
					});
				}
				catch (Exception e) {
					setResponse(req, 500, e.getMessage());
				}
			}
		});

		vertx.createHttpServer().requestHandler(routeMatcher).listen(9000);
	}

	private static PrivateKey readPrivateKey(Buffer buffer) throws Exception {

		ByteArrayInputStream bais = new ByteArrayInputStream(buffer.getBytes());
		DataInputStream dis = new DataInputStream(bais);
		byte[] keyBytes = new byte[(int) buffer.length()];
		dis.readFully(keyBytes);
		dis.close();

		PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
		KeyFactory kf = KeyFactory.getInstance("RSA");
		return kf.generatePrivate(spec);
	}

	@SuppressWarnings("rawtypes")
	private static X509Certificate readCertificate(Buffer buffer) throws Exception {
		ByteArrayInputStream bais = new ByteArrayInputStream(buffer.getBytes());
		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		Collection c = cf.generateCertificates(bais);
		Iterator i = c.iterator();
		if (i.hasNext()) {
			return (X509Certificate) i.next();
		}
		else {
			return null;
		}

	}
}
