(ns ^{:doc "RSS 2.0 Feed generator.
            Specification: http://cyber.law.harvard.edu/rss/rss.html"}
  qcast.feed.rss
  (:import [java.text SimpleDateFormat])
  (:require [clojure.string :refer [join]]
            [hiccup.core    :as hiccup]
            [hiccup.page    :refer [xml-declaration]]
            [hiccup.util    :refer [escape-html]]))


;;; Globals

(def ^:private feed-generator
  (str "clj-feed/1.0 Clojure/"
       (join "." (take-while identity (vals *clojure-version*)))))

(def ^:private rss-spec
  {:channel
   #{;; Required (AND)
     :description :link :title
     ;; Optional
     :category :cloud :copyright :docs :generator :image :language :lastBuildDate
     :managingEditor :pubDate :rating :skipDays :skipHours :textInput :ttl
     :webMaster}
   :item
   #{;; Required (OR)
     :description :title
     ;; Optional
     :author :category :comments :enclosure :guid :link :pubDate :source}})

(def ^:private rss-extensions
  {;; Optional
   :atom            [:xmlns:atom "http://www.w3.org/2005/Atom"]
   :itunes          [:xmlns:itunes "http://www.itunes.com/dtds/podcast-1.0.dtd"]
   :feedburner      [:xmlns:feedburner "http://rssnamespace.org/feedburner/ext/1.0"]
   :simple-chapters [:xmlns:psc "http://podlove.org/simple-chapters"]
   :content         [:xmlns:content "http://purl.org/rss/1.0/modules/content/"]
   :history         [:xmlns:fh "http://purl.org/syndication/history/1.0"]})


;;; Utilities

(defn- format-datetime [inst]
  (let [format (SimpleDateFormat. "EEE, dd MMM yyyy HH:mm:ss ZZZZ")]
    (.format format inst)))

(defn- insert? [coll kv]
  (let [contains? #(->> % (map first) set %2)]
    (if (contains? coll (first kv))
      coll
      (conj coll kv))))

;;; Internals

(defn- namespaces [extensions]
  (some->> extensions
           (select-keys rss-extensions)
           vals
           flatten
           (apply hash-map)))

(defn- item [entry]
  (let [{:keys [title description]} (set (map first entry))]
    (assert (or title description)
            "RSS item requires at least :title or :description")
    [:item (seq entry)]))

(defn- channel [info entries]
  (let [{:keys [link title description]} (set (map first info))]
    (assert (and link title description)
            "RSS channel requires at least :link, :title, and :description")
    (let [channel (insert? info [:generator feed-generator])
          items (map item (remove empty? entries))]
      [:channel (concat channel items)])))


;;; Interface

(defn title [s]
  [:title (escape-html s)])

(defn link [s]
  [:link s])

(defn description [s]
  [:description (escape-html s)])

(defn author [s]
  ;; Has to be a single email address
  [:author s])

(defn pub-date [inst]
  [:pubDate (format-datetime inst)])

(defn last-build-date [inst]
  [:lastBuildDate (format-datetime inst)])

(defn guid
  ([s]
     (guid s true))
  ([s perma-link?]
     [:guid {:isPermaLink (str perma-link?)} s]))

(defn image [url title link]
  [:image [:url url] [:title (escape-html title)] [:link link]])

(defn enclosure [url length type]
  ;; http://feedvalidator.org/docs/error/UseZeroForUnknown.html
  ;; http://feedvalidator.org/docs/warning/MissingTypeAttr.html
  [:enclosure {:url url, :length (or length 0), :type (or type "audio/mpeg")}])

(defn categories [& cats]
  (map #(vector :category (escape-html %)) cats))

(defn language [s]
  [:language s])

(defn generator [s]
  [:generator (escape-html s)])

(defn as-xml
  "Convert a RSS feed data structure to XML."
  [tree]
  (hiccup/html (xml-declaration "utf-8") tree))

(defn feed
  "Create a RSS 2.0 feed. Extensions can be:
  Atom, iTunes, FeedBurner, Simple Chapters, Content, History"
  [info entries & [extensions]]
  (let [attrs (merge {:version "2.0"} (namespaces extensions))]
    [:rss attrs (channel info entries)]))
