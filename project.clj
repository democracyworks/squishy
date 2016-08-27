(defproject democracyworks/squishy "3.0.1"
  :description "A library for consuming Amazon SQS queue messages"
  :url "https://github.com/democracyworks/squishy"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.amazonaws/aws-java-sdk-sqs "1.11.23"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.7"]
                 [com.cemerick/bandalore "0.0.6"
                  :exclusions [com.amazonaws/aws-java-sdk]]]
  :jar-exclusions [#"logback\.xml"]
  :deploy-repositories {"releases" :clojars}
  :profiles {:test {:jvm-opts ["-Dlog-level=OFF"]}})
