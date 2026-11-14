import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import { tokenStorage } from './client'

// ONE STOMP connection per page, one subscription per topic.
// Returns whether live updates are currently flowing.
export function useLiveQueue(topics, onUpdate) {
  const [connected, setConnected] = useState(false)

  // Keep the latest callback in a ref so a new callback doesn't force a reconnect
  const onUpdateRef = useRef(onUpdate)
  useEffect(() => { onUpdateRef.current = onUpdate }, [onUpdate])

  // Arrays are new objects every render; a sorted string is a stable dependency
  const key = [...topics].sort().join('|')

  useEffect(() => {
    if (!key) return undefined              // nothing to watch -> no connection
    const list = key.split('|')
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'

    const client = new Client({
      brokerURL: `${scheme}://${window.location.host}/ws/websocket`,
      reconnectDelay: 5000,                 // auto-retry every 5s if the connection drops
      // Read the token on EVERY (re)connect, not once — it may have changed since
      beforeConnect: () => {
        client.connectHeaders = { Authorization: `Bearer ${tokenStorage.get()}` }
      },
      onConnect: () => {
        setConnected(true)
        // Subscriptions don't survive a reconnect, so (re)subscribe here every time
        list.forEach((topic) =>
          client.subscribe(topic, (msg) => onUpdateRef.current(JSON.parse(msg.body))))
      },
      onWebSocketClose: () => setConnected(false),
      // Server refused us (expired/revoked token, not our topic): stop retrying
      onStompError: (frame) => {
        console.warn('Live updates refused:', frame.headers.message)
        setConnected(false)
        client.deactivate()
      },
    })

    client.activate()
    // Leaving the page or logging out closes the socket — no leaked connections
    return () => { client.deactivate(); setConnected(false) }
  }, [key])

  return connected
}