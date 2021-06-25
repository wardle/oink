(ns com.eldrix.oink.core
  (:require [clojure.core.async :as async]
            [com.eldrix.oink.importer :as importer]
            [datalevin.core :as d]))

(comment
  (def schema {:LOINC_NUM {:db/unique :db.unique/identity}
               :MAP_TO    {:db/cardinality :db.cardinality/many
                           :db/type :db.type/ref}})

  (def st (d/create-conn "/tmp/oink1" schema))
  (d/close st)
  (def ch (async/chan))
  (async/thread (importer/stream "/home/mark/Downloads/Loinc_2.70" ch :batch-size 5000 :types #{:org.loinc/map-to}))

  (async/<!! ch)
  (d/transact! st [{:LOINC_NUM "9770-9" :MAP_TO [:LOINC_NUM "21561-6"]}])
  (d/transact! st [{:LOINC_NUM "9770-9" :MAP_TO [:LOINC_NUM "5377-7"]}])
  (d/entity (d/db st) [:LOINC_NUM "9770-9"])
  (keys (d/entity (d/db st) [:LOINC_NUM "9770-9"]))
  (d/touch (d/entity (d/db st) [:LOINC_NUM "9770-9"]))
  (map #(d/datoms (d/db st) :eav (:db/id %)) (:MAP_TO (d/touch (d/entity (d/db st) [:LOINC_NUM "9770-9"]))))
  (d/transact! st [[:db/retract 95625 :MAP_TO ]])
  (d/datoms (d/db st) :eav 47987)


  (time (d/q
    '[:find ?name
      :where
      [_ :LONG_COMMON_NAME ?name]
      [(.startsWith ?name "chlordi")]]
    (d/db st)))


  (d/pull (d/db st) [:db/id :LOINC_NUM :STATUS :LONG_COMMON_NAME {:MAP_TO [:db/id :STATUS :LOINC_NUM :LONG_COMMON_NAME]}] 95625)

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

  (d/close st)
  )