(ns hivewing-api.core
   (:require [ring.adapter.jetty :as jetty]
             [hivewing-api.handler :as handler])
   (:gen-class))

(defn -main []
  (jetty/run-jetty handler/app {:port 5000}))
