(ns dpl.browser
  (:require [om.core :as om :include-macros true]
            [goog.events :as ev]
            [dpl.views :as views]
            [dpl.routes :as rt]
            [secretary.core :as secretary])
  (:import goog.history.EventType))

(def browser? (exists? js/document))

(defn ^:export set-foods [] (reset! views/app-state {:active :foods}))

;; setup navigation. See http://closure-library.googlecode.com/git-history/6b23d1e05147f2e80b0d619c5ff78319ab59fd10/closure/goog/demos/html5history.html
(defn setup-app [start-handler start-path]
  (do
    (start-handler start-path views/app-state
                   ["page-main" views/page-main]
                   ["left-nav" views/page-nav])
    (ev/listen ;; when token changes, update view
     views/hist (.-NAVIGATE EventType)
     #(let [path (str "/" (.-token %))
            handler (secretary/dispatch! path)]
        (handler path views/app-state)))))
