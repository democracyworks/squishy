(defproject democracyworks/squishy "1.0.1-SNAPSHOT"
  :description "A library for consuming Amazon SQS queue messages"
  :url "https://github.com/democracyworks/squishy"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [com.amazonaws/aws-java-sdk "1.7.8.1"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-simple "1.7.12"]
                 [com.cemerick/bandalore "0.0.6"]])
