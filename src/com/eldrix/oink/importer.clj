(ns com.eldrix.oink.importer
  (:require [clojure.core.async :as async]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging.readable :as log])
  (:import (java.io File)))

(def file-types
  "An ordered catalogue of interesting LOINC file types."
  [{:path ["LoincTable" "Loinc.csv"]
    :type :org.loinc}
   {:path ["LoincTable" "SourceOrganization.csv"]
    :type :org.loinc.source-organization}
   {:path ["LoincTable" "MapTo.csv"]
    :type :org.loinc.map-to}
   {:path ["AccessoryFiles" "MultiAxialHierarchy" "MultiAxialHierarchy.csv"]
    :type :org.loinc.multiaxial-hierarchy}
   {:path ["AccessoryFiles" "DocumentOntology" "DocumentOntology.csv"]
    :type :org.loinc.document-ontology}
   {:path ["AccessoryFiles" "PartFile" "Part.csv"]
    :type :org.loinc.part}
   {:path ["AccessoryFiles" "PartFile" "PartRelatedCodeMapping.csv"]
    :type :org.loinc.part.code-mapping}
   {:path ["AccessoryFiles" "PartFile" "LoincPartLink_Primary.csv"]
    :type :org.loinc.part.link}
   {:path ["AccessoryFiles" "PartFile" "LoincPartLink_Supplementary.csv"]
    :type :org.loinc.part.link}
   {:path ["AccessoryFiles" "PanelsAndForms" "PanelsAndForms.csv"]
    :type :org.loinc.panels-and-forms}])

(defn- examine-file
  [^File f]
  (let [nio-path (.toPath f)
        ordered-file-types (map-indexed (fn [idx file-type] (assoc file-type :order idx)) file-types)
        file-type (first (filter #(.endsWith nio-path (str/join File/separator (:path %))) ordered-file-types))]
    (assoc file-type
      :file f
      :path (.getPath f))))

(defn list-files
  "List the interesting LOINC files from the directory 'dir'.
  Returns a sequence of maps with information about each file."
  [dir]
  (->> dir
       clojure.java.io/file
       file-seq
       (map #(examine-file %))
       (filter :type)
       (sort-by :order)))

(defn- csv-data->maps [csv-data]
  (map zipmap
       (->> (first csv-data)
            (map keyword)
            repeat)
       (rest csv-data)))

(defn- stream-csv
  "Stream the specified file, streaming batched results to the channel
  specified, blocking if channel not being drained. Each batch is a map with
  keys :type and :data. :data is a sequence of maps representing each column."
  [f out-c batchSize]
  (with-open [reader (io/reader f)]
    (let [csv-data (csv-data->maps (csv/read-csv reader))
          loinc-file (examine-file (io/as-file f))]
      (when (:type loinc-file)
        (let [batches (->> csv-data
                           (partition-all batchSize)
                           (map #(assoc loinc-file
                                   :data %)))]
          (log/info "Processing: '" (:path loinc-file) "' type: " (:type loinc-file))
          (doseq [batch batches] (async/>!! out-c batch)))))))

(defn stream
  "Blocking; stream LOINC data from the directory 'dir' to the core.async
  channel 'ch'."
  [dir ch & {:keys [batch-size close? types] :or {batch-size 500 close? true}}]
  (let [files (list-files dir)
        files' (if types (filter #(types (:type %)) files) files)]
    (log/info "Processing files from `" dir "`:" (count files) " files found" (when types (str ", " (count files') " to be processed.")))
    (doseq [loinc-file files']
      (stream-csv (:file loinc-file) ch batch-size))
    (when close? (async/close! ch))))

(comment
  (list-files "/Users/mark/Downloads/Loinc_2.70/")
  (def ch (async/chan))
  (async/go (stream "/Users/mark/Downloads/Loinc_2.70" ch))
  (async/<!! ch)
  )