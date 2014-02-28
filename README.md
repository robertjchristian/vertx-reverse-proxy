# Vert.x Reverse Proxy

To get started, type

./gradlew clean assemble runMod -d

Then in another terminal, test target (8282) and reverse proxy (8080):

➜  ~  curl localhost:8282
foo%
➜  ~  curl localhost:8080
foo% 

... and logs should indicate the proxy took place:

16:30:16.156 [QUIET] [system.out] Proxying request: /
16:30:16.157 [QUIET] [system.out] end of the request
16:30:16.158 [QUIET] [system.out] Target server processing request: /
16:30:16.159 [QUIET] [system.out] Proxying response: 200
16:30:16.160 [QUIET] [system.out] Proxying response body:foo