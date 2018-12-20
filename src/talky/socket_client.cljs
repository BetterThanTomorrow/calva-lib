(ns talky.socket-client
  (:require
   ["net" :as net]))

;; From https://github.com/pedrorgirardi/talky

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