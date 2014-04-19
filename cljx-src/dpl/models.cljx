(ns dpl.models
  (:require [jkkramer.verily :as v]
            [dpl.config :as conf]
            [clojure.string :as str]
            #+clj [cheshire.core :refer [parse-string]]
            #+clj [aerogel.core :as ag]
            #+clj [korma.core :refer [defentity belongs-to database where
                                      select with fields transform exec-raw]]))

#+clj
(def db conf/recipe-db)

; categories
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def validate-categories (v/required [:name]))

#+clj
(defentity categories (database db))
#+clj
(ag/setup-getters! categories)

;; foods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def validate-foods
  (v/combine
   (v/required [:name :category])
   (v/min-length 2 :name)

   #+clj (fn [fd]
           (if-not (get-categories {:id (:category_id fd)})
             {:keys [:category]
              :msg (str "Category: " (:category fd) " does not exist")}))))

#+clj
(defentity foods
  (transform
   (fn [m]
     (assoc (select-keys m [:id :name :category_id])
        :category {:id (:category.id m)
                   :name (:category.name m)})))
  (database db)
  (belongs-to categories {:fk :category_id}))
#+clj
(ag/setup-getters! foods)
#+clj
(defn filter-foods [params]
  (let [matches (select foods
                  (with categories
                    (fields [:id :category.id] [:name :category.name]))
                  (where params))
        n (count matches)]
    (if (>= n 1) matches nil)))

#+clj
(defn food-search-sql [qy]
  (let [raw-query (str/join " | "
                   (-> qy
                       (str/replace #"!|,|\&|\(|\)", " ")
                       (str/split #"\s+")))]
    (str
     "select
        id,
        name,
        ts_rank_cd(
          setweight(
            to_tsvector(
              'english', substring(name from '([^,]*,?([^,]*,?)?)')), 'A') ||
            to_tsvector('english', name), to_tsquery('english', '" raw-query "'), 1)
        as rank
      from foods
      where to_tsvector('english', name) @@ to_tsquery('english', '" raw-query "')
      order by rank desc limit 5")))

#+clj
(defn search-foods [qy]
  (exec-raw db
   [(food-search-sql qy) []] :results))


; recipes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def validate-recipes
  (v/combine
   (v/required [:title])
   (v/min-length 2 :title)
      (v/min-length 1 :ingredients)
   #+clj (fn [rp]
           (filter identity
            (for [[amount [fid fname _]] (:ingredients rp)]
              (if-not (get-foods {:id (:id fid)})
                {:keys [:ingredients]
                 :msg (str "Food: " (:name fname) " does not exist")}))))))

#+clj
(def recipes (atom (vec
                  (for [[fid fd] (map-indexed
                                  vector
                                  (parse-string (slurp "recipes.json") true))]
                      (assoc fd
                        :id fid
                        :link (str "/api/recipes/" fid "/"))))))
#+clj
(defn all-recipes [] @recipes)
#+clj
(defn get-recipes [params]
  (->> @recipes
       (filter #(= (merge % params) %))
       first))
