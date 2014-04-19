(ns dpl.tests)

(require :reload
         '[aerogel.core :as ag]
         '[dpl.api1 :as api]
         '[dpl.models :as m :refer [filter-foods]]
         '[dpl.api1 :refer [slz]]
         '[clojure.pprint :refer :all])
(require :reload '[dpl.models :as m])

(count (slz (m/all-foods)))

(m/get-foods {:id 392})
(m/all-foods)

(m/get-categories {:id 88})
