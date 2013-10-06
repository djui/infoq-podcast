(ns infoq-podcast.main
  (:gen-class)
  (:require [infoq-podcast.cache   :as cache]
            [infoq-podcast.catcher :as catcher]
            [infoq-podcast.server  :as server]))


;;; Main

(defn -main []
  (cache/init)
  (server/-main)
  (catcher/-main))
