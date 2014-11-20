(ns hivewing-api.hives
  (:require [compojure.api.sweet :refer :all]
            [hivewing-api.hive-access]
            [hivewing-core.hive :as hive-core]
            [hivewing-core.worker :as worker-core]
            [hivewing-core.worker-events :as worker-events-core]
            [hivewing-core.worker-config :as worker-config-core]
            [ring.util.http-response :refer :all]
            [ring.swagger.schema :refer [describe]]
            [schema.core :as s]))

;; Schemas
(s/defschema Hive {:name String,
                   :uuid java.util.UUID,
                   :updated_at (s/maybe java.sql.Timestamp)
                   :created_at java.sql.Timestamp,
                   :apiary_uuid java.util.UUID})

(s/defschema WorkerUUID {  :uuid java.util.UUID,
                           :updated_at (s/maybe java.sql.Timestamp),
                           :created_at java.sql.Timestamp })
(s/defschema Worker {
                   :uuid     java.util.UUID,
                   :name        (s/maybe String),
                   :updated_at  (s/maybe java.sql.Timestamp),
                   :created_at  java.sql.Timestamp,
                   :apiary_uuid java.util.UUID,
                   :hive_uuid   java.util.UUID})

(s/defschema WorkerConfigurationPair { :name String, :value String })

(s/defschema WorkerEvent { :name String, :value String })


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
          (not-found (str "Could not find the hive " hive-uuid)))))

    (GET* "/workers" []
      :path-params [hive-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the hive")]
      :summary "Get a list of the worker uuids in this hive."
      :query-params [page :- Long, per-page :- Long]
      :hive-access permissions
      :return [WorkerUUID]
      (ok (hivewing-core.worker/worker-list hive-uuid :per-page per-page :page page))
    )
    (GET* "/worker/:worker-uuid" []
      :path-params [hive-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the hive")
                    worker-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the worker")]
      :summary "Get the high-level details about this worker"
      :hive-access permissions
      :return Worker
      (if (hivewing-core.worker/worker-in-hive? worker-uuid hive-uuid)
        (ok (hivewing-core.worker/worker-get worker-uuid))
        (not-found "Could not find the worker")))

    (GET* "/worker/:worker-uuid/config" []
      :path-params [hive-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the hive")
                    worker-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the worker")]
      :summary "Get the high-level details about this worker"
      :hive-access permissions
      :return [WorkerConfigurationPair]
      (if (hivewing-core.worker/worker-in-hive? worker-uuid hive-uuid)
        (map (partial zipmap [:name :value]) (hivewing-core.worker-config/worker-config-get worker-uuid))
        (not-found "Could not find the worker")))

    (POST* "/worker/:worker-uuid/config" []
      :path-params [hive-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the hive")
                    worker-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the worker")]
      :summary "Post and upate the worker-config values. These are sent to the worker"
      :hive-access permissions
      ;:body-params [config-name :- String config-value :- String]
      :body [config-body (ring.swagger.schema/describe WorkerConfigurationPair "set configuration pairs")]
      (let [{config-name :name config-value :value} config-body ]
        (if (hivewing-core.worker/worker-in-hive? worker-uuid hive-uuid)
          ; If the worker is in the hive
          (if (and (hivewing-core.worker-config/worker-config-valid-name? config-name)
                   (not (hivewing-core.worker-config/worker-config-system-name? config-name)))
              (do
                (hivewing-core.worker-config/worker-config-set worker-uuid {config-name config-value})
                (ok {:name config-name :value config-value}))
            ; invalid string
            (bad-request "Invalid key name"))
          ; Worker was NOT in the hive
          (not-found "Could not find the worker"))))

    (POST* "/worker/:worker-uuid/events" []
      :path-params [hive-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the hive")
                    worker-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the worker")]
      :summary "Push an event to the worker."
      :hive-access permissions
      :body [config-body (ring.swagger.schema/describe WorkerEvent "Send worker event")]

      (let [{event-name :name event-value :value} config-body ]
        (if (hivewing-core.worker/worker-in-hive? worker-uuid hive-uuid)
          ; If the worker is in the hive
          (if (and (hivewing-core.worker-events/worker-events-valid-name? event-name)
                   (not (hivewing-core.worker-events/worker-events-system-name? event-name)))
              (do
                (hivewing-core.worker-events/worker-events-send worker-uuid event-name event-value)
                (ok {:name event-name :value event-value}))
            ; invalid string
            (bad-request "Invalid event name"))
          ; Worker was NOT in the hive
          (not-found "Could not find the worker"))))
    ))
