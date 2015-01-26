(ns hivewing-api.hives
  (:require [compojure.api.sweet :refer :all]
            [hivewing-api.hive-access]
            [taoensso.timbre :as logger]
            [hivewing-core.hive :as hive-core]
            [hivewing-core.hive-data-stages :as hive-data-stages]
            [hivewing-core.worker :as worker-core]
            [hivewing-core.worker-events :as worker-events-core]
            [hivewing-core.worker-config :as worker-config-core]
            [hivewing-core.hive-data :as hive-data]
            [hivewing-core.pubsub :as core-pubsub]
            [ring.util.http-response :refer :all]
            [ring.swagger.schema :refer [describe]]
            [hivewing-api.schemas :as schemas]
            [schema.core :as s]))

;; Schemas


(comment
  (def hive-uuid "12345678-1234-1234-1234-123456789012")
  (def params {:params {:value "3", :url "http://123.com/22", :test "gt", :in {:worker "data"}}})
  (processing-stage-params-to-external (:params params))
    (:params (first (hive-data-stages/hive-data-stages-index hive-uuid)))
  (processing-stage-params-to-external {:in [:integer "Tricky wicket"]})
  (first (hive-data-stages/hive-data-stages-index hive-uuid))
  )


; The hives API routes
(defroutes* hives-api-routes
  (context "/hives/:hive-uuid" []

    (GET* "/" []
      :path-params [hive-uuid :- (ring.swagger.schema/describe String "Uuid of the hive")]
      :summary "Get all the data about this hive"
      :hive-access permissions
      :return schemas/Hive
      (let [hive (hivewing-core.hive/hive-get hive-uuid)]
        (if hive
          (ok hive)
          (not-found (str "Could not find the hive " hive-uuid)))))

    (GET* "/workers" []
      :path-params [hive-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the hive")]
      :summary "Get a list of the worker uuids in this hive."
      :query-params [page :- Long, per-page :- Long]
      :hive-access permissions
      :return [schemas/WorkerUUID]
      (ok (hivewing-core.worker/worker-list hive-uuid :per-page per-page :page page)))

    (POST* "/workers" []
      :path-params [hive-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the hive")]
      :summary "Create a worker and put it in a hive"
      :body-params [worker-name :- String]
      :hive-access permissions
      :return schemas/WorkerBootstrapData

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
      :return schemas/Worker
      (if (hivewing-core.worker/worker-in-hive? worker-uuid hive-uuid)
        (ok (hivewing-core.worker/worker-get worker-uuid))
        (not-found "Could not find the worker")))

    (GET* "/worker/:worker-uuid/config" []
      :path-params [hive-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the hive")
                    worker-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the worker")]
      :summary "Get the high-level details about this worker"
      :hive-access permissions
      :return [schemas/WorkerConfigurationPair]
      (if (hivewing-core.worker/worker-in-hive? worker-uuid hive-uuid)
        (map (partial zipmap [:name :value]) (hivewing-core.worker-config/worker-config-get worker-uuid))
        (not-found "Could not find the worker")))

    (POST* "/worker/:worker-uuid/config" []
      :path-params [hive-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the hive")
                    worker-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the worker")]
      :summary "Post and upate the worker-config values. These are sent to the worker"
      :hive-access permissions
      ;:body-params [config-name :- String config-value :- String]
      :body [config-body (ring.swagger.schema/describe schemas/WorkerConfigurationPair "set configuration pairs")]

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
      :body [config-body (ring.swagger.schema/describe schemas/WorkerEvent "Send worker event")]

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
      :return [schemas/DataKey]

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
      :return schemas/DataKeySequence

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
      :return [schemas/DataKey]

      (let [fields (hive-data/hive-data-get-keys hive-uuid)]
        (ok (map #(hash-map :name %) fields))))

      ; Retrieves data fields for the hive
    (GET* "/hive/data/:data-name" []
      :path-params [hive-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the hive")
                    data-name :- (ring.swagger.schema/describe  String "Name of the data field to request")]
      :summary "Get the data from the hive"
      :hive-access permissions
      :return schemas/DataKeySequence

      (ok {:name data-name
             :data (doall (map
                     #(hash-map :value (:data %)
                                :at  (:at %))
                     (hive-data/hive-data-read hive-uuid nil data-name)))}))

    (GET* "/hive/processing/stages" []
      :path-params [hive-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the hive")]
      :summary "Get the list of processing stages for the hive"
      :hive-access permissions
      :return [schemas/HiveProcessingStage]

      (ok (map schemas/processing-stage-to-external (hive-data-stages/hive-data-stages-index hive-uuid))))

    (GET* "/hive/processing/stages/:stage-uuid" []
      :path-params [ hive-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the hive")
                    stage-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the hive data stage")
                    ]
      :summary "Get the data for a processing stage for the hive"
      :hive-access permissions
      :return schemas/HiveProcessingStage

      (ok (schemas/processing-stage-to-external (hive-data-stages/hive-data-stages-get hive-uuid stage-uuid))))

    (POST* "/hive/processing/stages/:stage-uuid/delete" []
      :path-params [ hive-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the hive")
                    stage-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the hive data stage")
                    ]
      :summary "Delete a processing stage from the hive"
      :hive-access permissions
      :return schemas/HiveProcessingStage
      (let [stage-found (hive-data-stages/hive-data-stages-get hive-uuid stage-uuid) ]
        (println "GOING... \n\n\n" stage-found)
        (hive-data-stages/hive-data-stages-delete (:uuid stage-found))
        (ok (schemas/processing-stage-to-external stage-found))))

    (POST* "/hive/processing/stages" []
      :path-params [hive-uuid :- (ring.swagger.schema/describe java.util.UUID "Uuid of the hive")]
      :summary "Get the list of processing stages for the hive"
      :body-params [stage_params :- schemas/HiveProcessingStageParams ]
      :hive-access permissions
      :return schemas/HiveProcessingStage

      (let [stage-params (first (vals stage_params))
            stage-name (keyword (first (keys stage_params)))
            hive-stages  (hive-data-stages/hive-data-stages-specs)
            hive-stage-spec (get-in hive-stages [(keyword stage-name) :spec :params])
            stage-parameters (apply concat (map #(schemas/clean-stage-param hive-stage-spec %) stage-params))
            clean-stage-parameters (concat
                           (list hive-uuid
                                 stage-name)
                           stage-parameters)
            new-stage (apply hive-data-stages/hive-data-stages-create clean-stage-parameters)
            ]
        (ok (schemas/processing-stage-to-external new-stage))))

  ))
