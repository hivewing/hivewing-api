(ns hivewing-api.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [compojure.route :as route]
            [hivewing-api.hives :refer :all]
            [hivewing-api.schemas :as schemas]
            [hivewing-core.hive-data-stages :as hive-data-stages]
            [ring.adapter.jetty :as jetty]
            [schema.core :as s]))

(defapi app
  (swagger-ui)

  (swagger-docs "/api/api-docs"
    :title "Hivewing-api"
    :apiVersion "0.1")

  (swaggered "hives"
    :description "Hives API"
    (route/resources "/")
    hives-api-routes)
)
