(ns dpl.views
  (:require-macros [hiccups.core :as hiccups]
                   [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [cljs.core.async :refer [put! chan <!]]
            [formative.core :as f]
            [formative.parse :as fp]
            [formative.render :as fr]
            [formative.util :as fu]
            [sablono.core :as html :refer-macros [html]]
            [clojure.string :as str]
            [dpl.util :refer [__dirname browser? React map->css]]
            [dpl.models :as m]
            hiccups.runtime)
   (:import goog.history.Html5History))

(enable-console-print!)

(defn static [filename]
  (str "/" filename))

;; render components to string. Used on the server
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn render-cpt-to-string [component state]
  (.renderComponentToString
   React
   (om/build component state)))

(defn render-page-to-string [template state]
  (str "<!DOCTYPE html>" (hiccups/html (template state))))

;; There's gonna be a bunch of form rendering stuff

(defn render-default-component-input [field & [opts]]
  (let [attrs (fr/get-input-attrs field [:type :name :id :class :value :autofocus
                                      :checked :disabled :href :style :src :size
                                      :readonly :tabindex :onChange :onClick
                                      :onfocus :onblur :placeholder :autofill
                                      :multiple :title])
        attrs (if (and (= :submit (:type attrs))
                       (empty? (:value attrs)))
                (dissoc attrs :value)
                (assoc attrs :value (fr/render-input-val field)))
        attrs (assoc attrs :type (name (or (:type attrs) :text)))]
    (list
      (when-let [prefix (:prefix opts)]
        [:span.input-prefix prefix])
      [:input attrs])))

(defn maybe-deref [atom-or-val]
  (cond
   (instance? om.core/MapCursor atom-or-val) (om.core/value atom-or-val)
   (isa? IDeref atom-or-val) @atom-or-val
   :else atom-or-val))

(defn render-default-input-component [field]
  (let [chan (:channel field)
        onchange
           (fn [e]
             (let [fd (maybe-deref field)] ;; outside of render phase
               (when-let [old-handler (or (:onChange fd) (:onchange fd))]
                 (old-handler e))
               (.log js/console (str "onchange triggered"))
               (put! chan (-> e .-target .-value))))]
    (let [fd (if chan (assoc field :onChange onchange) field)]
       [:span.input-component-wrapper
        (render-default-component-input fd)])))

(remove-method fr/render-field :checkboxes)
(remove-method fr/render-field :checkbox)
(defn checkbox-component [field owner]
  (reify
    om/IInitState
    (init-state [_]
      {:channel (chan)})
    om/IWillMount
    (will-mount [this]
      (let [channel (om/get-state owner :channel)]
        (go (loop []
          (let [val (<! channel)]
            (.log js/console (str "recieved on channel - val: " val))
            (om/update! field (merge @field (:checked true)))
            (recur))))))
    ;; om/IRender
    ;; (render [_]
    ;;   (.log js/console "render called")
    ;;   (html
    ;;    (render-default-input-component field)))
    om/IRenderState
    (render-state [this component-state]
      (html
       (render-default-input-component
        (assoc field
          :channel (:channel component-state)))
      ;; (vector
      ;;  (when (contains? field :unchecked-value)
      ;;    (fr/render-default-input {:name (:name field)
      ;;                           :type :hidden
      ;;                           :value (:unchecked-value field)}))
      ;;  (render-default-input-component
      ;;   (assoc field
      ;;     :channel (:channel component-state))))
      ))))

(defmethod fr/render-field :checkboxes [field ]
  (let [vals (set (map str (:value field)))
        opts (fu/normalize-options (:options field))
        fname (str (name (:name field)) "[]")
        cols (:cols field 1)
        cb-per-col (+ (quot (count opts) cols)
                      (if (zero? (rem (count opts) cols))
                        0 1))
        build-cb (fn [oval olabel]
                   (let [id (str (:id field) "__" (fr/opt-slug oval))]
                     [:div.cb-shell
                      [:label.checkbox {:for id} " "
                       [:span.cb-input-shell
                        (let [fd {:name fname :id id
                                  :checked (contains? vals (str oval))
                                  :type :checkbox :value (str oval)}
                              whatever
                              (om/build
                               checkbox-component fd)]
                          whatever
                          )] " "
                       [:span.cb-label
                        [:span {:style {:white-space "nowrap"}} olabel]]]]))]
    [:div.checkboxes
     ;; FIXME: this prevents checkbox values from being absent in the submitted
     ;; request, but at the cost of including an empty value which must be
     ;; filtered out. We can't use an empty input without the "[]" suffix
     ;; because nested-params Ring middleware won't allow it.
     (fr/render-field {:name fname :type :hidden})
     (for [[col colopts] (map vector
                              (range 1 (inc cols))
                              (partition-all cb-per-col opts))]
       [:div {:class (str "cb-col cb-col-" col)}
        (for [[oval olabel subopts] colopts]
           (if (empty? subopts)
             (build-cb oval olabel)
             [:div.cb-group
              [:h5.cb-group-heading olabel]
              (for [[oval olabel] (fu/normalize-options subopts)]
                (build-cb oval olabel))]))])]))

