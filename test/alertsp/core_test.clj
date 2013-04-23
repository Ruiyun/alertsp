(ns alertsp.core-test
  (:use clojure.test
        alertsp.core)
  (:import [java.nio.charset StandardCharsets]
           [org.jboss.netty.handler.codec.http DefaultHttpRequest DefaultHttpResponse]
           [org.jboss.netty.handler.codec.rtsp RtspVersions RtspMethods RtspHeaders$Names RtspResponseStatuses]
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
            :content-type nil
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
            :content-type "text/plain"
            :body "Hello World!"}))))

(deftest netty-response->rtsp-map-test
  (testing "basic test without content"
    (is (= (netty-response->rtsp-map (doto (DefaultHttpResponse. RtspVersions/RTSP_1_0 RtspResponseStatuses/OK)
                                       (.setHeader RtspHeaders$Names/CSEQ 1)
                                       (.setHeader RtspHeaders$Names/SERVER "alertsp-test-server")))
           {:status 200
            :cseq 1
            :headers {"cseq" "1", "server" "alertsp-test-server"}
            :content-type nil
            :body nil})))
  (testing "basic test with content"
    (let [sdp (str "v=0\r\n"
                   "o=- 15354532072267698938 15354532072267698938 IN IP4 Kay-PC\r\n"
                   "c=IN IP4 0.0.0.0\r\n"
                   "t=0 0\r\n")]
      (is (= (netty-response->rtsp-map (doto (DefaultHttpResponse. RtspVersions/RTSP_1_0 RtspResponseStatuses/NOT_FOUND)
                                         (.setHeader RtspHeaders$Names/CSEQ 2)
                                         (.setHeader RtspHeaders$Names/CONTENT_TYPE "application/sdp")
                                         (.setContent (ChannelBuffers/copiedBuffer sdp StandardCharsets/UTF_8))))
             {:status 404
              :cseq 2
              :headers {"cseq" "2", "content-type" "application/sdp"}
              :content-type "application/sdp"
              :body sdp})))))

(deftest rtsp-map->netty-request-test
  (testing "basic test without content"
    (let [result (rtsp-map->netty-request {:method :options
                                           :uri "rtsp://192.168.1.1/something"
                                           :cseq 1
                                           :headers {"host" "192.168.1.2", "user-agent" "alertsp-client"}})]
      (is (= (.. result getMethod getName) "OPTIONS"))
      (is (= (.getUri result) "rtsp://192.168.1.1/something"))
      (is (= (.getHeader result RtspHeaders$Names/CSEQ) "1"))
      (is (= (.getHeader result RtspHeaders$Names/HOST) "192.168.1.2"))
      (is (= (.getHeader result RtspHeaders$Names/USER_AGENT) "alertsp-client"))
      (is (zero? (.getContentLength result)))))
  (testing "basic test with content"
    (let [result (rtsp-map->netty-request {:method :play
                                           :uri "rtsp://192.168.1.1/something"
                                           :cseq 2
                                           :headers {"session" "3bda72fe72d73e60"
                                                     "range" "npt=0.000-"}
                                           :content-type "text/plain"
                                           :body "Hello world!"})]
      (is (= (.. result getMethod getName) "PLAY"))
      (is (= (.getUri result) "rtsp://192.168.1.1/something"))
      (is (= (.getHeader result RtspHeaders$Names/CSEQ) "2"))
      (is (= (.getHeader result RtspHeaders$Names/SESSION) "3bda72fe72d73e60"))
      (is (= (.getHeader result RtspHeaders$Names/RANGE) "npt=0.000-"))
      (is (= (.getHeader result RtspHeaders$Names/CONTENT_TYPE) "text/plain"))
      (is (= (.getContentLength result) 12))
      (is (= (.. result getContent (toString StandardCharsets/UTF_8)) "Hello world!")))))

(deftest rtsp-map->netty-response-test
  (testing "basic test without content"
    (let [result (rtsp-map->netty-response {:status 200
                                            :cseq 1})]
      (is (= (.getStatus result) RtspResponseStatuses/OK))
      (is (= (.getHeader result RtspHeaders$Names/CSEQ) "1"))
      (is (zero? (.getContentLength result)))))
  (testing "basic test with content"
    (let [sdp (str "v=0\r\n"
                   "o=- 15354532072267698938 15354532072267698938 IN IP4 Kay-PC\r\n"
                   "c=IN IP4 0.0.0.0\r\n"
                   "t=0 0\r\n")
          result (rtsp-map->netty-response {:status 403
                                            :cseq 2
                                            :headers {"server" "alertsp-test-server"}
                                            :content-type "application/sdp"
                                            :body sdp})]
      (is (= (.getStatus result) RtspResponseStatuses/FORBIDDEN))
      (is (= (.getHeader result RtspHeaders$Names/CSEQ) "2"))
      (is (= (.getHeader result RtspHeaders$Names/SERVER) "alertsp-test-server"))
      (is (= (.getHeader result RtspHeaders$Names/CONTENT_TYPE) "application/sdp"))
      (is (= (.getContentLength result) (.length sdp)))
      (is (= (.. result getContent (toString StandardCharsets/UTF_8)) sdp)))))
