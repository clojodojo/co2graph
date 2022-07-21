(ns demo.ui
 (:require
    [clojure.string :as string]
    [reagent.core :as r]
    [tick.core :as t]
    [goog.labs.format.csv :as csv]
    [tick.locale-en-us]))

(defonce state (r/atom []))

(def formatter (t/formatter "dd/MM/yyyy h:mm:ss a"))

(defn fetch! []
  (-> (js/fetch "/co2data.csv")
      (.then (fn [e] (.text e)))
      (.then (fn [text] (js->clj (csv/parse text))))
      (.then (fn [data]
              (drop-while (fn [[date co2]]
                           (not (string/starts-with? date "19/07/2022 2:17:51 PM")))
                          data)))
      (.then (fn [data]
              (map (fn [[date co2 _temperature _humidity _pressure]]
                    [(cljc.java-time.local-date-time/parse date formatter) co2])
                   (rest data))))
      (.then (fn [data] (println (count data)) (reset! state data)))))


(defn app-view []
  [:div
   [:button {:on-click (fn []
                         (fetch!))}
    "fetch"]
   (let [height 200
         width 700
         interpolate (fn [x [x0 x1] [y0 y1]]
                       (let [m (/ (- y1 y0) (- x1 x0))
                             b (- y0 (* m x0))]
                         (+ (* m x) b)))
         start-date (ffirst @state)
         end-date (first (last @state))
         max-co2 1300 #_(apply js/Math.max (map second @state))
         ->x (fn [x] (interpolate x [0 (t/seconds (t/between start-date end-date))] [0 width]))
         ->y (fn [y] (interpolate y [0 max-co2] [height 0]))
         band (fn [y1 y2 color]
                 [:rect {:x 0 :y (->y y2)
                         :width width :height (- (- (->y y2) (->y y1)))
                         :fill color}])]
    [:svg {:width width :height height}
     [band 0 420 "#00FF0044"]
     [band 1000 max-co2 "#FF000044"]
     [band 420 550 "#0000FF44"]
     [:path {:stroke "black"
             :fill "none"
             :d (->> @state
                     (map-indexed (fn [i [date co2]]
                                   (str (->x (t/seconds (t/between start-date date)))
                                        ","
                                        (->y co2))))
                     (string/join " L ")
                     (str "M "))}]])])


#_(js/alert "yo")
#_(+ 1 2)