(defmethod fr/render-field :html-textarea [field]
  (let [attrs (fr/get-input-attrs field [:name :id :class :autofocus
                                      :disabled :style :size :rows :cols :wrap
                                      :readonly :tabindex :onchange :onclick
                                      :onfocus :onblur :placeholder])]
    [:textarea attrs (fr/render-input-val field)]))

;; (def EMPTY_MAPCURSOR (om.core/MapCursor. nil {} []))
;; (extend-type om.core/MapCursor
;;   IEmptyableCollection
;;   (-empty  [coll] EMPTY_MAPCURSOR))

;; ^^ end form rendering stuff

;; browser render and routing functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def hist
  (if browser?
   (doto (Html5History.) (.setUseFragment false) (.setEnabled true))))

(defn strip-leading-slash [s] (if (= (str (first s)) "/")
                                (str/join (rest s)) s))

(def app-state (atom {}))

(defn update-active! [e] ;; e should be the click event of an <a>
  ;; perform the equivalent of a page load without using the network
  (do
    (.setToken hist
               (-> e .-target (.getAttribute "href") strip-leading-slash)
               (-> e .-target .-title))
    (.preventDefault e)))

;; components
(defn- li [active current] (get {current :li.active} active :li))
(defn page-nav [{active :active :as app-state}]
 (om/component
  (html
   [:ul.nav.nav-sidebar
    [(li active :recipes)
     [:a {:href "/recipes/" :onClick update-active!} "Recipes"]]
    [(li active :foods)
     [:a {:href "/foods/" :onClick update-active!} "Foods"]]])))

(defn recipes [{recipes :recipes :as app-state}]
  (om/component
   (html
    [:div.col-sm-9.col-sm-offset-3.col-md-10.col-md-offset-2.main
     [:h1.page-header "Recipes"]
     [:div.table-responsive
      [:table.table.table-striped
       [:thead
        [:tr
         [:th "#"]
         [:th "Header"]
         [:th "Header"]
         [:th "Header"]
         [:th "Header"]]]
       [:tbody
        (for [rp recipes]
         [:tr
          [:td [:a
                {:href (str "/recipes/" (:id rp) "/") :onClick update-active!}
                (:title rp)]]
          [:td "wurdz"]
          [:td "ipsum"]
          [:td "dolor"]
          [:td "sit"]])
        [:tr
         [:td "1,001"]
         [:td "Lorem"]
         [:td "ipsum"]
         [:td "dolor"]
         [:td "sit"]]]]]
     [:h2.sub-header "Section title"]
     [:div.row.placeholders
      [:div.col-xs-6.col-sm-3.placeholder
       [:img.img-responsive
        {:alt "Generic placeholder thumbnail",
         :data-src "holder.js/200x200/auto/sky"}]
       [:h4 "Label"]
       [:span.text-muted "Something else"]]
      [:div.col-xs-6.col-sm-3.placeholder
       [:img.img-responsive
        {:alt "Generic placeholder thumbnail",
         :data-src "holder.js/200x200/auto/vine"}]
       [:h4 "Label"]
       [:span.text-muted "Something else"]]
      [:div.col-xs-6.col-sm-3.placeholder
       [:img.img-responsive
        {:alt "Generic placeholder thumbnail",
         :data-src "holder.js/200x200/auto/sky"}]
       [:h4 "Label"]
       [:span.text-muted "Something else"]]
      [:div.col-xs-6.col-sm-3.placeholder
       [:img.img-responsive
        {:alt "Generic placeholder thumbnail",
         :data-src "holder.js/200x200/auto/vine"}]
       [:h4 "Label"]
       [:span.text-muted "Something else"]]]
     ])))

(defn food-single [{fd :food-single :as app-state}]
  (om/component
   (html
    [:div.col-sm-9.col-sm-offset-3.col-md-10.col-md-offset-2.main
     [:h1.page-header (:name fd)]
     [:p "ya ..."]])))

(def unit-choices [[0 "g"] [1 "l"] [2 "oz"]])

