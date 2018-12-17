(ns calva.repl.nrepl
  (:require
   ["net" :as net]
   ["bencoder" :as bencoder]
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

(def *messages (atom {}))

(defn message [^js conn msg callback]
  (let [id (str (random-uuid))]
    (swap! *messages assoc id {:callback callback
                               :results []})
    (.on conn "data" (fn [chunk]
                       (when-let [decoded-messages (let [empty-buffer (buf/Buffer.from "")
                                                         buffer       (buf/Buffer.concat (jsify [empty-buffer chunk]))]
                                                     (when (= 0 (.-length buffer))
                                                       (js/console.warn "EMPTY BUFFER"))
                                                     (not-empty (decode [buffer])))]
                         (println (pr-str decoded-messages))
                         (map (fn [k] (swap! *messages assoc k "foo")) [:a :b])
                         (map (fn [] (println "FOO")) [1 2 3])
                         (swap! *messages assoc 1 :bar)
                         (map (fn [decoded]
                                (let [d-id (:id decoded)
                                      cb (get-in @*messages [d-id :callback])
                                      results (get-in @*messages [d-id :results])]
                                  (println "d-id" d-id)
                                  (println "*messages" @*messages)
                                  (swap! *messages assoc-in [d-id :results] (conj results decoded))
                                  (when (some #(= "done" %) (mapcat :status decoded-messages))
                                    (let [results (get-in @*messages [d-id :results])]
                                      (println "results" results)
                                      (println "cb" cb)
                                      (swap! *messages dissoc d-id)
                                      (cb (jsify results))))))
                              decoded-messages)
                         (println (pr-str @*messages)))))
    (.write conn (bencoder/encode (jsify (assoc msg :id id))) "binary")))


(comment
  (def messages (atom {}))
  (let [dm '({:id "a74d6afa-925b-4801-9ab1-7d5879e07802", :new-session "357076d7-fdbb-4d42-8ad2-4284c4980885", :session "d59020f4-2220-477b-9b29-96cbbf653005", :status ["done"]})]
    (map (fn [decoded]
           (let [d-id (:id decoded)
                 cb (get-in @messages [d-id :callback])
                 results (get-in @messages [d-id :results])]
             (swap! messages assoc-in [d-id :results] (conj results decoded))))
         dm)))