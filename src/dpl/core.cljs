(ns dpl.core
  (:require [dpl.server :as server]
            ;; [dpl.browser :as browser]
            [secretary.core :as secretary]
            [dpl.util :refer [browser?]]
            [dpl.browser :as browser]
            [dpl.data :as data]))

(if browser?
  ;; (.log js/console "hi")
  (let [path (-> js/window .-location .-pathname)
        handler (secretary/dispatch! path)]
    (browser/setup-app handler path))
  (set! *main-cli-fn* server/-main))
