(ns com.eldrix.oink.core
  "Support for LOINC.

  This is a thin wrapper around the original LOINC source data, stored in a
  datalog repository (an indexed entity/attribute/value store) backed by a
  key value store (lmdb). This provides considerable flexibility and
  adaptability although there would be opportunity for pre-computations should
  optimisation for performance be required.

  In essence, the tables are stored as-is, albeit with namespaced keys. We
  join them up in interesting ways at the time of query in order to generate a
  searchable index."
  (:require [clojure.core.async :as async]
            [com.eldrix.oink.importer :as importer]
            [datalevin.core :as d]
            [clojure.tools.logging.readable :as log]
            [clojure.string :as str])
  (:import (java.io Closeable)))

(def schema
  {:org.loinc/LOINC_NUM                        {:db/unique    :db.unique/identity
                                                :db/valueType :db.type/string}
   :org.loinc.map-to/LOINC                     {:db/valueType :db.type/string}
   :org.loinc.multiaxial-hierarchy/CODE        {:db/valueType :db.type/string}
   :org.loinc/EXTERNAL_COPYRIGHT_LINK          {:db/valueType :db.type/string}
   :org.loinc.source-organization/COPYRIGHT_ID {:db/valueType :db.type/string}
   :org.loinc.part/PartNumber                  {:db/valueType :db.type/string}
   })

(deftype Svc [conn]
  Closeable
  (close [_] (d/close conn)))

(defn open [dir]
  (->Svc (d/create-conn dir schema)))

(defn close [^Svc svc]
  (d/close (.-conn svc)))

(defn import-batch
  [^Svc svc {:keys [type data] :as batch}]
  (let [tx-data (map #(reduce-kv
                        (fn [m k v] (assoc m (keyword (name type) (name k)) v))
                        {} %) data)]
    (log/info "Processing batch of type " type ": " (count data))
    (d/transact! (.-conn svc) tx-data)))

(defn import-dir
  [^Svc svc dir]
  (let [ch (async/chan)]
    (async/thread
      (importer/stream dir ch :batch-size 5000))
    (loop [batch (async/<!! ch)]
      (when (seq batch)
        (import-batch svc batch)
        (recur (async/<!! ch))))))

(defn list-dir [dir]
  (importer/list-files dir))

(defn get-status [^Svc svc]
  (log/info "not implemented"))
;;
;;
;;

