(ns com.eldrix.oink.core
  (:require [clojure.core.async :as async]
            [com.eldrix.oink.importer :as importer]
            [datalevin.core :as d]))

(comment
  (def schema {:LOINC_NUM  {:db/unique :db.unique/identity}})

  (def st (d/create-conn "/tmp/oink1" schema))
  (def ch (async/chan))
  (async/thread (importer/stream "/home/mark/Downloads/Loinc_2.70" ch :batch-size 5000))

  (time
    (loop [i 0
           total 0
           batch (async/<!! ch)]
      (when (seq batch)
        (d/transact! st (:data batch))
        (recur (inc i)
               (+ total (count (:data batch)))
               (async/<!! ch)))))

  (def batch (async/<!! ch))
  batch
  (d/transact! st (:data batch))
  (d/schema st)
  (d/db st)

  (require '[datalevin.core :as d])
  (time (d/q '[:find ?e ?long-common-name
               :in $ ?loinc-num
               :where
               [?e :LOINC_NUM ?loinc-num]
               [?e :LONG_COMMON_NAME ?long-common-name]]
             (d/db st)
             "2951-2"))
  (d/q '[:find (count ?e)
         :in $ ?class
         :where
         [?e :CLASS ?class]]
       (d/db st)
       "CHEM")

  (d/q '[:find (count ?class)
         :where
         [_ :CLASS ?class]]
       (d/db st))

  (d/q '[:find [?class ...]
         :where
         [_ :CLASS ?class]]
       (d/db st))


  (d/datoms (d/db st) :eav 21266)

  (:DisplayName (d/entity (d/db st) [:LOINC_NUM "2951-2"]))
  (seq (d/entity (d/db st) [:LOINC_NUM "2951-2"]))

  (store/close-store st)
  )