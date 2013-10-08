(ns infoq-podcast.catcher
  (:gen-class)
  (:require [clj-time.coerce     :as time-coerce]
            [clj-time.format     :as time]
            [clojure.string      :as string]
            [infoq-podcast.cache :as cache]
            [infoq-podcast.html  :as html :refer :all]
            [infoq-podcast.util  :as util :refer :all]
            [taoensso.timbre     :as timbre :refer :all])
  (:refer-clojure :exclude [meta]))


;;; Internals

(defn- base-url [& s]
  (apply str "http://www.infoq.com" s))


;; Scraping Internals

(defn- poster [dom]
  (meta :property "og:image" base-url dom))

(defn- keywords [dom]
  (letfn [(split-keywords [s]
            (let [[lowercase-kw & uppercase-kw] (string/split s #",")
                  lowercase-kw (string/split lowercase-kw #" ")]
              (concat lowercase-kw uppercase-kw)))]
    (meta :name "keywords" split-keywords dom)))

(defn- summary [dom]
  (meta "description" dom))

(defn- title [dom]
  (select [:head :title] inner-text dom))

(defn- authors [dom]
  (let [transformer #(some-> % inner-text (string/split #"\s*(and|,)\s*"))]
    (select [:.author_general :> :a] transformer dom)))

(defn- length [dom]
  (let [transformer #(some-> % inner-text util/interval->sec)]
    (select [:.videolength2] transformer dom)))

(defn- video [dom]
  (let [transformer #(some->> % (attr :src) content-header)]
    (select [:#video :> :source] transformer dom)))

(defn- record-date [dom]
  (let [transformer #(some->> % (attr :src)
                              (re-find #"/([0-9]{2}-[a-z]{3})-.*$") second
                              (time/parse (time/formatter "yy-MMM"))
                              time-coerce/to-date)]
    (select [:#video :> :source] transformer dom)))

(defn- publish-date [dom]
  (let [transformer #(some->> % (inner-text 2)
                              (re-find #"(?s)on\s*(.*)") second
                              (time/parse (time/formatter "MMM dd, yyyy"))
                              time-coerce/to-date)]
    (select [:.author_general] transformer dom)))

(defn- slides [dom]
  (let [transformer #(some->> % inner-text (re-find #"var slides.*"))
        filter #(some->> % (some identity) (re-seq #"'(.+?)'")
                         (map (comp base-url second)))]
    (select-all [:script] transformer filter dom)))

(defn- times [dom]
  (let [transformer #(some->> % inner-text (re-find #"TIMES.*"))
        filter #(some->> % (some identity) (re-seq #"(\d+?),")
                         (map (comp parse-int second)))]
    (select-all [:script] transformer filter dom)))

(defn- overview-ids [dom]
  (select-all [:.news_type_video :> :a] #(attr :href %) dom))


;; Scraping API

(defn- metadata [id]
  (let [md-keys [:id :link :poster :keywords :summary :title :authors
                 :record-date :publish-date :length :video :slides :times]
        md-vals (juxt (constantly id) (constantly (base-url id)) poster
                      keywords summary title authors record-date publish-date
                      length video slides times)]
    (debug "Fetching presentation" id)
    (->> (base-url id)
         dom
         md-vals
         (zipmap md-keys))))

(defn- latest
  ([] (latest 0))
  ([marker]
     (debug "Fetching overview from index" marker)
     (let [dom (dom (base-url "/presentations/" marker))
           items (overview-ids dom)]
       (lazy-cat items (latest (+ marker (count items)))))))


(defn- cache-updates
  "Scrape the overview sites and collect its oughly 12 items per site until
  finding an seen item (since). Scrape a maximum of limit or 100 items. This
  sequence requires additional two requests (page+video) per item, thus n%12 +
  2*n."
  ([] (cache-updates (cache/latest)))
  ([since] (cache-updates since 100))
  ([since limit]
     (let [since-id (or (:id since) :inf)]
       (info "Check for updates since" since-id)
       (->> (latest)
            (take-while #(not= % since-id))
            (pmap metadata)
            (map cache/put)
            (dorun (dec limit))))))


;;; Main

(defn -main [& args]
  (info "Starting catcher")
  (if (= (first args) "once")
    (do (info "Running once")
        (cache-updates))
    (do (info "Running periodically")
        (logged-future (interspaced (minutes) cache-updates)))))
