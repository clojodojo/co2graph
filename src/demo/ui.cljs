(ns demo.ui
  (:require
    [clojure.string :as string]
    [reagent.core :as r]
    [tick.core :as t]
    [goog.labs.format.csv :as csv]
    [tick.timezone]
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
                    [(cljc.java-time.local-date-time/parse date formatter) co2])
                   (rest data))))
      (.then (fn [data] (reset! co2-data data))))

  (-> (js/fetch "/log.edn")
      (.then (fn [e] (.text e)))
      (.then (fn [text] (cljs.reader/read-string text)))
      ;; TODO fix the data points to be in toronto time
      #_(t/<< (cljc.java-time.local-date-time/parse date formatter)
              (t/new-duration 4 :hours))
      (.then (fn [data] (reset! event-data data)))))


(defn date-range [start end step]
  (loop [dates (list start)]
    (let [date (first dates)]
     (if (< date end)
       (recur (conj dates (t/>> date step)))
       (reverse dates)))))



;; find first "midnight" after start

#_(date-range (t/now)
              (t/>> (t/now) (t/new-duration 10 :days))
              (t/new-duration 1 :days))
#_(t/at (t/date (t/now)) (t/midnight))
#_(t/>> (t/now) (t/new-duration 1 :days))

#_(t/in (t/now) "America/Toronto")


;;  [[1  true]
;;    ....
;;   [6  false]
;;   ....
;;   [8  true]
;;    ....
;;   [10  false]]

;; ???

;; [[1 6] [8 10]]
;;
;; line 1->6,  line 8->10


(defn on-states [data f]
 (reduce (fn [accum record]
            (case (f record)
              nil accum
              true (conj accum [(first record)])
              false (update accum (dec (count accum)) conj (first record))))
         [] data))

#_(on-states [[1 true] [2 nil] [4 nil] [6 false] [7 nil] [8 true] [9 nil] [10 false] [11 nil]]
             second)
;; [[1 6] [8 10]]



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
         ;; y lines
         (for [co2 (range 400 1500 100)]
           ^{:key co2}
           [:line {:title co2
                   :stroke "#00000033"
                   :x1 (->x 0)
                   :x2 (->x (t/seconds (t/between start-date end-date)))
                   :y1 (->y co2)
                   :y2 (->y co2)}])
         ;; color bands
         #_[band 0 420 "#00FF0044"]
         #_[band 1000 max-co2 "#FF000044"]
         #_[band 420 550 "#0000FF44"]
         ;; date lines
         (for [date (date-range (t/at (t/date start-date) (t/midnight))
                                (t/>> (t/now) (t/new-duration 5 :days))
                                (t/new-duration 1 :days))]
              ^{:key date}
              [:line {:stroke "black"
                      :x1 (->x (t/seconds (t/between start-date date)))
                      :x2 (->x (t/seconds (t/between start-date date)))
                      :y1 (->y 0)
                      :y2 (->y max-co2)}])
         ;; oven fan lines
         (for [[start end] (on-states @event-data (fn [event] (get event 2)))]
          ^{:key [start end]}
          [:line
           {:stroke "#ff000088"
            :stroke-width 1000
            :x1 (->x (t/seconds (t/between start-date start)))
            :x2 (->x (t/seconds (t/between start-date end)))
            :y1 (->y 500)
            :y2 (->y 500)}])
         (for [[start end] (on-states @event-data (fn [event] (get event 3)))]
          ^{:key [start end]}
          [:line
           {:stroke "#00ff0033"
            :stroke-width 1000
            :x1 (->x (t/seconds (t/between start-date start)))
            :x2 (->x (t/seconds (t/between start-date end)))
            :y1 (->y 500)
            :y2 (->y 500)}])
         ;; events
         #_(for [[date label] @event-data]
            ^{:key date}
            [:line {:title label
                    :stroke "black"
                    :x1 (->x (t/seconds (t/between start-date date)))
                    :x2 (->x (t/seconds (t/between start-date date)))
                    :y1 (->y 0)
                    :y2 (->y max-co2)}])
         ;; co2 line
         [:path {:stroke "black"
                 :fill "none"
                 :d (->> @co2-data
                         (map (fn [[date co2]]
                                (str (->x (t/seconds (t/between start-date date)))
                                     ","
                                     (->y co2))))
                         (string/join " L ")
                         (str "M "))}]]]))])
