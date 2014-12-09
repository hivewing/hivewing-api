(defproject hivewing-api "0.1.0"
  :description "Hivewing.io API server"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [metosin/compojure-api "0.16.4"]
                 [metosin/ring-http-response "0.5.2"]
                 [metosin/ring-swagger-ui "2.0.17"]
                 [environ "1.0.0"]
                 [ring-basic-authentication "1.0.5"]
                 [hivewing-core "0.1.1"]]

  :ring {:handler hivewing-api.handler/app}

  :uberjar-name "hivewing-api_%s.jar"

  :repositories [["hivewing-core" {:url "s3p://clojars.hivewing.io/hivewing-core/releases"
                                   :username "AKIAJCSUM5ZFGI7DW5PA"
                                   :passphrase "UcO9VGAaGMRuJZbgZxCiz0XuHmB1J0uvzt7WIlJK"}]]

  :plugins [[lein-ring "0.8.13"]
            [s3-wagon-private "1.1.2"]
            [lein-environ "1.0.0"]
            [lein-ring "0.8.13"]
            ]

  :profiles {:uberjar {:resource-paths ["swagger-ui"]}
             :dev {:dependencies [[javax.servlet/servlet-api "2.5"]]}})

  ;   :hivewing-redis-uri "redis://127.0.0.1:3900/"
  ;   :hivewing-aws-access-key "123abc",
  ;   :hivewing-aws-secret-key "123abc",
  ;   :hivewing-ddb-endpoint   "http://127.0.0.1:3800",
  ;   :hivewing-ddb-worker-config-table "HivewingWorkerConfiguration.v1"
  ;   :hivewing-sql-connection-string "//127.0.0.1:5432/hivewing-dev?user=hivewing&password=hivewing"
