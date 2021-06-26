(ns com.eldrix.oink.core
  "Support for LOINC.

  This is a thin wrapper around the original LOINC source data, stored in a
  datalog repository (an indexed entity/attribute/value store) backed by a
  key value store (lmdb). This provides considerable flexibility and
  adaptability although there would be opportunity for pre-computations should
  optimisation for performance be required.

  In essence, the tables are stored as-is, albeit with namespaced keys."
  (:require [clojure.core.async :as async]
            [com.eldrix.oink.importer :as importer]
            [datalevin.core :as d]
            [clojure.tools.logging.readable :as log]))

(def schema
  {:org.loinc/LOINC_NUM                        {:db/unique    :db.unique/identity
                                                :db/valueType :db.type/string}
   :org.loinc.map-to/LOINC                     {:db/valueType :db.type/string}
   :org.loinc.multiaxial-hierarchy/CODE        {:db/valueType :db.type/string}
   :org.loinc/EXTERNAL_COPYRIGHT_LINK          {:db/valueType :db.type/string}
   :org.loinc.source-organization/COPYRIGHT_ID {:db/valueType :db.type/string}})

(defn import-batch
  [conn {:keys [type data] :as batch}]
  (let [tx-data (map #(reduce-kv
                        (fn [m k v] (assoc m (keyword (name type) (name k)) v))
                        {} %) data)]
    (log/info "Processing batch of type " type ": " (count data))
    (d/transact! conn tx-data)))

(defn import-dir
  [conn dir]
  (let [ch (async/chan)]
    (async/thread
      (importer/stream dir ch :batch-size 5000))
    (loop [batch (async/<!! ch)]
      (when (seq batch)
        (import-batch conn batch)
        (recur (async/<!! ch))))))

(defn fetch-loinc
  "Returns data about the given LOINC code."
  ([conn loinc-code]
   (fetch-loinc conn loinc-code '[*]))
  ([conn loinc-code pattern]
   (d/q '[:find (pull ?e pattern) .
          :in $ ?loinc-num pattern
          :where
          [?e :org.loinc/LOINC_NUM ?loinc-num]]
        (d/db conn)
        loinc-code
        pattern)))

(defn fetch-map-to
  "Returns 'map-to' LOINC data for the given code."
  ([conn loinc-code]
   (fetch-map-to conn loinc-code '[*]))
  ([conn loinc-code pattern]
   (d/q
     '[:find (pull ?e pattern)
       :in $ ?loinc-num pattern
       :where
       [?e :org.loinc.map-to/LOINC ?loinc-num]]
     (d/db conn)
     loinc-code
     pattern)))

(defn fetch-source-organization
  ([conn loinc-code]
   (fetch-source-organization conn loinc-code '[*]))
  ([conn loinc-code pattern]
   (d/q '[:find (pull ?e pattern) .
          :in $ ?loinc-num pattern
          :where
          [?loinc :org.loinc/LOINC_NUM ?loinc-num]
          [?loinc :org.loinc/EXTERNAL_COPYRIGHT_LINK ?copyright-id]
          [?e :org.loinc.source-organization/COPYRIGHT_ID ?copyright-id]]
        (d/db conn)
        loinc-code
        pattern)))

(defn fetch-multiaxial-hierarchy
  ([conn loinc-code]
   (fetch-multiaxial-hierarchy conn loinc-code '[*]))
  ([conn loinc-code pattern]
   (d/q '[:find [(pull ?e pattern) ...]
          :in $ ?loinc-num pattern
          :where
          [?e :org.loinc.multiaxial-hierarchy/CODE ?loinc-num]]
        (d/db conn)
        loinc-code
        pattern)))

(defn with-copyright
  [conn copyright-id]
  (d/q '[:find [?loinc-num ...]
         :in $ ?copyright-id
         :where
         [?e :org.loinc/EXTERNAL_COPYRIGHT_LINK ?copyright-id]
         [?e :org.loinc/LOINC_NUM ?loinc-num]]
       (d/db conn)
       copyright-id))

(comment

  (def st (d/create-conn "/tmp/oink1" schema))
  (import-dir st "/Users/mark/Downloads/Loinc_2.70")
  (d/close st)
  (def ch (async/chan))
  (async/thread (importer/stream "/Users/mark/Downloads/Loinc_2.70" ch :batch-size 10 :types #{:org.loinc}))

  (def batch (async/<!! ch))
  (count (:data batch))
  (d/entity (d/db st) [:org.loinc/LOINC_NUM "9770-9"])
  (d/touch (d/entity (d/db st) [:org.loinc/LOINC_NUM "9770-9"]))

  (time (fetch-map-to st "9770-9"))
  (time (fetch-loinc st "9770-9"))
  (fetch-source-organization st "32099-4")
  (time (d/q '[:find ?e ?long-common-name
               :in $ ?loinc-num
               :where
               [?e :org.loinc/LOINC_NUM ?loinc-num]
               [?e :org.loinc/LONG_COMMON_NAME ?long-common-name]]
             (d/db st)
             "2951-2"))
  (d/q '[:find (count ?e)
         :in $ ?class
         :where
         [?e :org.loinc/CLASS ?class]]
       (d/db st)
       "CHEM")

  (d/q '[:find (count ?class)
         :where
         [_ :org.loinc/CLASS ?class]]
       (d/db st))

  (d/q '[:find [?class ...]
         :where
         [_ :org.loinc/CLASS ?class]]
       (d/db st))

  (with-copyright st "Pfizer")
  (fetch-source-organization st "69723-5")
  (with-copyright st "WHO_HIV")
  (fetch-source-organization st "45247-4")
  (fetch-multiaxial-hierarchy st "45247-4")
  (fetch-multiaxial-hierarchy st "LP373671-9")
  (fetch-multiaxial-hierarchy st "LP14082-9")

  (fetch-multiaxial-hierarchy st 	"57021-8")
  (fetch-loinc st 	"57021-8")

  (fetch-multiaxial-hierarchy st 	"LP393878-6")
  (fetch-multiaxial-hierarchy st 	"LP96800-5")
  (fetch-multiaxial-hierarchy st 	"LP7833-9")
  (fetch-multiaxial-hierarchy st 	"LP7803-2")
  (fetch-multiaxial-hierarchy st "LP29693-6")
  (d/close st)
  )