(ns com.eldrix.oink.cmd.core
  (:gen-class)
  (:require [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [clojure.tools.logging :as log]
            [com.eldrix.oink.cmd.server :as server]
            [com.eldrix.oink.importer :as importer]
            [com.eldrix.oink.core :as oink]))

(defn import-from [{:keys [db]} args]
  (if db
    (let [import-dir (if (= 0 (count args)) "." (first args))]
      (with-open [svc (oink/open db)]
        (oink/import-dir svc import-dir)))
    (log/error "no database directory specified")))

(defn list-from [_ args]
  (let [dir (if (= 0 (count args)) "." (first args))
        metadata (oink/list-dir dir)
        metadata' (map #(select-keys % [:path :type]) metadata)]
    (pp/print-table metadata')))

(defn status [{:keys [db]} _]
  (if db
    (with-open [svc (oink/open db)]
      (pp/pprint (oink/get-status svc)))
    (log/error "no database directory specified")))

(defn build-index [{:keys [db]}]
  (log/info "not implemented"))

(defn serve [{:keys [db _port _bind-address] :as params} _]
  (if db
    (with-open [svc (oink/open db)]
      (log/info "starting terminology server " params)
      (server/start svc params))
    (log/error "no database directory specified")))

(def cli-options
  [["-p" "--port PORT" "Port number"
    :default 8080
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]

   ["-a" "--bind-address BIND_ADDRESS" "Address to bind"]

   ["-d" "--db PATH" "Path to database directory"
    :validate [string? "Missing database path"]]

   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Usage: oink [options] command [parameters]"
        ""
        "Options:"
        options-summary
        ""
        "Commands:"
        " import [path] Import LOINC distribution files from path specified."
        " list [path]   List importable files from the paths specified."
        " index          Build search index."
        " serve          Start a terminology server"
        " status         Displays status information"]
       (str/join \newline)))

(def commands
  {"import" {:fn import-from}
   "list"   {:fn list-from}
   "index"  {:fn build-index}
   "serve"  {:fn serve}
   "status" {:fn status}})

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn invoke-command [cmd opts args]
  (if-let [f (:fn cmd)]
    (f opts args)
    (exit 1 "error: not implemented")))

(defn -main [& args]
  (let [{:keys [options arguments summary errors]} (cli/parse-opts args cli-options)
        command (get commands ((fnil str/lower-case "") (first arguments)))]
    (cond
      ;; asking for help?
      (:help options)
      (println (usage summary))
      ;; if we have any errors, exit with error message(s)
      errors
      (exit 1 (str/join \newline errors))
      ;; if we have no command, exit with error message
      (not command)
      (exit 1 (str "invalid command\n" (usage summary)))
      ;; invoke command
      :else (invoke-command command options (rest arguments)))))

(comment

  )