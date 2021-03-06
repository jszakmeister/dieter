(ns dieter.core
  (:require [clojure.java.io :as io])
  (:use
   dieter.settings
   dieter.asset
   [dieter.path :only [find-file cached-file-path make-relative-to-cache
                       uncachify-filename cache-busting-path write-file]]
   [ring.middleware.file      :only [wrap-file]]
   [ring.middleware.file-info :only [wrap-file-info]]
   [dieter.asset.javascript   :only [map->Js]]
   [dieter.asset.css          :only [map->Css]]
   [dieter.asset.static       :only [map->Static]]
   [dieter.asset.less         :only [map->Less]]
   [dieter.asset.coffeescript :only [map->Coffee]]
   [dieter.asset.hamlcoffee   :only [map->HamlCoffee]]
   [dieter.asset.manifest     :only [map->Dieter]]))

(register :default map->Static)
(register "coffee" map->Coffee)
(register "cs"     map->Coffee)
(register "css"    map->Css)
(register "dieter" map->Dieter)
(register "hamlc"  map->HamlCoffee)
(register "js"     map->Js)
(register "less"   map->Less)

(defn write-to-cache [content requested-path]
  (let [dest (io/file (cached-file-path requested-path content))]
    (io/make-parents dest)
    (write-file content dest)
    dest))

(def known-mime-types
  {:hbs "text/javascript"
   "less" "text/css"
   "hamlc" "text/javascript"
   "coffee" "text/javascript"
   "cs" "text/javascript"})

(defn find-and-cache-asset [requested-path]
  (if-let [file (find-file requested-path (asset-root))]
    (-> file
        (make-asset)
        (read-asset *settings*)
        (compress *settings*)
        (write-to-cache requested-path))))

(defn asset-builder [app & [options]]
  (fn [req]
    (binding [*settings* (merge *settings* options)]
      (let [path (uncachify-filename (:uri req))]
        (if (re-matches #"^/assets/.*" path)
          (if-let [cached (find-and-cache-asset (str "." path))]
            (let [new-path (make-relative-to-cache (str cached))]
              (swap! cached-paths assoc path new-path)
              (app (assoc req :uri new-path)))
            (app req))
          (app req))))))

(defn asset-pipeline [app & [options]]
  (binding [*settings* (merge *settings* options)]
    (if (= :production (:cache-mode *settings*))
      (-> app
          (wrap-file (cache-root))
          (asset-builder options)
          (wrap-file (cache-root))
          (wrap-file-info known-mime-types))
      (-> app
          (wrap-file (cache-root))
          (asset-builder options)
          (wrap-file-info known-mime-types)))))

(defn link-to-asset [path & [options]]
  "path should start under assets and not contain a leading slash
ex. (link-to-asset \"javascripts/app.js\") => \"/assets/javascripts/app-12345678901234567890123456789012.js\""
  (binding [*settings* (merge *settings* options)]
    (if-let [file (find-file (str "./assets/" path) (asset-root))]
      (cache-busting-path *settings* (str "/assets/" path)))))
