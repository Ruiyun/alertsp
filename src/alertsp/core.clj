(ns alertsp.core
  (:require [clojure.string :refer [lower-case upper-case]])
  (:import [java.nio.charset StandardCharsets]
           [org.jboss.netty.buffer ChannelBuffers ChannelBuffer]
           [org.jboss.netty.handler.codec.http HttpMessage HttpRequest HttpResponse
            DefaultHttpRequest DefaultHttpResponse HttpResponseStatus]
           [org.jboss.netty.handler.codec.rtsp RtspVersions RtspMethods RtspHeaders$Names]))

(defn- get-headers [^HttpMessage message]
  (reduce
   (fn [headers, ^String name]
     (assoc headers
       (lower-case name)
       (->> (.getHeaders message name)
            (seq)
            (clojure.string/join ","))))
   {}
   (seq (.getHeaderNames message))))

(defn- get-body [^HttpMessage message]
  (let [buf (.getContent message)]
    (when (pos? (.capacity buf))
      (.toString buf StandardCharsets/UTF_8))))

(defn- set-headers! [^HttpMessage message, ^Long cseq, headers]
  {:pre [(pos? cseq)]}
  (-> message (.setHeader message RtspHeaders$Names/CSEQ cseq))
  (doseq [[key val-or-vals] (dissoc headers RtspHeaders$Names/CSEQ RtspHeaders$Names/CONTENT_LENGTH)]
    (if (string? val-or-vals)
      (.setHeader message key val-or-vals)
      (doseq [val val-or-vals]
        (.addHeader message key val))))
  message)

(defn- set-body! [^HttpMessage message, body]
  (cond
   (string? body) (let [^ChannelBuffer buffer (ChannelBuffers/copiedBuffer body StandardCharsets/UTF_8)]
                    (.setContent message buffer)
                    (.setHeader message RtspHeaders$Names/CONTENT_LENGTH (.readableBytes buffer))))
  message)

(defn- get-cseq [^HttpMessage message]
  (-> message (.getHeader RtspHeaders$Names/CSEQ) (Long/parseLong)))

(defn rtsp-map->netty-request [{:keys [method uri cseq headers body]}]
  (-> (DefaultHttpRequest. RtspVersions/RTSP_1_0 (-> method name upper-case (RtspMethods/valueOf)) uri)
      (set-headers! cseq headers)
      (set-body! body)))

(defn netty-request->rtsp-map [^HttpRequest request]
  {:method (-> request (.getMethod) (.getName) lower-case keyword)
   :uri (.getUri request)
   :cseq (get-cseq request)
   :headers (get-headers request)
   :body (get-body request)})

(defn rtsp-map->netty-response [{:keys [status cseq headers body]}]
  (-> (DefaultHttpResponse. RtspVersions/RTSP_1_0 (HttpResponseStatus/valueOf status))
      (set-headers! cseq headers)
      (set-body! body)))

(defn netty-response->rtsp-map [^HttpResponse response]
  {:status (.. response getStatus getCode)
   :cseq (get-cseq response)
   :headers (get-headers response)
   :body (get-body response)})
