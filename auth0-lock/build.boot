(set-env!
  :resource-paths #{"resources"}
  :dependencies '[[cljsjs/boot-cljsjs "0.9.0" :scope "test"]])

(require '[boot.core :as boot]
         '[boot.tmpdir :as tmpd]
         '[boot.util :refer [sh]]
         '[clojure.java.io :as io]
         '[cljsjs.boot-cljsjs.packaging :refer :all])

(def +lib-version+ "10.24.1")
(def +version+ (str +lib-version+ "-1"))
(def +lib-folder+ (format "lock-%s" +lib-version+))

(task-options!
 pom { :project     'cljsjs/auth0-lock
       :version     +version+
       :description "Auth0 Lock"
       :url         "https://auth0.com/docs/libraries/lock"
       :scm         { :url "https://github.com/auth0/lock" }
       :license     { "MIT" "https://github.com/auth0/lock/blob/master/LICENSE" }})

(deftask build []
  (let [tmp (boot/tmp-dir!)]
    (with-pre-wrap
      fileset
      (doseq [f (boot/input-files fileset)]
        (let [target (io/file tmp (tmpd/path f))]
          (io/make-parents target)
          (io/copy (tmpd/file f) target)))
      (io/copy
       (io/file tmp "build/webpack.config.js")
       (io/file tmp +lib-folder+ "webpack-cljsjs.config.js"))
      (binding [*sh-dir* (str (io/file tmp +lib-folder+))]
        ((sh "npm" "install" "--ignore-scripts"))
        ((sh "npm" "install" "webpack"))
        ((sh "npm" "run" "build"))
        ((sh "./node_modules/.bin/webpack" "--config" "webpack-cljsjs.config.js")))
      (-> fileset (boot/add-resource tmp) boot/commit!))))

(deftask package []
  (comp
    (download :url (format "https://github.com/auth0/lock/archive/v%s.zip" +lib-version+)
              :unzip true)
    (build)
    (sift :move {#"^lock.*/build/lock\.js$" "cljsjs/auth0-lock/development/lock.inc.js"})
    (minify :in "cljsjs/auth0-lock/development/lock.inc.js"
            :out "cljsjs/auth0-lock/production/lock.min.inc.js")
    (sift :include #{#"^cljsjs"})
    (deps-cljs :name "cljsjs.auth0-lock" :requires ["cljsjs.react" "cljsjs.react.dom"])
    (pom)
    (jar)
    (validate-checksums)))
