(defproject hivewing-api "0.1.0"
  :description "Hivewing.io API server"
  :dependencies [[org.clojure/clojure "1.7.0-alpha5"]
                 [metosin/compojure-api "0.17.0"]
                 [metosin/ring-http-response "0.5.2"]
                 [metosin/ring-swagger-ui "2.0.17"]
                 [environ "1.0.0"]
                 [hivewing-core "0.1.3-SNAPSHOT"]]

  :ring {:handler hivewing-api.handler/app
         :port 5000}

  :uberjar-name "hivewing-api-%s.uber.jar"

  :repositories [["hivewing-core" {:url "s3p://clojars.hivewing.io/hivewing-core/releases"
                                   :username "AKIAJCSUM5ZFGI7DW5PA"
                                   :passphrase "UcO9VGAaGMRuJZbgZxCiz0XuHmB1J0uvzt7WIlJK"}]]

  :plugins [[lein-ring "0.8.13"]
            [s3-wagon-private "1.1.2"]
            [lein-environ "1.0.0"]
            ]

  :profiles {:uberjar {:aot :all
                       :resource-paths ["swagger-ui"]}
             :dev {:dependencies [[javax.servlet/servlet-api "2.5"]]}}
  :main hivewing-api.core
  )
