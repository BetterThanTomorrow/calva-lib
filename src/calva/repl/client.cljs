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

(defn update-state [*state {:keys [id status] :as decoded}]
  (println "update-state: " (pr-str *state) " status: " (pr-str status))
  (let [done? (some #{"done"} status)]
    (println "done? " done?)
    (-> *state
        (update-in [id :results] (fnil conj []) decoded)
        (assoc-in [id :done?] done?))))

(defn do-receive [*state [decoded]]
  (println "do-receive, @*state: " (pr-str @*state) "decoded: " (pr-str decoded))
  (when-let [{:keys [id]} decoded]
    (when (get @*state id)
      (println "do-receive, id: " id " decoded: " decoded)
      (let [new-state (swap! *state update-state decoded)]
        (println "new-state: " (pr-str new-state))
        (when (:done? (get new-state id))
          (swap! *state dissoc id)
          (let [cb (get-in new-state [id :callback])
                results (get-in new-state [id :results])]
            (cb (jsify results))))))))

(defn handle-state [*state _ decoded-messages]
  (doseq [msg decoded-messages]
    (do-receive *state msg))
  (println (pr-str "*state pending:" @*state)))

(defn send! [write-fn! *state message callback]
  (let [id (str (random-uuid))]
    (swap! *state assoc id {:callback callback
                            :message message
                            :results []})
    (write-fn! (assoc message :id id))
    id))

(def client-config {:socket/encoding "utf8"
                    :socket/decoder decoder
                    :socket/encoder (comp bencoder/encode jsify)})

(defn- make-nrepl-client
  [{:keys [host port on-connect]}]
  (let [*state (atom {})
        on-close (fn [_ error?] (js/console.log "Disconnected."))
        on-data (partial handle-state *state)
        {:socket.api/keys [write! connected? end!]} (talky/make-socket-client
                                                     #:socket {:host host
                                                               :port (js/parseInt port)
                                                               :config client-config
                                                               :on-connect on-connect
                                                               :on-close on-close
                                                               :on-data on-data})]
    (when connected?
      {:send (partial send! write! *state)
       :end end!
       :connected connected?})))

(defn ^:export create
  [^js options]
  (some-> options
          cljify
          make-nrepl-client
          jsify))