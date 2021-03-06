(ns ^{:doc "iTunes extension: http://www.itunes.com/dtds/podcast-1.0.dtd.
            Specification: http://www.apple.com/itunes/podcasts/specs.html"}
  qcast.feed.ext.itunes
  (:require [clojure.string :as string]
            [hiccup.util    :refer [escape-html]]))


;;; Utilities

(defn- bool-str [b]
  (if b "yes" "no"))

(defn- secs->time [n]
  (let [s (-> n               (mod 60) int)
        m (-> n        (/ 60) (mod 60) int)
        h (-> n (/ 60) (/ 60)          int)]
    (format "%02d:%02d:%02d" h m s)))


;;; Interface

;; Channel

(defn owner [name email]
  [:itunes:owner (seq [[:itunes:name (escape-html name)]
                       [:itunes:email email]])])

(defn categories [& cats]
  (map #(vector :itunes:category {:text (escape-html %)}) cats))

(defn keywords [& keywords]
  [:itunes:keywords (escape-html (string/join ", " keywords))])

(defn block [b]
  [:itunes:block (bool-str b)])

(defn explicit [b]
  [:itunes:explicit (bool-str b)])

;; Channel or Item

(defn author [s]
  [:itunes:author (escape-html s)])

(defn summary [s]
  [:itunes:summary (escape-html s)])

(defn subtitle [s]
  [:itunes:subtitle (escape-html s)])

(defn image [url]
  [:itunes:image {:href url}])

;; Item

(defn duration [len]
  [:itunes:duration (secs->time len)])