(defn fetch-loinc
  "Returns data about the given LOINC code."
  ([^Svc svc loinc-code]
   (fetch-loinc svc loinc-code '[*]))
  ([^Svc svc loinc-code pattern]
   (d/q '[:find (pull ?e pattern) .
          :in $ ?loinc-num pattern
          :where
          [?e :org.loinc/LOINC_NUM ?loinc-num]]
        (d/db (.-conn svc))
        loinc-code
        pattern)))

(defn fetch-map-to
  "Returns 'map-to' LOINC data for the given code."
  ([^Svc svc loinc-code]
   (fetch-map-to svc loinc-code '[*]))
  ([^Svc svc loinc-code pattern]
   (d/q
     '[:find (pull ?e pattern)
       :in $ ?loinc-num pattern
       :where
       [?e :org.loinc.map-to/LOINC ?loinc-num]]
     (d/db (.-conn svc))
     loinc-code
     pattern)))

(defn fetch-source-organization
  ([^Svc svc loinc-code]
   (fetch-source-organization svc loinc-code '[*]))
  ([^Svc svc loinc-code pattern]
   (d/q '[:find (pull ?e pattern) .
          :in $ ?loinc-num pattern
          :where
          [?loinc :org.loinc/LOINC_NUM ?loinc-num]
          [?loinc :org.loinc/EXTERNAL_COPYRIGHT_LINK ?copyright-id]
          [?e :org.loinc.source-organization/COPYRIGHT_ID ?copyright-id]]
        (d/db (.-conn svc))
        loinc-code
        pattern)))

(defn fetch-multiaxial-hierarchy
  ([^Svc svc loinc-code]
   (fetch-multiaxial-hierarchy svc loinc-code '[*]))
  ([^Svc svc loinc-code pattern]
   (d/q '[:find [(pull ?e pattern) ...]
          :in $ ?loinc-num pattern
          :where
          [?e :org.loinc.multiaxial-hierarchy/CODE ?loinc-num]]
        (d/db (.-conn svc))
        loinc-code
        pattern)))

(defn with-copyright
  [^Svc svc copyright-id]
  (d/q '[:find [?loinc-num ...]
         :in $ ?copyright-id
         :where
         [?e :org.loinc/EXTERNAL_COPYRIGHT_LINK ?copyright-id]
         [?e :org.loinc/LOINC_NUM ?loinc-num]]
       (d/db (.-conn svc))
       copyright-id))

(defn fetch-part
  ([^Svc svc part-number]
   (fetch-part svc part-number '[*]))
  ([^Svc svc part-number pattern]
   (d/q '[:find (pull ?e pattern) .
          :in $ ?part-number pattern
          :where
          [?e :org.loinc.part/PartNumber ?part-number]]
        (d/db (.-conn svc))
        part-number
        pattern)))


(defn fetch-part-code-mapping
  ([^Svc svc part-number]
   (fetch-part-code-mapping svc part-number '[*]))
  ([^Svc svc part-number pattern]
   (d/q '[:find (pull ?e pattern) .
          :in $ ?part-number pattern
          :where
          [?e :org.loinc.part.code-mapping/PartNumber ?part-number]]
        (d/db (.-conn svc))
        part-number
        pattern)))

(defn snomed->loinc-parts
  ([^Svc svc sctid]
   (snomed->loinc-parts svc sctid '[*]))
  ([^Svc svc sctid pattern]
   (d/q '[:find (pull ?e pattern)
          :in $ ?sctid pattern
          :where
          [?e :org.loinc.part.code-mapping/ExtCodeId ?sctid]
          [?e :org.loinc.part.code-mapping/ExtCodeSystem "http://snomed.info/sct"]]
        (d/db (.-conn svc))
        (str sctid)
        pattern)))

(defn loinc-part->snomed
  ([^Svc svc part-number]
   (loinc-part->snomed svc part-number '[*]))
  ([^Svc svc part-number pattern]
   (d/q '[:find (pull ?e pattern) .
          :in $ ?part-number pattern
          :where
          [?e :org.loinc.part.code-mapping/PartNumber ?part-number]
          [?e :org.loinc.part.code-mapping/ExtCodeSystem "http://snomed.info/sct"]]
        (d/db (.-conn svc))
        part-number
        pattern)))

(defn loinc->parts
  ([^Svc svc loinc-code]
   (loinc->parts svc loinc-code '[*]))
  ([^Svc svc loinc-code pattern]
   (d/q '[:find [(pull ?e pattern) ...]
          :in $ ?loinc-code pattern
          :where
          [?e :org.loinc.part.link/LoincNumber ?loinc-code]]
        (d/db (.-conn svc))
        loinc-code
        pattern)))

(defn fetch-part-from-links
  ([^Svc svc part-number]
   (fetch-part-from-links svc part-number '[*]))
  ([^Svc svc part-number pattern]
   (d/q '[:find [(pull ?e pattern) ...]
          :in $ ?part-number pattern
          :where
          [?e :org.loinc.part.link/PartNumber ?part-number]]
        (d/db (.-conn svc))
        part-number
        pattern)))

(defn all-classes
  "Return all classes in the LOINC dataset."
  [^Svc svc]
  (d/q '[:find [?class ...]
         :where
         [_ :org.loinc/CLASS ?class]]
       (d/db (.-conn svc))))

(defn all-loinc-numbers
  [^Svc svc]
  (d/q '[:find [?loinc-number ...]
         :where
         [_ :org.loinc/LOINC_NUM ?loinc-number]]
       (d/db (.-conn svc))))

(comment
  (def svc (open "/tmp/oink1"))
  (import-dir svc "/Users/mark/Downloads/Loinc_2.70")
  (count (all-loinc-numbers svc))
  (importer/list-files "/Users/mark/Downloads/Loinc_2.70")
  (close svc)
  (def ch (async/chan))
  (async/thread (importer/stream "/Users/mark/Downloads/Loinc_2.70" ch :batch-size 10 :types #{:org.loinc}))

  (def batch (async/<!! ch))
  (count (:data batch))
  (def conn (.-conn svc))
  (d/entity (d/db conn) [:org.loinc/LOINC_NUM "9770-9"])
  (d/touch (d/entity (d/db conn) [:org.loinc/LOINC_NUM "9770-9"]))

  (time (fetch-map-to svc "9770-9"))
  (time (fetch-loinc svc "9770-9"))
  (fetch-source-organization svc "32099-4")
  (time (d/q '[:find ?e ?long-common-name
               :in $ ?loinc-num
               :where
               [?e :org.loinc/LOINC_NUM ?loinc-num]
               [?e :org.loinc/LONG_COMMON_NAME ?long-common-name]]
             (d/db conn)
             "2951-2"))
  (d/q '[:find (count ?e)
         :in $ ?class
         :where
         [?e :org.loinc/CLASS ?class]]
       (d/db conn)
       "CHEM")

  ;; count the number of unique classes in LOINC -> 398 as of June 2021
  (d/q '[:find (count ?class) .
         :where
         [_ :org.loinc/CLASS ?class]]
       (d/db conn))

  (d/q '[:find [?class ...]
         :where
         [_ :org.loinc/CLASS ?class]]
       (d/db conn))

  (time (with-copyright svc "Pfizer"))
  (fetch-source-organization svc "69723-5")
  (with-copyright svc "WHO_HIV")
  (fetch-source-organization svc "45247-4")
  (fetch-multiaxial-hierarchy svc "45247-4")
  (fetch-multiaxial-hierarchy svc "LP373671-9")
  (fetch-multiaxial-hierarchy svc "LP14082-9")

  (fetch-multiaxial-hierarchy svc "57021-8")
  (time (fetch-loinc svc "57021-8"))

  (fetch-multiaxial-hierarchy svc "LP393878-6")
  (fetch-multiaxial-hierarchy svc "LP96800-5")
  (fetch-multiaxial-hierarchy svc "LP7833-9")
  (fetch-multiaxial-hierarchy svc "LP7803-2")
  (fetch-multiaxial-hierarchy svc "LP29693-6")

  (fetch-loinc svc "10005-7")
  (fetch-part svc "LP101394-7")
  (fetch-part svc "LP393878-6")  ;;; not clear why this doesn't exist!
  (fetch-part-from-links svc "LP393878-6")
  (fetch-part svc "LP100041-5")
  (fetch-part-code-mapping svc "LP100006-8")
  (snomed->loinc-parts svc 708299006)
  (loinc-part->snomed svc "LP100006-8")

  (fetch-loinc svc "2951-2")
  (time (loinc->parts svc "2951-2"))
  (time (loinc->parts svc "5778-6"))
  (map :org.loinc.part.link/PartName (loinc->parts svc "5778-6"))
  (close svc)

  (loinc->parts svc "21756-2")
  (fetch-loinc svc "21756-2")
  (fetch-loinc svc "21762-0")
  (->> (loinc->parts svc "21756-2")
       (map :org.loinc.part.link-primary/PartNumber)
       (map #(loinc-part->snomed svc %))
       (map :org.loinc.part.code-mapping/ExtCodeId))

  ;; this gets all of the part display names of any elements
  ;; in the multiaxial hierarchy for this loinc code.
  (->> (fetch-multiaxial-hierarchy svc "21756-2")
       (map :org.loinc.multiaxial-hierarchy/PATH_TO_ROOT)
       (map #(clojure.string/split % #"\."))
       flatten
       set
       (map #(fetch-part svc %))
       ;  (map :org.loinc.part/PartDisplayName)
       )

  (loinc-part->snomed svc "LP19786-0")

  (str/split (:org.loinc/RELATEDNAMES2 (fetch-loinc svc "21756-2")) #"; ")


  (fetch-part svc "LP409272-4")
  )