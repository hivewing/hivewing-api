(ns hivewing-api.public-keys
  (:require [compojure.api.sweet :refer :all]
            [taoensso.timbre :as logger]
            [hivewing-api.schemas :as schemas]
            [ring.util.http-response :refer :all]
            [hivewing-core.worker-config :as worker-config-core]))

(defroutes* public-keys-routes
  (context "/keys" []
    (GET* "/worker/:worker-uuid" []
      :path-params [worker-uuid :- (ring.swagger.schema/describe String "Uuid of the worker")]
      :summary "Get the public keys from this worker."
      :return schemas/WorkerPublicKey
      ;(def worker-uuid "bb5309dc-b21d-11e4-9370-0242ac110082")
      ;(worker-config-core/worker-config-set worker-uuid {(worker-config-core/public-key-key) "123"} :allow-system-keys true)
      (let [config (worker-config-core/worker-config-get-public-key worker-uuid)]
        (ok (-> config
              (assoc :public_key (:data config))
              (dissoc :key)
              (dissoc :data)))))))
