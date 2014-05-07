(defproject democracyworks.squishy "1.0.0-SNAPSHOT"
  :description "A library for consuming Amazon SQS queue messages"
  :url "http://github.com/turbovote/squishy"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.amazonaws/aws-java-sdk "1.7.8"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.slf4j/slf4j-simple "1.7.6"]
                 [turbovote.resource-config "0.1.1"]
                 [com.cemerick/bandalore "0.0.5"]
                 [riemann-clojure-client "0.2.9"]])
