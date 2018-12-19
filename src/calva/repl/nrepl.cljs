(ns calva.repl.nrepl
  (:require
   ["net" :as net]
   ["bencoder" :as bencoder]
   [clojure-party-repl.bencode :as bencode]
   ["buffer" :as buf]
   [clojure.string :as str]
   [calva.js-utils :refer [cljify jsify]]))


(def CONTINUATION_ERROR_MESSAGE
  "Unexpected continuation: \"")


(defn connect
  "Connects to a socket-based REPL at the given host (defaults to localhost) and port."
  [{:keys [host port on-connect on-error on-end] :or {host "localhost"}}]
  (doto (net/createConnection #js {:host host :port port})
    (.once "connect" (fn []
                       #_(js/console.log (str "Connected to " host ":" port))
                       (when on-connect
                         (on-connect))))
    (.once "end" (fn []
                   #_(js/console.log (str "Disconnected from " host ":" port))
                   (when on-end
                     (on-end))))
    (.once "error" (fn [error]
                     (js/console.log (str "Failed to connect to " host ":" port) error)
                     (when on-error
                       (on-error error))))))


(defn- decode [buffers]
  (mapcat
   (fn [^js buffer]
     (try
       (-> (bencoder/decode buffer)
           (js->clj :keywordize-keys true)
           (vector))
       (catch js/Error e
         (let [exception-message (.-message e)]
           (if (str/includes? exception-message CONTINUATION_ERROR_MESSAGE)
             ;; we might be able to handle this specific error
             ;; of unexpected continuation,
             ;; so we substring the error message to remove
             ;; the continuation error and then
             ;; we are ready to try to decode again
             ;; but this time passing two buffers
             (let [recoverable-content (subs exception-message (count CONTINUATION_ERROR_MESSAGE))
                   recoverable-buffer  (.slice buffer 0 (- (.-length buffer) (count recoverable-content)))]
               (decode [recoverable-buffer (buf/Buffer.from recoverable-content)]))

             ;; can't (don't know) handle other errors
             (js/console.error "FAILED TO DECODE" exception-message))))))
   buffers))

(def *results (atom {}))

(defn message [^js conn msg callback]
  (let [id (str (random-uuid))]
    (swap! *results assoc id {:callback callback
                              :msg msg
                              :results []})
    (.on conn "data" (fn [chunk]
                       (when-let [decoded-messages (bencode/decode chunk)]
                         (dorun (map (fn [decoded]
                                       (let [d-id (:id decoded)
                                             cb (get-in @*results [d-id :callback])
                                             results (get-in @*results [d-id :results])]
                                         (swap! *results assoc-in [d-id :results] (conj results decoded))
                                         #_(when (some #{"done"} (:status decoded)))
                                         (let [results (get-in @*results [d-id :results])]
                                           (println (pr-str "*results cb" @*results))
                                           (swap! *results dissoc d-id)
                                           (cb (jsify results)))))
                                     decoded-messages))
                         (println (pr-str "*results pending" @*results)))))
    (.write conn (bencode/encode (jsify (assoc msg :id id))))))
