(ns dpl.config
  (:require [clojure.string :as str]
            [dpl.config-local :as SECRETS]
            #+clj korma.db
            #+cljs [dpl.util :refer [browser? node-process]])
  #+clj (:import (java.net URI)))


(def api-port
  (let [port-varname "API_PORT"
        default 8000]
   #+cljs (if-not browser?
            (or (aget node-process "env" port-varname) default))
   #+clj (get (System/getenv) port-varname default)))

#+cljs
(def listen-port (if-not browser?
                  (or (aget node-process "env" "NODE_PORT") 3000)))

#+cljs
(def api-cfg
   {:host "localhost"
    :port (if browser? (.-port js/location) api-port)
    :root-path "/api"})

#+clj
(defn- convert-db-uri [db-uri]
  (let [[_ user password host port db]
        (re-matches #"postgres://(?:(.+):(.*)@)?([^:]+)(?::(\d+))?/(.+)"
                    db-uri)]
    {
      :user user
      :password password
      :host host
      :port (or port 80)
      :db db}))

#+clj
(def ral-db (if-let [uri (System/getenv "RAL_APP_DB")] ;; todo deprecate this
         (korma.db/postgres
          (convert-db-uri uri))
         {:classname "org.postgresql.Driver"
          :host "localhost"
          :port "5432"
          :db "rippedathlete"
          :subprotocol "postgresql"
          :subname "//localhost:5432/rippedathlete"
          :user "ral"
          :password "ral"}))

#+clj
(def recipe-db (if-let [uri (System/getenv "RECIPE_DB")] ;; todo deprecate this
         (korma.db/postgres
          (convert-db-uri uri))
         {:classname "org.postgresql.Driver"
          :host "localhost"
          :port "5432"
          :db "mealplans"
          :subprotocol "postgresql"
          :subname "//localhost:5432/mealplans"
          :user "mealplans"
          :password "mealplans"}))
