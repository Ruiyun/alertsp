(ns alertsp.client
  (:require [alertsp.core :refer :all]
            [aleph.netty :as netty])
  (:import [org.jboss.netty.handler.codec.rtsp RtspResponseDecoder RtspRequestEncoder]
           [org.jboss.netty.handler.codec.oneone OneToOneEncoder OneToOneDecoder]))

(defn rtsp-client [{:keys [client-name] :as options}]
  (let [client-name (or client-name "alertsp-client")]
    (netty/create-client
     client-name
     (fn [channel-group]
      (netty/create-netty-pipeline client-name nil channel-group
        "netty-response-decoder" (RtspResponseDecoder.)
        "netty-response->rtsp-map" (proxy [OneToOneDecoder] []
                                     (decode [ctx channel msg]
                                       (netty-response->rtsp-map msg)))
        "netty-request-encoder" (RtspRequestEncoder.)
        "rtsp-map->netty-request" (proxy [OneToOneEncoder] []
                                    (encode [ctx channel msg]
                                      (rtsp-map->netty-request msg)))))
    options)))
