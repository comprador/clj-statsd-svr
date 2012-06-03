(ns clj-statsd-svr
  "statsd protocol server"
  (:use [clojure.string :only [replace] :rename {replace re-replace}]) 
  (:import [java.net DatagramPacket DatagramSocket]))

(def port 8125)
(def statistics (agent { :counters {} :timers {} :gauges {} }))

(defn update-stat [stats stat bucket f]
  (assoc stats stat (assoc (stats stat) bucket (f ((stats stat) bucket)))))

(defn update-stat-val [stats stat bucket value]
  (let [f ({:counters #(+ value (or % 0))
            :timers #()
            :gauges #(value)} stat)]
    (update-stat stats stat bucket f)))

(defn inc-counter [stats bucket delta]
  (update-stat stats :counters bucket #(+ delta (or % 0))))

(defn receive [socket]
  (let [size 1024
        packet (DatagramPacket. (byte-array size) size)]
    (.receive socket packet)
    (String. (.getData packet) 0 (.getLength packet) "UTF-8")))

(defn start-receiver [port-no queue]
  (let [socket (DatagramSocket. port-no)]
    (.start (Thread. #(while true
                        (dorun (map (fn [data] (.put queue data))
                                    (.split #"\n" (receive socket)))))))
    socket))

(defn decode [data]
  (if-let [[_ bucket value type sample-rate] (re-matches #"(.+):(\d+)\|(c|ms|g)(?:(?<=c)\|@(\d+(?:\.\d+)?))?" data)]
    (let [nicebucket (re-replace (re-replace (re-replace bucket #"\s+" "_") #"/" "-") #"[^a-zA-Z_\-0-9\.]" "")
          nicevalue (/ (Double/parseDouble value) (Double/parseDouble (or sample-rate "1")))
          nicetype ({"c" :counters "ms" :timers "g" :gauges} type)]
      {:bucket nicebucket :value nicevalue :type nicetype })
    (println "bad data:" data)))

(defn handle [record]
  (println (record :value))
  (send-off statistics update-stat-val (record :type) (record :bucket) (record :value)))

(defn new-worker [queue]
  #(while true (when-let [record (decode (.take queue))] (handle record))))

(defn start []
  (let [worker-count 2
        work-queue (java.util.concurrent.LinkedBlockingQueue.)
        executor (java.util.concurrent.Executors/newFixedThreadPool worker-count)]
    (start-receiver port work-queue)
    (doall (for [_ (range worker-count)] (.submit executor (new-worker work-queue))))))