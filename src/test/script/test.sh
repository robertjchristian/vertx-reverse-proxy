while true; do

  curl -X POST -d @test.payload --header "Cookie:  token=12345678" localhost:8080

  curl --header "Authorization:  Basic cm9vdDpzZWNyZXQ=" localhost:8080

  curl --header "Authorization:  Basic cm9iOnJvYg==" localhost:8080

done