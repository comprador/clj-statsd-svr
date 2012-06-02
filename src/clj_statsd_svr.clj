(ns clj-statsd-svr
  "statsd protocol server"
  (:import [java.net DatagramPacket DatagramSocket InetAddress]))

(def work (java.util.concurrent.LinkedBlockingQueue.))

(def port 8125)
(def socket (DatagramSocket. port))

(defn receive
  (let [size 1024
        data (byte-array size)
        packet (DatagramPacket. data size)]
  (.receive socket packet)
  (.put work (String. (.getData packet) 0 (.getLength packet) "UTF-8"))))