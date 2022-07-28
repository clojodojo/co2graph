(ns demo.ui
  (:require
    [clojure.string :as string]
    [reagent.core :as r]
    [tick.core :as t]
    [goog.labs.format.csv :as csv]
    [tick.locale-en-us]))

(defonce co2-data (r/atom []))
(defonce event-data (r/atom []))

(def formatter (t/formatter "dd/MM/yyyy h:mm:ss a"))

(defn fetch! []
  (-> (js/fetch "/co2data.csv")
      (.then (fn [e] (.text e)))
      (.then (fn [text] (js->clj (csv/parse text))))
      (.then (fn [data]
              (drop-while (fn [[date _co2]]
                           (not (string/starts-with? date "19/07/2022 2:10:49 PM")))
                          data)))
      (.then (fn [data]
              (map (fn [[date co2 _temperature _humidity _pressure]]
                    [(t/<< (cljc.java-time.local-date-time/parse date formatter)
                           (t/new-duration 4 :hours)) co2])
                   (rest data))))
      (.then (fn [data] (reset! co2-data data))))

  (-> (js/fetch "/log.edn")
      (.then (fn [e] (.text e)))
      (.then (fn [text] (cljs.reader/read-string text)))
      (.then (fn [data] (reset! event-data data)))))

(defn app-view []
  [:div
   [:button {:on-click (fn []
                         (fetch!))}
    "fetch"]
   (when (and (seq @co2-data) (seq @event-data))
     (let [height 400
           width 1800
           interpolate (fn [x [x0 x1] [y0 y1]]
                         (let [m (/ (- y1 y0) (- x1 x0))
                               b (- y0 (* m x0))]
                           (+ (* m x) b)))
           start-date (ffirst @co2-data)
           end-date (first (last @co2-data))
           max-co2 1500 #_(apply js/Math.max (map second @co2-data))
           ->x (fn [x] (interpolate x
                                    [0 (t/seconds (t/between start-date end-date))]
                                    [0 width]))
           ->y (fn [y] (interpolate y
                                    [0 max-co2]
                                    [height 0]))
           band (fn [y1 y2 color]
                  [:rect {:x 0
                          :y (->y y2)
                          :width width
                          :height (- (- (->y y2) (->y y1)))
                          :fill color}])]
       [:div
        [:svg {:width width :height height}
         (for [co2 [400 500 600 700 800 900 1000 1100 1200 1300 1400 1500]]
           ^{:key co2}
           [:line {:title co2
                   :stroke "#00000033"
                   :x1 (->x 0)
                   :x2 (->x (t/seconds (t/between start-date end-date)))
                   :y1 (->y co2)
                   :y2 (->y co2)}])
         [band 0 420 "#00FF0044"]
         [band 1000 max-co2 "#FF000044"]
         [band 420 550 "#0000FF44"]
         #_(for [[date label] @event-data]
           ^{:key date}
           [:line {:title label
                   :stroke "black"
                   :x1 (->x (t/seconds (t/between start-date date)))
                   :x2 (->x (t/seconds (t/between start-date date)))
                   :y1 (->y 0)
                   :y2 (->y max-co2)}])
         [:path {:stroke "black"
                 :fill "none"
                 :d (->> @co2-data
                         (map (fn [[date co2]]
                                (str (->x (t/seconds (t/between start-date date)))
                                     ","
                                     (->y co2))))
                         (string/join " L ")
                         (str "M "))}]]]))])
