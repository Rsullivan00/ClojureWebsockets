(ns websocket.server
  (:require [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [not-found]]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [org.httpkit.server :refer [send! with-channel on-close on-receive]]))

(defonce channels (atom #{}))

(defn connect! [channel]
  (swap! channels conj channel))

(defn disconnect! [channel status]
  (swap! channels disj channel))

(defn broadcast [ch msg-type payload]
  (let [msg (json/encode {:type msg-type :payload payload})]
    (run! #(send! % msg) @channels)))

(defn unknown-type-response [ch _]
  (send! ch (json/encode {:type "error" :payload "ERROR: unknown message type"})))

(defn user-disconnected [ch payload]
  (broadcast ch "userDisconnected" payload))

(defn user-entered [ch payload]
  (broadcast ch "userEntered" payload))

(defn message [ch payload]
  (broadcast ch "message" payload))

(defn start-typing [ch payload]
  (broadcast ch "userDidStartTyping" payload))

(defn stop-typing [ch payload]
  (broadcast ch "userDidStopTyping" payload))

(defn dispatch [ch msg]
  (let [parsed (json/decode msg)]
    ((case (get parsed "type")
        "userDisconnected" user-disconnected
        "userEntered" user-entered
        "userDidStartTyping" start-typing
        "userDidStopTyping" stop-typing
        "message" message
        unknown-type-response)
      ch (get parsed "payload"))))

(defn ws-handler [request]
  (with-channel request channel
    (connect! channel)
    (on-close channel #(disconnect! channel %))
    (on-receive channel #(dispatch channel %))))

(defroutes app
  (GET "/ws" request (ws-handler request)))
  (not-found "Route not found")
