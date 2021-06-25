(ns com.eldrix.oink.core
  (:require [clojure.core.async :as async]
            [com.eldrix.oink.importer :as importer]
            [com.eldrix.oink.store :as store]
            [datalevin.core :as d]
            [datascript.core :as ds]))

(comment

  (def ds-conn (ds/create-conn))
  (def ch (async/chan))
  (async/go (importer/stream "/Users/mark/Downloads/Loinc_2.70" ch :batch-size 10000))
  (time (loop [i 0
               total 0
               batch (async/<!! ch)
               futures []]
          (if-not (seq batch)
            (dorun (map deref futures))
            ;(println "Processed " total "; processing batch " i)
            (recur (inc i)
                   (+ total (count (:data batch)))
                   (async/<!! ch)
                   (conj futures (ds/transact-async ds-conn (:data batch)))))))

  (ds/q '[:find ?e ?long-common-name
          :in $ ?class
          :where
          [?e :CLASS ?class]
          [?e :LONG_COMMON_NAME ?long-common-name]]
        @ds-conn
        "CHEM")
  (time (ds/q '[:find ?e ?long-common-name
                :in $ ?loinc-num
                :where
                [?e :LOINC_NUM ?loinc-num]
                [?e :LONG_COMMON_NAME ?long-common-name]]
              @ds-conn
              "2951-2"))

  (def st (store/open-store "/tmp/oink1"))
  (def ch (async/chan))
  (async/thread (importer/stream "/Users/mark/Downloads/Loinc_2.70" ch :batch-size 5000))

  (time
    (loop [i 0
           total 0
           batch (async/<!! ch)
           results []]
      (if-not (seq batch)
        (do
          (println "Import complete. Finishing processing.")
          (dorun (map deref results))
          (println "Processing done"))
        (do
          (println "Processed " total "; processing batch " i)
          (recur (inc i)
                 (+ total (count (:data batch)))
                 (async/<!! ch)
                 (conj results (d/transact-async st (:data batch))))))))

  (def batch (async/<!! ch))
  batch
  (d/transact! st (:data batch))
  (d/schema st)
  (d/datoms (d/db st) :eav "10448-9")
  (d/db st)

  (require '[datalevin.core :as d])
  (time (d/q '[:find ?e ?long-common-name
               :in $ ?loinc-num
               :where
               [?e :LOINC_NUM ?loinc-num]
               [?e :LONG_COMMON_NAME ?long-common-name]]
             (d/db st)
             "2951-2"))
  (d/q '[:find ?e ?long-common-name
         :in $ ?class
         :where
         [?e :CLASS ?class]
         [?e :LONG_COMMON_NAME ?long-common-name]]
       (d/db st)
       "CHEM")
  (d/datoms (d/db st) :eav 21266)



  (store/close-store st)
  )