(ns hivewing-api.hives
  (:require [compojure.api.sweet :as sweet]
            [ring.util.http-response :as response]
            [ring.swagger.schema :as ss]
            [hivewing-core.hive :as hive-core]
            [ring.middleware.basic-authentication :refer [wrap-basic-authentication]]
            [schema.core :as schema]))

(schema/defschema Hive {:name String,
                        :worker-count Long})

(defn hive-auth? [hive-uuid access-token]
  "Look up the hiveuuid and access token, see if it matches.
   if so return the data (and role information)"
  {:uuid "123"
   :roles []})

; The hives API routes
(def hives-api-routes
  (wrap-basic-authentication hive-auth?
    (sweet/context "/hives" {hive-auth :basic-authentication, :as request}
      (sweet/GET* "/" []
        :summary "Get all the data about this hive"
        :return Hive
        (response/ok {:name "Worker" :worker-count 1})
    ))))
