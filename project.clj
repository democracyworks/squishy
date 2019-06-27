(defproject democracyworks/squishy "3.1.0-SNAPSHOT"
  :description "A library for consuming Amazon SQS queue messages"
  :url "https://github.com/democracyworks/squishy"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.amazonaws/aws-java-sdk-sqs "1.11.582"]
                 [org.clojure/tools.logging "0.5.0-alpha.1"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 [com.cemerick/bandalore "0.0.6"
                  :exclusions [com.amazonaws/aws-java-sdk]]]
  :jar-exclusions [#"logback\.xml"]
  :deploy-repositories {"releases" :clojars}
  :profiles {:test {:jvm-opts ["-Dlog-level=OFF"]}})
