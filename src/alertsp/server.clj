(ns alertsp.server
  (:require [alertsp.core :refer :all]
            [aleph.netty :as netty])
  (:import [java.net InetSocketAddress]
           [org.jboss.netty.channel SimpleChannelUpstreamHandler MessageEvent ExceptionEvent]
           [org.jboss.netty.handler.codec.oneone OneToOneEncoder OneToOneDecoder]
           [org.jboss.netty.handler.codec.rtsp RtspRequestDecoder RtspResponseEncoder]))

(defn- parse-addr [^InetSocketAddress addr]
  {:addr (.. addr getAddress getHostAddress)
   :port (.getPort addr)})

(defn- handler-wrap [handler error-handler]
  (proxy [SimpleChannelUpstreamHandler] []
    (messageReceived [ctx, ^MessageEvent evt]
      (let [req (.getMessage evt)
            ch (.getChannel evt)
            {r-addr :addr r-port :port} (parse-addr (.getRemoteAddress ch))
            {l-addr :addr l-port :port} (parse-addr (.getLocalAddress ch))]
        (when (map? req)
          (when-let [rsp (-> (assoc req
                               :remote-addr r-addr
                               :remote-port r-port
                               :local-addr l-addr
                               :local-port l-port)
                             handler)]
            (when (.isWritable ch)
              (->> (:cseq req)
                   (assoc rsp :cseq)
                   (.write ch)))))))
    (exceptionCaught [ctx, ^ExceptionEvent evt]
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
