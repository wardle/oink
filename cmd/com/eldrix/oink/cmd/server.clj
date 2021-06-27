(ns com.eldrix.oink.cmd.server
  (:require [cheshire.core :as json]
            [cheshire.generate :as json-gen]
            [clojure.string :as str]
            [clojure.tools.logging.readable :as log]
            [com.eldrix.oink.core :as oink]
            [io.pedestal.http :as http]
            [io.pedestal.http.content-negotiation :as conneg]
            [io.pedestal.http.route :as route]
            [io.pedestal.interceptor :as intc]
            [io.pedestal.interceptor.error :as intc-err])
  (:import (com.eldrix.oink.core Svc)
           (java.time.format DateTimeFormatter)
           (java.time LocalDate)
           (com.fasterxml.jackson.core JsonGenerator)
           (java.util Locale)))

(set! *warn-on-reflection* true)

(def supported-types ["text/html" "application/edn" "application/json" "text/plain"])
(def content-neg-intc (conneg/negotiate-content supported-types))

(defn response [status body & {:as headers}]
  {:status  status
   :body    body
   :headers headers})

(def ok (partial response 200))
(def not-found (partial response 404))

(defn accepted-type
  [context]
  (get-in context [:request :accept :field] "application/json"))

(json-gen/add-encoder LocalDate
                      (fn [^LocalDate o ^JsonGenerator out]
                        (.writeString out (.format (DateTimeFormatter/ISO_DATE) o))))

(defn transform-content
  [body content-type]
  (case content-type
    "text/html" body
    "text/plain" body
    "application/edn" (.getBytes (pr-str body) "UTF-8")
    "application/json" (.getBytes (json/generate-string body) "UTF-8")))

(defn coerce-to
  [response content-type]
  (-> response
      (update :body transform-content content-type)
      (assoc-in [:headers "Content-Type"] content-type)))

(def coerce-body
  {:name ::coerce-body
   :leave
         (fn [context]
           (if (get-in context [:response :headers "Content-Type"])
             context
             (update-in context [:response] coerce-to (accepted-type context))))})

(defn inject-svc
  "A simple interceptor to inject terminology service 'svc' into the context."
  [svc]
  {:name  ::inject-svc
   :enter (fn [context] (update context :request assoc ::service svc))})

(def entity-render
  "Interceptor to render an entity '(:result context)' into the response."
  {:name :entity-render
   :leave
         (fn [context]
           (if-let [item (:result context)]
             (assoc context :response (ok item))
             context))})

(def service-error-handler
  (intc-err/error-dispatch
    [context err]
    [{:exception-type :java.lang.NumberFormatException :interceptor ::get-search}]
    (assoc context :response {:status 400
                              :body   (str "Invalid search parameters; invalid number: " (ex-message (:exception (ex-data err))))})
    [{:exception-type :java.lang.IllegalArgumentException :interceptor ::get-search}]
    (assoc context :response {:status 400 :body (str "invalid search parameters: " (ex-message (:exception (ex-data err))))})

    [{:exception-type :clojure.lang.ExceptionInfo :interceptor ::get-search}]
    (assoc context :response {:status 400 :body (str "invalid search parameters: " (ex-message (:exception (ex-data err))))})

    :else
    (assoc context :io.pedestal.interceptor.chain/error err)))

(def get-concept
  {:name  ::get-concept
   :enter (fn [context]
            (when-let [loinc-code (get-in context [:request :path-params :loinc-code])]
              (when-let [concept (oink/fetch-loinc (get-in context [:request ::service]) loinc-code)]
                (assoc context :result concept))))})


(def common-routes [coerce-body content-neg-intc entity-render])
(def routes
  (route/expand-routes
    #{["/v1/loinc/:loinc-code" :get (conj common-routes get-concept)]
      }))

;; TODO(mw): make a configuration option
(def service-map
  {::http/routes routes
   ::http/type   :jetty
   ::http/port   8080})

(defn start-server
  ([^Svc svc {:keys [port bind-address join?] :as opts :or {join? true}}]
   (let [cfg (cond-> {}
                     port (assoc ::http/port port)
                     bind-address (assoc :http/host bind-address))]
     (-> (merge service-map cfg)
         (assoc ::http/join? join?)
         (http/default-interceptors)
         (update ::http/interceptors conj (intc/interceptor (inject-svc svc)))
         http/create-server
         http/start))))

(defn stop-server [server]
  (http/stop server))

;; For interactive development
(defonce server (atom nil))

(defn start-dev [svc port]
  (reset! server
          (start-server svc {:port port :join? false})))

(defn stop-dev []
  (http/stop @server))

(comment

  (def svc (com.eldrix.oink.core/open "/tmp/oink1"))
  (start-dev svc 8080)
  (stop-dev)
  )
