(ns com.eldrix.oink.cmd.server
  (:require [clojure.tools.logging.readable :as log]
            [com.eldrix.oink.core :as oink])
  (:import (com.eldrix.oink.core Svc)))


(defn start [^Svc svc {:keys [db port bind-address] :as params}]
  (log/info "not yet implemented"))