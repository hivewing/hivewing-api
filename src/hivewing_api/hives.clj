(ns hivewing-api.hives
  (:require [compojure.api.sweet :refer :all]
            [hivewing-core.hive :as hive-core]
            [ring.util.http-response :refer :all]
            [ring.swagger.schema :refer [describe]]
            [schema.core :as s]))

(s/defschema Hive {:name String,
                   :uuid java.util.UUID,
                   :updated_at (s/maybe java.sql.Timestamp)
                   :created_at java.sql.Timestamp,
                   :apiary_uuid java.util.UUID})

(defmethod compojure.api.meta/restructure-param :hive-access
  [_ hive-permissions {:keys [parameters lets body middlewares] :as acc}]
  "Make sure the request has X-Hive-Access-Token header and that it's poiting to an access token
   that is owned by the hive pointed to by hive-uuid (path_param)"
  (let [let-ds `[{{raw-token# "x-hive-access-token"} :headers} ~'+compojure-api-request+
                 hive-uuid# (:hive-uuid (:params ~'+compojure-api-request+))
                 ~hive-permissions (hivewing-core.hive/hive-get-permissions hive-uuid# raw-token#)]]
    (-> acc
      (update-in [:lets] into let-ds)
      (assoc :body `((if ~hive-permissions
                     (do
                       ~@body)
                     (ring.util.http-response/forbidden "Auth required")))))))

; The hives API routes
(defroutes* hives-api-routes
  (context "/hives/:hive-uuid" []
    (GET* "/" []
      :path-params [hive-uuid :- (ring.swagger.schema/describe String "Uuid of the hive")]
      :summary "Get all the data about this hive"
      :hive-access permissions
      :return Hive
      (let [hive (hivewing-core.hive/hive-get hive-uuid)]
        (if hive
          (ok hive)
          (not-found (str "Could not find the hive " hive-uuid))))
  )))
