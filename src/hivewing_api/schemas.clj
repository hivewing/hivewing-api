(ns hivewing-api.schemas
  (:require [ring.swagger.schema :refer [describe]]
            [ring.swagger.json-schema-dirty]
            [schema.core :as s]))

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

(s/defschema HiveProcessingDataStream
  ;{ :worker (s/maybe String)
  ;  :hive (s/maybe String)}
  {(s/optional-key :worker) String
   (s/optional-key :hive)   String
   })

(s/defschema HiveProcessingDumpS3StageParams {
              :in HiveProcessingDataStream
              :bucket String
              :secret-key String
              :access-key String
              :size Integer
              :window Integer })

(s/defschema HiveProcessingDumpPostStageParams {
              :in HiveProcessingDataStream
              :url String
              :size Integer
              :window Integer
              })

(s/defschema HiveProcessingAverageStageParams {
              :in HiveProcessingDataStream
              :out HiveProcessingDataStream
              :window Integer
              })

(s/defschema HiveProcessingAlertEmailStageParams {
              :in HiveProcessingDataStream
              :email String
              :value String
              :test (s/enum "gt" "gte" "lt" "lte" "eq")
              })

(s/defschema HiveProcessingAlertPostStageParams {
              :in HiveProcessingDataStream
              :url   String
              :value String
              :test (s/enum "gt" "gte" "lt" "lte" "eq")
              })
(s/defschema HiveProcessingChangeEmailStageParams {
              :in HiveProcessingDataStream
              :email String
                })
(s/defschema HiveProcessingChangePostStageParams {
              :in HiveProcessingDataStream
              :url String
                                                  })

(comment
    )

(s/defschema HiveProcessingStageParams
  {
   (s/optional-key :dump-s3     ) HiveProcessingDumpS3StageParams
   (s/optional-key :dump-post   ) HiveProcessingDumpPostStageParams
   (s/optional-key :average     ) HiveProcessingAverageStageParams
   (s/optional-key :alert-email ) HiveProcessingAlertEmailStageParams
   (s/optional-key :alert-post  ) HiveProcessingAlertPostStageParams
   (s/optional-key :change-email) HiveProcessingChangeEmailStageParams
   (s/optional-key :change-post ) HiveProcessingChangePostStageParams
  })

(s/defschema HiveProcessingStage
    {:uuid java.util.UUID,
     :hive_uuid java.util.UUID,
     :created_at java.sql.Timestamp,
     :stage HiveProcessingStageParams
    })

(defn clean-stage-param
  [stage-spec [field-name field-val]]
  (let [field-spec (get stage-spec field-name)
        [field-type field-desc field-details]  field-spec ]

    (vector
      (keyword field-name)
      (case field-type
        :data-stream field-val
        :url field-val
        :string field-val
        :integer field-val
        :email field-val
        :enum (keyword field-val)))))

(defn processing-stage-to-external
  [stage]
  {:uuid (:uuid stage)
   :hive_uuid (:hive_uuid stage)
   :created_at (:created_at stage)
   :stage (hash-map (keyword (:stage_type stage)) (:params stage))
   })
