(ns alertsp.core-test
  (:use clojure.test
        alertsp.core)
  (:import [java.nio.charset StandardCharsets]
           [org.jboss.netty.handler.codec.http DefaultHttpRequest DefaultHttpResponse]
           [org.jboss.netty.handler.codec.rtsp RtspVersions RtspMethods RtspHeaders$Names]
           [org.jboss.netty.buffer ChannelBuffers]))

(deftest netty-request->rtsp-map-test
  (testing "basic test without content"
    (is (= (netty-request->rtsp-map (doto (DefaultHttpRequest. RtspVersions/RTSP_1_0 RtspMethods/OPTIONS "rtsp://localhost/")
                                      (.setHeader RtspHeaders$Names/CSEQ 1)
                                      (.setHeader RtspHeaders$Names/USER_AGENT "alertsp-test")))
           {:method :options
            :uri "rtsp://localhost/"
            :cseq 1
            :headers {"cseq" "1", "user-agent" "alertsp-test"}
            :body nil})))
  (testing "basic test with content"
    (is (= (netty-request->rtsp-map (doto (DefaultHttpRequest. RtspVersions/RTSP_1_0 RtspMethods/DESCRIBE "rtsp://localhost/")
                                      (.setHeader RtspHeaders$Names/CSEQ 2)
                                      (.setHeader RtspHeaders$Names/CONTENT_TYPE "text/plain")
                                      (.setContent (ChannelBuffers/copiedBuffer "Hello World!" StandardCharsets/UTF_8))))
           {:method :describe
            :uri "rtsp://localhost/"
            :cseq 2
            :headers {"cseq" "2", "content-type" "text/plain"}
            :body "Hello World!"}))))
