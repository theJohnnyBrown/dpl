(ns aerogel.core
  (:require [korma.core :refer [select where]]))


(defn get-many [entity conds]
  (let [matches (select entity (where conds))
        n (count matches)]
    (cond
     (>= n 1) matches
     (< n 1) nil)))

(defn lookup-str [s] (-> s symbol resolve))

(defmacro setup-getters! [entity]
  (let [ns *ns*]
   `(do
      (intern ~ns (symbol (str "filter-" (:name ~entity)))
              (fn [conds#] (~get-many ~entity conds#)))
      (intern ~ns (symbol (str "get-" (:name ~entity)))
              (fn [conds#] (let [filter-func# (->> (str "filter-" (:name ~entity))
                                                  symbol (ns-resolve ~ns))
                                matches# (filter-func# conds#)
                                n# (count matches#)]
                            (cond
                             (= n# 1) (first matches#)
                             (> n# 1) (throw (Exception.
                                              (str "More than one one " (name ~entity)
                                                   " matches: " conds#)))
                             (< n# 1) nil))))
      (intern ~ns (symbol (str "all-" (:name ~entity)))
              (fn []
                (let [ffnc#
                      (ns-resolve ~ns
                        (symbol (str "filter-" (:name ~entity))))]
                  (ffnc# {})))))))
