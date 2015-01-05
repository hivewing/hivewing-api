(ns hivewing-api.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [hivewing-api.hives :refer :all]
            [ring.adapter.jetty :as jetty]
            [schema.core :as s])
  (:gen-class))

(defapi app
  (swagger-ui)

  (swagger-docs "/api/api-docs"
    :title "Hivewing-api"
    :apiVersion "0.1")

  (swaggered "hives"
    :description "Hives API"
    hives-api-routes)
)

(defn -main []
  (jetty/run-jetty app {:port 3000}))
