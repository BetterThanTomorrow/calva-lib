(ns calva.repl.client
  (:require
   [clojure.string :as string]
   ["bencoder" :as bencoder]
   ["buffer" :as buf]
   [talky.socket-client :as talky]
   [calva.js-utils :refer [cljify jsify]]))

(def ^:private CONTINUATION_ERROR_MESSAGE
  "Unexpected continuation: \"")

(defn- decode [^js buffer]
  (try
    (-> buffer
        bencoder/decode
        cljify
        vector)
    (catch js/Error e
      (let [exception-message (.-message e)]
        (if (string/includes? exception-message CONTINUATION_ERROR_MESSAGE)
          (let [recoverable-content (subs exception-message (count CONTINUATION_ERROR_MESSAGE))
                recoverable-buffer  (.slice buffer 0 (- (.-length buffer) (count recoverable-content)))]
            (map decode [recoverable-buffer (buf/Buffer.from recoverable-content)]))
          (js/console.error "FAILED TO DECODE" exception-message))))))

(defn decoder [chunk]
  (let [buffer (buf/Buffer.from chunk)]
    (when (zero? (.-length buffer))
      (js/console.warn "EMPTY BUFFER"))
    (->> [buffer]
         (map decode)
         not-empty)))

(defn update-results [results id decoded done?]
  (let [r (-> results
              (update-in [id :results] conj decoded)
              (get-in [id :results]))]
    (cond-> r
      done? (dissoc id))))

(defn do-receive [results {:keys [id status] :as decoded}]
  (when (and id (get @results id nil))
    (let [cb (get-in @results [id :callback])
          done? (some #{"done"} status)
          results (swap! results update-results id decoded done?)]
      (when done?
        (cb (jsify results))))))

(defn handle-data [*results _ decoded-messages]
  (doseq [msg decoded-messages]
    (do-receive *results msg))
  (println (pr-str "*results pending" @*results)))

(defn send! [write-fn! *results message callback]
  (let [id (str (random-uuid))]
    (swap! *results assoc id {:id id
                              :callback callback
                              :message message
                              :results []})
    (write-fn! (assoc message :id id))
    id))

(def client-config {:socket/encoding "utf8"
                    :socket/decoder decoder
                    :socket/encoder (comp bencoder/encode jsify)})

(defn- make-nrepl-client
  [{:keys [host port on-connect]}]
  (let [*results (atom {})
        on-close (fn [_ error?] (js/console.log "Disconnected."))

        on-data (partial handle-data *results)
        {:socket.api/keys [write! connected? end!]} (talky/make-socket-client
                                                     #:socket {:host host
                                                               :port (js/parseInt port)
                                                               :config client-config
                                                               :on-connect on-connect
                                                               :on-close on-close
                                                               :on-data on-data})]
    (when connected?
      {:send (partial send! write! *results)
       :end end!
       :connected connected?})))

(defn ^:export create
  [^js options]
  (some-> options
          cljify
          make-nrepl-client
          jsify))