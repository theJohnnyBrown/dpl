(ns dpl.api1
  (:require [liberator.core :refer [resource defresource]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.adapter.jetty :refer [run-jetty]]
            [compojure.core :refer [defroutes ANY]]
            [cheshire.core :refer [generate-string]]
            [dpl.models :as models]))

(def slz generate-string)

(defresource food [food-id]
  :available-media-types ["application/json"]
  :exists? (fn [ctx] (let [fd (models/get-foods {:id (Integer/parseInt food-id)})]
                      (if fd {:food fd} nil)))
  :handle-ok (fn [ctx] (slz (:food ctx))))

(defresource foods []
  :available-media-types ["application/json"]
  :handle-ok (fn [ctx]
               (slz (models/all-foods))))

(defresource foodsearch []
  :available-media-types ["application/json"]
  :handle-ok (fn [ctx]
               (let [query (get-in ctx [:request :params "q"])]
                 (models/search-foods query))))


(defresource recipe [rid]
  :available-media-types ["application/json"]
  :exists? (fn [ctx] (let [rp (models/get-recipes
                              {:id (Integer/parseInt rid)})]
                      (if rp {:recipe rp} nil)))
  :handle-ok (fn [ctx] (slz (:recipe ctx))))

(defresource recipes []
  :available-media-types ["application/json"]
  :handle-ok (fn [_] (slz (models/all-recipes))))

(defresource category [cid]
  :available-media-types ["application/json"]
  :exists? (fn [ctx] (let [ct (models/get-categories
                              {:id (Integer/parseInt cid)})]
                      (if ct {:category ct} nil)))
  :handle-ok (fn [ctx] (slz (:category ctx))))

(defresource categories []
  :available-media-types ["application/json"]
  :handle-ok (fn [_] (slz (models/all-categories))))

(defroutes app
  (ANY "/api/foods/search/" [] (foodsearch))
  (ANY "/api/foods/:foodid/" [foodid] (food foodid))
  (ANY "/api/foods/" [] (foods))
  (ANY "/api/recipes/:rid/" [rid] (recipe rid))
  (ANY "/api/recipes/" [] (recipes))
  (ANY "/api/categories/:cid/" [cid] (category cid))
  (ANY "/api/categories/" [] (categories)))

(def handler
  (-> app
      (wrap-params)))


;; (run-jetty #'handler {:port 8000})
