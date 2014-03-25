<div>

<a href="https://travis-ci.org/robertjchristian/vertx-reverse-proxy">
<img src="https://travis-ci.org/robertjchristian/vertx-reverse-proxy.png?branch=master" />
</a>

</div>


# Vert.x Reverse Proxy

To get started, type

./gradlew clean assemble runMod -d

Then in another terminal, test target (8282) and reverse proxy (8080):
<pre>
➜  ~  curl localhost:8282
foo%
➜  ~  curl localhost:8080
foo% 
</pre>
... and logs should indicate the proxy took place:
<pre>
16:30:16.156 [QUIET] [system.out] Proxying request: /
16:30:16.157 [QUIET] [system.out] end of the request
16:30:16.158 [QUIET] [system.out] Target server processing request: /
16:30:16.159 [QUIET] [system.out] Proxying response: 200
16:30:16.160 [QUIET] [system.out] Proxying response body:foo
</pre>

# How to suppress certificate warning in browser
<pre>
<b>This should NEVER be used with any other certificates except proxy.cer located in root of this project</b>
This may open up a vulnerability for man-in-the-middle attack.
</pre>

Note: Only have to perform these steps once for Chrome and Internet Explorer.

Chrome:
<pre>
1. Open 'Settings'
2. Click on 'show advanced settings...'
3. Click 'Manage certificates' under HTTPS/SSL
4. Click 'Import...'
5. Click 'Next'
6. Click 'Browse..', select proxy.cer in root of this project, and click 'Next'
7. Click 'Browse..' button next to Certificate store, select 'Trusted Root Certification Authorities', click 'OK', and click 'Next'
8. Click 'Finish'
9. Click 'Yes' if Security Warning alert pops up
10. Restart browser
</pre>

Internet Explorer:
<pre>
1. Open 'Internet Options'
2. Go to 'Content' tab
3. Click 'Certificates'
4. Follow step 4 - 10 in Chrome
</pre>

Firefox:
<pre>
1. Open 'Options'
2. Go to 'Advanced'
3. Click 'View Certificates'
4. Go to 'Servers' tab
5. Click 'Add Exception...'
6. Enter proxy server address (proxy server runs on https://localhost:8989 by default)
7. Click 'Get Certificate'
8. Click 'Confirm Security Exception'
</pre>