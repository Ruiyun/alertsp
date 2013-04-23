(ns alertsp.api-test
  (:require [clojure.test :refer :all]
            [alertsp.server :refer :all]
            [alertsp.client :refer :all]
            [lamina.core :as lamina])
  (:import [java.net ServerSocket]
           [java.io IOException]))

(deftest bind-close-test
  (testing "binding test"
    (let [close-server (rtsp-server nil {:name "test-rtsp-server", :port 2554})]
      (is (thrown? IOException (ServerSocket. 2554)))
      (close-server)
      (let [s (ServerSocket. 2554)]
        (.close s)))))

(deftest typical-message-flow-test
  (let [default-headers {"server" "alertsp-test-server"
                         "content-length" "0"}
        sdp (str "v=0\r\n"
                 "o=- 15354532072267698938 15354532072267698938 IN IP4 Kay-PC\r\n"
                 "s=Unnamed\r\n"
                 "i=N/A\r\n"
                 "c=IN IP4 0.0.0.0\r\n"
                 "t=0 0\r\n"
                 "a=tool:vlc 2.0.6\r\n"
                 "a=recvonly\r\n"
                 "a=type:broadcast\r\n"
                 "a=charset:UTF-8\r\n"
                 "a=control:rtsp://localhost:2554/\r\n"
                 "m=video 0 RTP/AVP 96\r\n"
                 "b=RR:0\r\n"
                 "a=rtpmap:96 H264/90000\r\n"
                 "a=fmtp:96 packetization-mode=1;profile-level-id=42c01e;sprop-parameter-sets=Z0LAHtlAtBJoQAAAAwBAAAAMo8WLlg==,aMkjyA==;\r\n"
                 "a=control:rtsp://localhost:2554/trackID=10\r\n")
        options-response {:status 200
                          :headers (assoc default-headers "public" "DESCRIBE,SETUP,TEARDOWN,PLAY,PAUSE,GET_PARAMETER")}
        describe-response {:status 200
                           :headers (assoc default-headers
                                      "date" "Mon, 15 Apr 2013 09:06:25 GMT"
                                      "content-base" "rtsp://localhost:2554/"
                                      "cache-control" "no-cache")
                           :content-type "application/sdp"
                           :body sdp}
        setup-response {:status 200
                        :headers (assoc default-headers
                                   "date" "Mon, 15 Apr 2013 09:06:25 GMT"
                                   "transport" "RTP/AVP/UDP;unicast;client_port=50814-50815;server_port=50816-50817;ssrc=2BB89D64;mode=play"
                                   "session" "3bda72fe72d73e60;timeout=60"
                                   "cache-control" "no-cache")}
        play-response {:status 200
                       :headers (assoc default-headers
                                  "date" "Mon, 15 Apr 2013 09:06:25 GMT"
                                  "rtp-info" "url=rtsp://localhost:2554/trackID=10;seq=10893;rtptime=1877939411"
                                  "range" "npt=5561.972219-"
                                  "session" "3bda72fe72d73e60;timeout=60"
                                  "cache-control" "no-cache")}
        teardown-response {:status 200
                           :headers (assoc default-headers
                                      "date" "Mon, 15 Apr 2013 09:15:20 GMT"
                                      "session" "3bda72fe72d73e60;timeout=60"
                                      "cache-control" "no-cache")}]
    (letfn [(handler [request]
              (case (:method request)
                :options options-response
                :describe describe-response
                :setup setup-response
                :play play-response
                :teardown teardown-response
                (throw (Exception. "Unknown request method."))))]
      (testing "basic flow"
        (let [close-server (rtsp-server handler {:name "rtsp-test-server", :port 2554})
              client-channel (lamina/wait-for-result (rtsp-client {:client-name "rtsp-test-client", :host "localhost" :port 2554})
                                                     2000)]
          (lamina/enqueue client-channel {:method :options
                                          :uri "rtsp://localhost:2554/"
                                          :cseq 1
                                          :headers {"user-agent" "rtsp-test-client"}})
          (let [{:keys [status cseq headers content-type body]} (lamina/wait-for-message client-channel 2000)
                {cseq-header "cseq", server-header "server"} headers]
            (are [x y] (= x y)
                 status 200
                 cseq 1
                 cseq-header "1"
                 server-header "alertsp-test-server"
                 content-type nil
                 body nil))

          (lamina/enqueue client-channel {:method :describe
                                          :uri "rtsp://localhost:2554/"
                                          :cseq 2
                                          :headers {"accept" "application/sdp"}})
          (let [{:keys [status cseq headers content-type body]} (lamina/wait-for-message client-channel 2000)
                {cseq-header "cseq", date-header "date", content-base-header "content-base", cache-control-header "cache-control"} headers]
            (are [x y] (= x y)
                 status 200
                 cseq 2
                 cseq-header "2"
                 date-header "Mon, 15 Apr 2013 09:06:25 GMT"
                 content-base-header "rtsp://localhost:2554/"
                 cache-control-header "no-cache"
                 content-type "application/sdp"
                 body sdp))

          (lamina/enqueue client-channel {:method :setup
                                          :uri "rtsp://localhost:2554/"
                                          :cseq 3
                                          :headers {"transport" "RTP/AVP;unicast;client_port=50814-50815"}})
          (let [{:keys [status cseq headers content-type body]} (lamina/wait-for-message client-channel 2000)
                {transport-header "transport", session-header "session"} headers]
            (are [x y] (= x y)
                 status 200
                 cseq 3
                 transport-header "RTP/AVP/UDP;unicast;client_port=50814-50815;server_port=50816-50817;ssrc=2BB89D64;mode=play"
                 session-header "3bda72fe72d73e60;timeout=60"
                 content-type nil
                 body nil))

          (lamina/enqueue client-channel {:method :play
                                          :uri "rtsp://localhost:2554/"
                                          :cseq 4
                                          :headers {"session" "3bda72fe72d73e60"
                                                    "range" "npt=0.000-"}})
          (let [{status :status, cseq :cseq
                 {rtp-info-header "rtp-info"
                  range-header "range"
                  session-header "session"} :headers} (lamina/wait-for-message client-channel 2000)]
            (are [x y] (= x y)
                 status 200
                 cseq 4
                 rtp-info-header "url=rtsp://localhost:2554/trackID=10;seq=10893;rtptime=1877939411"
                 range-header "npt=5561.972219-"
                 session-header "3bda72fe72d73e60;timeout=60"))

          (lamina/enqueue client-channel {:method :teardown
                                          :uri "rtsp://localhost:2554/"
                                          :cseq 5
                                          :headers {"session" "3bda72fe72d73e60"}})
          (let [{status :status, cseq :cseq
                 {session-header "session"} :headers} (lamina/wait-for-message client-channel 2000)]
            (are [x y] (= x y)
                 status 200
                 cseq 5
                 session-header "3bda72fe72d73e60;timeout=60"))

          (lamina/close client-channel)
          (close-server))))))
