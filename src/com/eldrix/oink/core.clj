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
            [clojure.string :as str]))

(def schema
  {:org.loinc/LOINC_NUM                        {:db/unique    :db.unique/identity
                                                :db/valueType :db.type/string}
   :org.loinc.map-to/LOINC                     {:db/valueType :db.type/string}
   :org.loinc.multiaxial-hierarchy/CODE        {:db/valueType :db.type/string}
   :org.loinc/EXTERNAL_COPYRIGHT_LINK          {:db/valueType :db.type/string}
   :org.loinc.source-organization/COPYRIGHT_ID {:db/valueType :db.type/string}
   :org.loinc.part/PartNumber                  {:db/valueType :db.type/string}
   })


(defn open [dir]
  (d/create-conn dir schema))

(defn close [conn]
  (d/close conn))

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

(defn list-dir [dir]
  (importer/list-files dir))

(defn get-status [conn]
  (log/info "not implemented"))
;;
;;
;;

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

(defn fetch-part
  ([conn part-number]
   (fetch-part conn part-number '[*]))
  ([conn part-number pattern]
   (d/q '[:find (pull ?e pattern) .
          :in $ ?part-number pattern
          :where
          [?e :org.loinc.part/PartNumber ?part-number]]
        (d/db conn)
        part-number
        pattern)))


(defn fetch-part-code-mapping
  ([conn part-number]
   (fetch-part-code-mapping conn part-number '[*]))
  ([conn part-number pattern]
   (d/q '[:find (pull ?e pattern) .
          :in $ ?part-number pattern
          :where
          [?e :org.loinc.part.code-mapping/PartNumber ?part-number]]
        (d/db conn)
        part-number
        pattern)))

(defn snomed->loinc-parts
  ([conn sctid]
   (snomed->loinc-parts conn sctid '[*]))
  ([conn sctid pattern]
   (d/q '[:find (pull ?e pattern)
          :in $ ?sctid pattern
          :where
          [?e :org.loinc.part.code-mapping/ExtCodeId ?sctid]
          [?e :org.loinc.part.code-mapping/ExtCodeSystem "http://snomed.info/sct"]]
        (d/db conn)
        (str sctid)
        pattern)))

(defn loinc-part->snomed
  ([conn part-number]
   (loinc-part->snomed conn part-number '[*]))
  ([conn part-number pattern]
   (d/q '[:find (pull ?e pattern) .
          :in $ ?part-number pattern
          :where
          [?e :org.loinc.part.code-mapping/PartNumber ?part-number]
          [?e :org.loinc.part.code-mapping/ExtCodeSystem "http://snomed.info/sct"]]
        (d/db conn)
        part-number
        pattern)))

(defn loinc->parts
  ([conn loinc-code]
   (loinc->parts conn loinc-code '[*]))
  ([conn loinc-code pattern]
   (d/q '[:find [(pull ?e pattern) ...]
          :in $ ?loinc-code pattern
          :where
          [?e :org.loinc.part.link/LoincNumber ?loinc-code]]
        (d/db conn)
        loinc-code
        pattern)))

(defn fetch-part-from-links
  ([conn part-number]
   (fetch-part-from-links conn part-number '[*]))
  ([conn part-number pattern]
  (d/q '[:find [(pull ?e pattern) ...]
         :in $ ?part-number pattern
         :where
         [?e :org.loinc.part.link/PartNumber ?part-number]]
       (d/db conn)
       part-number
       pattern)))

(defn all-classes
  "Return all classes in the LOINC dataset."
  [conn]
  (d/q '[:find [?class ...]
         :where
         [_ :org.loinc/CLASS ?class]]
       (d/db conn)))

(defn all-loinc-numbers
  [conn]
  (d/q '[:find [?loinc-number ...]
         :where
         [_ :org.loinc/LOINC_NUM ?loinc-number]]
       (d/db conn)))

(comment
  (def st (d/create-conn "/tmp/oink1" schema))
  (import-dir st "/Users/mark/Downloads/Loinc_2.70")
  (count (all-loinc-numbers st))
  (importer/list-files "/Users/mark/Downloads/Loinc_2.70")
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

  (time (with-copyright st "Pfizer"))
  (fetch-source-organization st "69723-5")
  (with-copyright st "WHO_HIV")
  (fetch-source-organization st "45247-4")
  (fetch-multiaxial-hierarchy st "45247-4")
  (fetch-multiaxial-hierarchy st "LP373671-9")
  (fetch-multiaxial-hierarchy st "LP14082-9")

  (fetch-multiaxial-hierarchy st "57021-8")
  (time (fetch-loinc st "57021-8"))

  (fetch-multiaxial-hierarchy st "LP393878-6")
  (fetch-multiaxial-hierarchy st "LP96800-5")
  (fetch-multiaxial-hierarchy st "LP7833-9")
  (fetch-multiaxial-hierarchy st "LP7803-2")
  (fetch-multiaxial-hierarchy st "LP29693-6")

  (fetch-loinc st "10005-7")
  (fetch-part st "LP101394-7")
  (fetch-part st "LP393878-6")
  (fetch-part-from-links st "LP393878-6")
  (fetch-part st "LP100041-5")
  (fetch-part-code-mapping st "LP100006-8")
  (snomed->loinc-parts st 708299006)
  (loinc-part->snomed st "LP100006-8")

  (fetch-loinc st "2951-2")
  (time (loinc->parts st "2951-2"))
  (time (loinc->parts st "5778-6"))
  (map :org.loinc.part.link/PartName (loinc->parts st "5778-6"))
  (d/close st)

  (loinc->parts st "21756-2")
  (fetch-loinc st "21756-2")
  (fetch-loinc st "21762-0")
  (->> (loinc->parts st "21756-2")
       (map :org.loinc.part.link-primary/PartNumber)
       (map #(loinc-part->snomed st %))
       (map :org.loinc.part.code-mapping/ExtCodeId))

  ;; this gets all of the part display names of any elements
  ;; in the multiaxial hierarchy for this loinc code.
  (->> (fetch-multiaxial-hierarchy st "21756-2")
       (map :org.loinc.multiaxial-hierarchy/PATH_TO_ROOT)
       (map #(clojure.string/split % #"\."))
       flatten
       set
       (map #(fetch-part st %))
     ;  (map :org.loinc.part/PartDisplayName)
       )

  (loinc-part->snomed st "LP19786-0")

  (clojure.string/split
    (:org.loinc/RELATEDNAMES2 (fetch-loinc st "21756-2"))
    #"; ")


  (fetch-part st "LP409272-4")
  )