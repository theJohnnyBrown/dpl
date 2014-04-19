(ns dpl.routes
  (:require [secretary.core :as secretary
             :include-macros true :refer [defroute]]
            [dpl.views :refer [dashboard] :as views]
            [dpl.util :refer [browser?]]
            [dpl.config :refer [api-cfg]]
            [om.core :as om :include-macros true]
            [dpl.data :as data]))

(defn node-handle [req resp next view])

(defn api-url [path]
  ;; this should end up in cljx
  (let [{:keys [host port root-path]} api-cfg]
    (str "http://" host ":" port  path )))


;; ^^ parametrize these vv^^vv with state-path, api-path

(defroute "/foods/" []
  (if browser?
    ;; get state over http, stick into views/app-state atom
    (fn [url state-atom & roots]
      (swap! state-atom merge  {:active :foods})
      (data/json-xhr
       {:method :get :url (api-url "/api/foods/")
        :on-complete
        (fn [result]
          (swap! state-atom merge  {:foods result})
          (when roots
           (doseq [[html-id view] roots]
             (om/root view state-atom
                      {:target (.getElementById js/document html-id)}))))}))

   ;; get state over http, use to render html
   ;; really should just apply same changes as above to atom, then
   ;; (views/render-page-to-string dashboard @views/app-state)
   (fn [req resp next]
     (data/json-xhr
      {:method :get :url (api-url "/api/foods/")
       :on-complete
       (fn [result]
         (.log js/console (str (first result)))
         (.send resp (views/render-page-to-string
                      dashboard {:active :foods
                                 :foods result})))}))))

(defn update-state [api-path state-path state-atom post-update]
  ;; this is more like "put the results of this request in to the atom given
  ;; maybs should use om/transact instead?
  (data/json-xhr
      {:method :get :url (api-url api-path)
       :on-complete
       (fn [result]
         (do
           (swap! state-atom assoc-in state-path result)
           (post-update result)))}))

(defn setup-om-roots [roots state-atom]
  ;; this is more like a view fn
  (when roots
    (doseq [[html-id view] roots]
      (om/root view state-atom
               {:target (.getElementById js/document html-id)}))))

(defn send-html-response [resp template state-atom]
  (.send resp (views/render-page-to-string template @state-atom)))

(defroute "/foods/:fid/" [fid]
  (if browser?
    (fn [url state-atom & roots]
      (swap! state-atom assoc-in [:active] :food-single)
      (update-state
       (str "/api/foods/" fid "/") [:food-single] state-atom
       (fn [result] (setup-om-roots roots state-atom))))

    (fn [req resp next]
      (let [temp-state (atom {})]
        (swap! temp-state assoc-in  [:active] :food-single)
        (update-state
         (str "/api/foods/" fid "/") [:food-single] temp-state
         (fn [result] (send-html-response resp dashboard temp-state)))))))

(defroute "/recipes/" []
  (if browser?
    ;; get state over http, stick into views/app-state atom
    (fn [url state-atom & roots]
      (swap! state-atom merge  {:active :recipes})
      (update-state
       "/api/recipes/" [:recipes] state-atom
       (fn [result] (setup-om-roots roots state-atom))))

   ;; get state over http, use to render html
   (fn [req resp next]
     (let [temp-state (atom {})]
       (swap! temp-state merge  {:active :recipes})
       (update-state
        "/api/recipes/" [:recipes] temp-state
        (fn [result] (send-html-response resp dashboard temp-state)))))))

(defroute "/recipes/:rid/" [rid]
  (if browser?
    (fn [url state-atom & roots]
      (swap! state-atom assoc-in [:active] :recipe-single)
      (update-state
       (str "/api/recipes/" rid "/") [:recipe-single] state-atom
       (fn [result] (setup-om-roots roots state-atom))))

    (fn [req resp next]
      (let [temp-state (atom {})]
        (swap! temp-state merge  {:active :recipe-single})
        (update-state
         (str "/api/recipes/" rid "/") [:recipe-single] temp-state
         (fn [result] (send-html-response resp dashboard temp-state)))))))
