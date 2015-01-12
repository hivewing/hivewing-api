(ns hivewing-api.hives
  (:require [compojure.api.sweet :refer :all]
            [hivewing-api.hive-access]
            [taoensso.timbre :as logger]
            [hivewing-core.hive :as hive-core]
            [hivewing-core.worker :as worker-core]
            [hivewing-core.worker-events :as worker-events-core]
            [hivewing-core.worker-config :as worker-config-core]
            [hivewing-core.hive-data :as hive-data]
            [hivewing-core.pubsub :as core-pubsub]
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
(s/defschema WorkerBootstrapData {
                                  :uuid java.util.UUID,
                                  :access_token java.util.UUID
                                  })
(s/defschema Worker {
                   :uuid     java.util.UUID,
                   :name        (s/maybe String),
                   :updated_at  (s/maybe java.sql.Timestamp),
                   :created_at  java.sql.Timestamp,
                   :apiary_uuid java.util.UUID,
                   :last_seen   (s/maybe java.sql.Timestamp),
                   :connected   Boolean,
                   :hive_uuid   java.util.UUID})

(s/defschema WorkerConfigurationPair { :name String, :value String })

(s/defschema WorkerEvent { :name String, :value String })

(s/defschema DataEntry  { :value String , :at java.sql.Timestamp})

(s/defschema DataKey {:name String })
(s/defschema DataKeySequence  { :name String, :data [DataEntry] })


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
      (ok (hivewing-core.worker/worker-list hive-uuid :per-page per-page :page page)))

    (POST* "/workers" []
      :path-params [hive-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the hive")]
      :summary "Create a worker and put it in a hive"
      :body-params [worker-name :- String]
      :hive-access permissions
      :return WorkerBootstrapData

      (let [apiary-uuid (:apiary_uuid (hivewing-core.hive/hive-get hive-uuid))
            result      (hivewing-core.worker/worker-create {:name worker-name
                                                            :apiary_uuid apiary-uuid
                                                            :hive_uuid hive-uuid})
            ]
        (ok {:uuid (:uuid result)
             :access_token (:access_token result)})))

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

    (GET* "/worker/:worker-uuid/data" []
      :path-params [hive-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the hive")
                    worker-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the worker") ]
      :summary "Get all the data keys for a worker"
      :hive-access permissions
      :return [DataKey]

      (if (hivewing-core.worker/worker-in-hive? worker-uuid hive-uuid)
        (let [fields (hive-data/hive-data-get-keys hive-uuid worker-uuid)]
          (ok (map #(hash-map :name %) fields)))
          ; invalid string
        ; Worker was NOT in the hive
        (not-found "Could not find the worker")))

      ; Retrieves ALL the data fields as a hash.
    (GET* "/worker/:worker-uuid/data/:data-name" []
      :path-params [hive-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the hive")
                    worker-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the worker")
                    data-name :- (ring.swagger.schema/describe  String "Name of the data field to request")]
      :summary "Get the data for a worker"
      :hive-access permissions
      :return DataKeySequence

      (if (hivewing-core.worker/worker-in-hive? worker-uuid hive-uuid)
        (let [values (doall (map
                     #(hash-map :value (:data %)
                                :at  (:at %))
                     (hive-data/hive-data-read hive-uuid worker-uuid data-name))) ]
          (ok {:name data-name
               :data values}))
        (not-found "Could not find the worker"))
      )

    (GET* "/hive/data" []
      :path-params [hive-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the hive")]
      :summary "Get all the data keys for a hive (hive-based data)"
      :hive-access permissions
      :return [DataKey]

      (let [fields (hive-data/hive-data-get-keys hive-uuid)]
        (ok (map #(hash-map :name %) fields))))

      ; Retrieves data fields for the hive
    (GET* "/hive/data/:data-name" []
      :path-params [hive-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the hive")
                    data-name :- (ring.swagger.schema/describe  String "Name of the data field to request")]
      :summary "Get the data from the hive"
      :hive-access permissions
      :return DataKeySequence

      (ok {:name data-name
             :data (doall (map
                     #(hash-map :value (:data %)
                                :at  (:at %))
                     (hive-data/hive-data-read hive-uuid nil data-name)))}))

  ))
