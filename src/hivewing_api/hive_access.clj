(ns hivewing-api.hive-access
  (:require [compojure.api.sweet :refer :all]
            [hivewing-core.hive :as hive-core]
            [ring.util.http-response :refer :all]
            [ring.swagger.schema :refer [describe]]
            [schema.core :as s]))

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
                     (ring.util.http-response/not-found "Resource not found")))))))