(def category-choices
  [[0 "/recipe-terms/new-recipes"] [1 "/recipe-terms/general-audience"] [2 "/recipe-terms/breakfast"] [3 "/recipe-terms/middle-eastern"] [4 "/recipe-terms/microwave"] [5 "/recipe-terms/desserts"] [6 "/recipe-terms/blender"] [7 "/recipe-terms/side-dishes"] [8 "/recipe-terms/no-cooking-required"] [9 "/recipe-terms/ready-30-minutes-or-less"] [10 "/recipe-terms/oven"] [11 "/recipe-terms/new"] [12 "/recipe-terms/skillet"] [13 "/recipe-terms/older-adults"] [14 "/recipe-terms/salads"] [15 "/recipe-terms/eat-more-fruits-and-vegetables"] [16 "/recipe-terms/american-indian"] [17 "/recipe-terms/eat-fat-free-low-fat-dairy-foods-containing-calcium"] [18 "/recipe-terms/southern"] [19 "/recipe-terms/asian"] [20 "/recipe-terms/parents-young-children"] [21 "/recipe-terms/reduce-sodium-intake"] [22 "/recipe-terms/new-recipe"] [23 "/recipe-terms/breads"] [24 "/recipe-terms/sauces-condiments-dressings"] [25 "/recipe-terms/vegetarian"] [26 "/recipe-terms/main-dish"] [27 "/recipe-terms/soups-stews"] [28 "/recipe-terms/snacks-sandwiches"] [29 "/recipe-terms/stovetop-hot-plate"] [30 "/recipe-terms/wok"] [31 "/recipe-terms/eat-whole-grains"] [32 "/recipe-terms/toaster"] [33 "/recipe-terms/beverages"] [34 "/recipe-terms/appetizers"] [35 "/recipe-terms/parents-teens"] [36 "/recipe-terms/hispanic"] [37 "/recipe-terms/eat-less-saturated-fats-trans-fats-and-cholesterol"]])

(defn rf-spec [rp]

  (let [food-field-name
        (fn [[amount [fid fname _]]]
          (str "recipe-" (:id rp) ".food-" fid ".name"))
        food-units-field-name
        (fn [[amount [fid fname _]]]
          (str "recipe-" (:id rp) ".food-" fid ".units"))
        rpval (maybe-deref rp)
        values (apply assoc
                       (concat [rpval]
                               (flatten
                                (for [ing (:ingredients rp)]
                                  [(keyword (food-field-name ing))
                                   (-> ing second second)]))))]

   {:fields (vec
             (flatten
              (concat
               [
                {:name :title :type :text}
                {:name :portions :type :select
                 :options (for [i (range 1 5)] [i (str i)])}
                {:name :method :type :html-textarea}
                ]
               (for [ing (:ingredients rp)]
                 [
                  {:name (food-field-name ing) :label ""}
                  {:name (food-units-field-name ing)
                   :type :select :options unit-choices :label ""}
                  ])
               [{:name :link :type :text}
                {:name :categories :type :checkboxes :options category-choices}])))

    :values values
    :validator m/validate-recipes}))

(defn recipe-form [rp]
  (f/render-form (rf-spec rp)))

;; initial state of component is form spec with data from rp
;; changing form updates that form-spec
(defn recipe-single [{rp :recipe-single :as app-state}]
  (om/component
   (html
    [:div.col-sm-9.col-sm-offset-3.col-md-10.col-md-offset-2.main
     [:h1.page-header (:title rp)]
     [:h2.sub-header "Edit recipe"]
     (recipe-form rp)])))


(defn foods [{foods :foods :as app-state}]
  (om/component
   (html
    [:div.col-sm-9.col-sm-offset-3.col-md-10.col-md-offset-2.main
     [:h1.page-header "Foods"]
     [:div.table-responsive
      [:table.table.table-striped
       [:thead
        [:tr
         [:th "#"]
         [:th "Name"]
         [:th "Category"]
         [:th "Header"]
         [:th "Header"]]]
       [:tbody
        (for [fd foods]
         [:tr
          [:td [:a
                {:href (str "/foods/" (:id fd) "/") :onClick update-active!}
                (:name fd)]]
          [:td (:category fd)]
          [:td "ipsum"]
          [:td "dolor"]
          [:td "sit"]])]]]
     [:h2.sub-header "Section Title"]
     [:div.row.placeholders
      [:div.col-xs-6.col-sm-3.placeholder
       [:img.img-responsive
        {:alt "Generic placeholder thumbnail",
         :data-src "holder.js/200x200/auto/sky"}]
       [:h4 "Label"]
       [:span.text-muted "Something else"]]
      [:div.col-xs-6.col-sm-3.placeholder
       [:img.img-responsive
        {:alt "Generic placeholder thumbnail",
         :data-src "holder.js/200x200/auto/vine"}]
       [:h4 "Label"]
       [:span.text-muted "Something else"]]
      [:div.col-xs-6.col-sm-3.placeholder
       [:img.img-responsive
        {:alt "Generic placeholder thumbnail",
         :data-src "holder.js/200x200/auto/sky"}]
       [:h4 "Label"]
       [:span.text-muted "Something else"]]
      [:div.col-xs-6.col-sm-3.placeholder
       [:img.img-responsive
        {:alt "Generic placeholder thumbnail",
         :data-src "holder.js/200x200/auto/vine"}]
       [:h4 "Label"]
       [:span.text-muted "Something else"]]]])))


