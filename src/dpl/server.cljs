(ns dpl.server
  (:require [dpl.views :as views]
            [dpl.routes :as routes]
            [secretary.core :as secretary]
            [clojure.string :as str]
            [dpl.config :as conf :refer [api-cfg]]))

(def __dirname (if (exists? (js* "__dirname")) (js* "__dirname")))
; Define namespaced references to Node's externed globals:
(def node-require (if __dirname (js* "require")))
(def node-process (if __dirname (js* "process")))

(def home-url "/foods/")

(defn startswith [s q] (= (.indexOf s q) 0))

(defn -main [& args]
  (let [express (node-require "express")
        logfmt (node-require "logfmt")
        http-proxy (node-require "http-proxy")
        proxy (.createProxyServer http-proxy)
        app (express)
        port conf/listen-port
        api-proxy (fn [host port]
                    (fn [req res next]
                      (if (-> req .-url (startswith "/api/"))
                        (.web proxy req res
                          #js {"target" (routes/api-url (.-url req))})
                        (next))))]
    (.log js/console __dirname)
   (doto app

     ; Logger
     (.use (.requestLogger logfmt))

     ; Body parser
     (.use (.urlencoded express))
     (.use (.json express))

     ; Set assets folder
     (.use (.static express (str __dirname "/../resources/public")))

     (.use (api-proxy (:host api-cfg) (:port api-cfg)))

     (.get "*"
           (fn [req resp next]
             (let [handler (secretary/dispatch! (.-url req))]
               (if handler
                 (handler req resp next) (next)))))

     (.get "*" #(.send %2 "Not found" 404))

     (.listen port))
   (.log js/console (str "listening on " port))))
