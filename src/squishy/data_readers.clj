(ns squishy.data-readers
  (:require [clojure.string :as str])
  (:import [com.amazonaws.regions Region Regions]))

(defn aws-region
  "Attempts to coerce the string into a Java Region instance. Can accept
   either of the two string versions of a region name, lowercase with dashes
   (e.g. `us-east-1`) or uppercase with underscores (e.g. `US_WEST_1`)."
  [region-name]
  (if (str/includes? region-name "_")
    (-> region-name
        Regions/valueOf
        Region/getRegion)
    (-> region-name
        Regions/fromName
        Region/getRegion)))
