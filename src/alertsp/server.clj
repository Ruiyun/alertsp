(ns alertsp.server
  (:require [alertsp.core :refer :all]
            [aleph.netty :as netty])
  (:import [org.jboss.netty.channel SimpleChannelUpstreamHandler]
           [org.jboss.netty.handler.codec.oneone OneToOneEncoder OneToOneDecoder]
           [org.jboss.netty.handler.codec.rtsp RtspRequestDecoder RtspResponseEncoder]))

(defn- handler-wrap [handler error-handler]
  (proxy [SimpleChannelUpstreamHandler] []
    (messageReceived [ctx evt]
      (let [msg (.getMessage evt)
            ch (.getChannel evt)]
        (when (map? msg)
          (.write ch (assoc (handler msg) :cseq (:cseq msg))))))
    (exceptionCaught [ctx evt]
      (if error-handler
        (error-handler (.getChannel evt) (.getCause evt))
        (.. evt getChannel close)))))

(defn rtsp-server
  ([handler options] (rtsp-server handler nil options))
  ([handler error-handler {name :name port :port :as options}]
     {:pre [(integer? port)]}
     (let [server-name (or name
                           (-> options :server :name)
                           "rtsp-server")]
       (netty/start-server
        server-name
        (fn [channel-group]
          (netty/create-netty-pipeline
           server-name true channel-group
           :netty-request-decoder (RtspRequestDecoder.)
           :netty-request->rtsp-map (proxy [OneToOneDecoder] []
                                      (decode [ctx channel msg]
                                        (netty-request->rtsp-map msg)))
           :handler (handler-wrap handler error-handler)
           :netty-response-encoder (RtspResponseEncoder.)
           :rtsp-map->netty-response (proxy [OneToOneEncoder] []
                                       (encode [ctx channel msg]
                                         (rtsp-map->netty-response msg)))))
        options))))
