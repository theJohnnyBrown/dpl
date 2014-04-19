(ns dpl.data
  (:require [goog.events :as events]
            [dpl.util :refer [browser? node-require]]
            [clojure.string :as str])
  (:import [goog.net XhrIo]
           goog.net.EventType
           [goog.events EventType]))

(enable-console-print!)

(defn popn [n v]
  (loop [n n res v]
    (if (pos? n)
      (recur (dec n) (pop res))
      res)))

(defn sub [p0 p1]
  (vec (drop (- (count p0) (count p1)) p0)))

(defn tx-tag [{:keys [tag] :as tx-data}]
  (if (keyword? tag)
    tag
    (first tag)))

(defn subpath? [a b]
  (= a (popn (- (count b) (count a)) b)))

(defn error? [res]
  (contains? res :error))

(def ^:private meths
  {:get "GET"
   :put "PUT"
   :post "POST"
   :delete "DELETE"})

(defn json-serialize [clj-data]
  (if clj-data (.stringify js/JSON (clj->js clj-data)) ""))
(defn json-deserialize [raw-data]
  (if raw-data
   (js->clj (.parse js/JSON raw-data) :keywordize-keys true) nil))

(def json-headers
  #js {"Content-Type" "application/json; charset=utf-8"
             "Accept" "application/json"})
(defn json-xhr-bsr [{:keys [method url data on-complete on-error]}]
  (let [xhr (XhrIo.)]
    (events/listen xhr goog.net.EventType.SUCCESS
      (fn [e]
        (on-complete (json-deserialize (.getResponseText xhr)))))
    (events/listen xhr goog.net.EventType.ERROR
      (fn [e]
        (on-error {:error (.getResponseText xhr)})))
    (. xhr
      (send url (meths method) (json-serialize data) json-headers))))

(defn json-xhr-svr [{:keys [method url data on-complete on-error]}]
  (let [http (node-require "restler")
        req (.request http url
             (clj->js
              {:method (str/lower-case (get meths method))
               :headers json-headers
               :data (json-serialize data)}))]
    (doto req
      (.on "success"
           #((or on-complete identity) (json-deserialize %)))
      (.on "error" #((or on-error identity) (json-deserialize %))))))

(def json-xhr (if browser? json-xhr-bsr json-xhr-svr))
