(ns dpl.util
  (:require [clojure.string :as str]))

(def __dirname (if (exists? (js* "__dirname")) (js* "__dirname")))
; Define namespaced references to Node's externed globals:
(def node-require (if __dirname (js* "require")))
(def node-process (if __dirname (js* "process")))

(def browser? (exists? js/document))
(def React (if browser? js/React (node-require "react")))

(defn map->css [map]
  (str/join
   (for [[k v] map]
     (str (name k) ": " v ";"))))
