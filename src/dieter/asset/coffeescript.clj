(ns dieter.asset.coffeescript
  (:require
   dieter.asset
   dieter.asset.javascript)
  (:use [dieter.rhino :only (call with-scope make-pool)]))

(def pool (make-pool))

(defn compile-coffeescript [input filename]
  (with-scope pool ["coffee-script.js" "coffee-wrapper.js"]
    (str (call "compileCoffeeScript" input filename))))

(defn preprocess-coffeescript [file]
  (compile-coffeescript (slurp file) (.getCanonicalPath file)))

(defrecord Coffee [file]
  dieter.asset.Asset
  (read-asset [this options]
    (dieter.asset.javascript.Js. (:file this) (preprocess-coffeescript (:file this)))))