(defn page-main [{active :active :as app-state}]
  (let [page-component (active {:foods foods :recipes recipes
                                :food-single food-single
                                :recipe-single recipe-single})]
    (page-component app-state)))

;; fns app state->string
(defn dashboard [app-state]
  "app state -> string of html"
  [:html
   {:lang "en"}
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:content "IE=edge", :http-equiv "X-UA-Compatible"}]
    [:meta
     {:content "width=device-width, initial-scale=1", :name "viewport"}]
    [:meta {:content "", :name "description"}]
    [:meta {:content "", :name "author"}]
    [:link {:href "../../assets/ico/favicon.ico", :rel "shortcut icon"}]
    [:title "Dashboard Template for Bootstrap"]
    "<!-- compiled and minified CSS -->"
    [:link
     {:href
      "//netdna.bootstrapcdn.com/bootstrap/3.1.1/css/bootstrap.min.css",
      :rel "stylesheet"}]
    "<!-- compiled and minified JavaScript -->"
    [:script
     {:src
      "https://ajax.googleapis.com/ajax/libs/jquery/1.11.0/jquery.min.js"}]
    [:script
     {:src
      "//netdna.bootstrapcdn.com/bootstrap/3.1.1/js/bootstrap.min.js"}]
    [:script {:src "http://getbootstrap.com/assets/js/docs.min.js"}]
    "<!-- Custom styles for this template -->"
    [:link {:rel "stylesheet", :href "/css/dashboard.css"}]
    "<!-- HTML5 shim and Respond.js IE8 support of HTML5 elements and media queries -->"
    "<!--[if lt IE 9]>\n      <script src=\"https://oss.maxcdn.com/libs/html5shiv/3.7.0/html5shiv.js\"></script>\n      <script src=\"https://oss.maxcdn.com/libs/respond.js/1.4.2/respond.min.js\"></script>\n    <![endif]-->"]
   [:body
    [:div.navbar.navbar-inverse.navbar-fixed-top
     {:role "navigation"}
     [:div.container-fluid
      [:div.navbar-header
       [:button.navbar-toggle
        {:data-target ".navbar-collapse",
         :data-toggle "collapse",
         :type "button"}
        [:span.sr-only "Toggle navigation"]
        [:span.icon-bar]
        [:span.icon-bar]
        [:span.icon-bar]]
       [:a.navbar-brand {:href "#"} "Diet Planner"]]
      [:div.navbar-collapse.collapse
       [:form.navbar-form.navbar-right
        [:input.form-control
         {:placeholder "Search...", :type "text"}]]]]]
    [:div.container-fluid
     [:div.row
      [:div.col-sm-3.col-md-2.sidebar
       [:div#left-nav
        (render-cpt-to-string page-nav app-state)]
       [:ul.nav.nav-sidebar
        [:li [:a {:href "#"} "Overview"]]
        [:li [:a {:href "#"} "Reports"]]
        [:li [:a {:href "#"} "Analytics"]]
        [:li [:a {:href "#"} "Export"]]]
       [:ul.nav.nav-sidebar
        [:li [:a {:href ""} "Nav item"]]
        [:li [:a {:href ""} "Nav item again"]]
        [:li [:a {:href ""} "One more nav"]]
        [:li [:a {:href ""} "Another nav item"]]
        [:li [:a {:href ""} "More navigation"]]]
       [:ul.nav.nav-sidebar
        [:li [:a {:href ""} "Nav item again"]]
        [:li [:a {:href ""} "One more nav"]]
        [:li [:a {:href ""} "Another nav item"]]]]
      [:div#page-main
       (render-cpt-to-string page-main app-state)]]]
    "<!-- Bootstrap core JavaScript\n    ================================================== -->"
    "<!-- Placed at the end of the document so the pages load faster -->"
    [:script
     {:src
      "https://ajax.googleapis.com/ajax/libs/jquery/1.11.0/jquery.min.js"}]
    [:script
     {:src (static "js/app-dev.js")}]]])
