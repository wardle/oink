(ns com.eldrix.oink.store
  (:require [datalevin.core :as d]))

(defn open-store [dir]
  (d/create-conn dir))

(defn close-store [st]
  (d/close st))

(defn put-batch [st data]
  (d/transact! st data))

(comment
  (def st (open-store "/tmp/oink1"))

  )