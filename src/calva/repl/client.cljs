(ns calva.repl.client
  (:require
   [clojure.string :as string]
   ["bencoder" :as bencoder]
   ["buffer" :as buf]
   [talky.socket-client :as talky]
   [calva.js-utils :refer [cljify jsify]]))

(def ^:private CONTINUATION_ERROR_MESSAGE
  "Unexpected continuation: \"")


(defn- decode [buffers]
  (mapcat
   (fn [^js buffer]
     (try
       (-> (bencoder/decode buffer)
           (cljify)
           (vector))
       (catch js/Error e
         (let [exception-message (.-message e)]
           (if (string/includes? exception-message CONTINUATION_ERROR_MESSAGE)
             (let [recoverable-content (subs exception-message (count CONTINUATION_ERROR_MESSAGE))
                   recoverable-buffer  (.slice buffer 0 (- (.-length buffer) (count recoverable-content)))]
               (decode [recoverable-buffer (buf/Buffer.from recoverable-content)]))
             (js/console.error "FAILED TO DECODE" exception-message))))))
   buffers))


(defn- make-nrepl-client
  [{:keys [host port on-connect]}]
  (let [*results (atom {})

        config
        {:socket/encoding "utf8"

         :socket/decoder
         (fn [chunk]
           (let [buffer (buf/Buffer.from chunk)]
             (when (= 0 (.-length buffer))
               (js/console.warn "EMPTY BUFFER"))
             (not-empty (decode [buffer]))))
           ;;(bencode/decode chunk))

         :socket/encoder
         (fn [data]
           (bencoder/encode (jsify data)))}

        on-close
        (fn [_ error?]
          (js/console.log "Disconnected."))

        on-data
        (fn [_ decoded-messages]
          (mapv (fn [decoded]
                  (when-let [d-id (:id decoded)]
                    (when-let [cb (get-in @*results [d-id :callback])]
                      (let [results (get-in (swap! *results update-in [d-id :results]
                                                   (fnil conj []) decoded) [d-id :results])]
                        (when (some #{"done"} (:status decoded))
                          (swap! *results dissoc d-id)
                          (cb (jsify results)))))))
                decoded-messages)
          (println (pr-str "*results pending" @*results)))

        {:socket.api/keys [write! connected? end!]}
        (talky/make-socket-client
         #:socket {:host host
                   :port (js/parseInt port)
                   :config config
                   :on-connect on-connect
                   :on-close on-close
                   :on-data on-data})]
    {:nrepl.api/send!
     (fn [message callback]
       (when connected?
         (let [id (str (random-uuid))]
           (swap! *results assoc id {:id id
                                     :callback callback
                                     :message :message
                                     :results []})
           (write! (assoc message :id id))
           id)))
     :nrepl.api/end! end!
     :nrepl.api/connected? connected?}))


(defn ^:export create
  [^js options]
  (let [client (make-nrepl-client (cljify options))]
    (jsify {:send (:nrepl.api/send! client)
            :end (:nrepl.api/end! client)
            :connected (:nrepl.api/connected? client)})))