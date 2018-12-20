(ns calva.repl.client
  (:require
   ["net" :as net]
   [clojure-party-repl.bencode :as bencode]
   [calva.js-utils :refer [cljify jsify]]))


(defn make-socket-client
  [{:socket/keys [host port config on-connect on-close on-data]
    :or {config
         {:socket/encoder
          (fn [data]
            ;; See https://nodejs.org/api/net.html#net_socket_write_data_encoding_callback
            data)

          ;; You can also set the encoding.
          ;; See https://nodejs.org/api/net.html#net_socket_setencoding_encoding
          ;; :socket/encoding "utf8"

          :socket/decoder
          (fn [buffer-or-string]
            ;; See https://nodejs.org/api/net.html#net_event_data
            buffer-or-string)}

         on-connect
         (fn [socket]
           ;; Do stuff and returns nil.
           nil)

         on-close
         (fn [socket error?]
           ;; Do stuff and returns nil.
           nil)

         on-data
         (fn [socket buffer-or-string]
           ;; Do stuff and returns nil.
           nil)}
    :as socket}]
  (let [net-socket (doto (net/connect #js {:host host :port port})
                     (.once "connect"
                            (fn []
                              (on-connect socket)))
                     (.once "close"
                            (fn [error?]
                              (on-close socket error?)))
                     (.on "data"
                          (fn [buffer]
                            (let [{:socket/keys [decoder]} config]
                              (on-data socket (decoder buffer))))))

        net-socket (if-let [encoding (:socket/encoding config)]
                     (.setEncoding net-socket encoding)
                     net-socket)]
    {:socket.api/write!
     (fn write [data]
       (let [{:socket/keys [encoder]} config]
         (.write ^js net-socket (encoder data))))

     :socket.api/end!
     (fn []
       (.end ^js net-socket))

     :socket.api/connected?
     (fn []
       (not (.-pending ^js net-socket)))}))


(defn make-nrepl-client
  [{:keys [host port on-connect]}]
  (let [*results (atom {})

        config
        {:socket/encoding "binary"

         :socket/decoder
         (fn [buffer-or-string]
           (bencode/decode buffer-or-string))

         :socket/encoder
         (fn [data]
           (bencode/encode (jsify data)))}

        ; on-connect
        ; (fn [_]
        ;   (js/console.log "Connected."))

        on-close
        (fn [_ error?]
          (js/console.log "Disconnected."))

        ;; TODO
        on-data
        (fn [_ decoded-messages]
          (dorun (map (fn [decoded]
                        (when-let [d-id (:id decoded)]
                          (let [cb (get-in @*results [d-id :callback])
                                results (get-in (swap! *results update-in [d-id :results] (fnil conj []) decoded) [d-id :results])]
                            (when (some #{"done"} (:status decoded))
                              (println (pr-str "*results cb" @*results))
                              (swap! *results dissoc d-id)
                              (cb (jsify results))))))
                      decoded-messages))
          (println (pr-str "*results pending" @*results)))

        {:socket.api/keys [write! connected?]}
        (make-socket-client
         #:socket {:host host
                   :port (js/parseInt port)
                   :config config
                   :on-connect on-connect
                   :on-close on-close
                   :on-data on-data})]
    {:nrepl.api/send!
     (fn [message callback]
       (when connected?
         (let [id (str (random-uuid))
               message (assoc message :id id)]
           (swap! *results assoc id {:nrepl.message/id id
                                     :nrepl.message/callback callback})
           (write! message)
           id)))}))


(defn ^:export create
  [^js options]
  (let [client (make-nrepl-client (cljify options))]
    (jsify {:send (:nrepl.api/send! client)})))