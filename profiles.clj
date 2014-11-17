{
 :dev {
   :env {
     :hivewing-redis-uri "redis://127.0.0.1:3900/"
     :hivewing-aws-access-key "123abc",
     :hivewing-aws-secret-key "123abc",
     :hivewing-ddb-endpoint   "http://127.0.0.1:3800",
     :hivewing-ddb-worker-config-table "HivewingWorkerConfiguration.v1"
     :hivewing-sql-connection-string "//127.0.0.1:5432/hivewing-dev?user=hivewing&password=hivewing"
    },
   },
 :test {
    :env {
     :hivewing-redis-uri "redis://127.0.0.1:3900/"
     :hivewing-aws-access-key "123abc",
     :hivewing-aws-secret-key "123abc",
     :hivewing-ddb-endpoint   "http://127.0.0.1:3800",
     :hivewing-ddb-worker-config-table "test.HivewingWorkerConfiguration.v1"
     :hivewing-sql-connection-string "//127.0.0.1:5432/hivewing-test?user=hivewing&password=hivewing"
    }
  }
}
