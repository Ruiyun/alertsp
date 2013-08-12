(defproject alertsp "0.3.0-rc1"
  :description "A rtsp client & server library based aleph."
  :url "https://github.com/Ruiyun/alertsp"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[io.netty/netty "3.6.6.Final"]
                 [org.clojure/tools.logging "0.2.6"]
                 [aleph "0.3.0-rc2"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.5.1"]]}}
  :global-vars {*warn-on-reflection* true})
