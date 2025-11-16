package offgrid.geogram.p2p;

import android.content.Context;
import android.content.SharedPreferences;
import offgrid.geogram.core.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles peer discovery using APRS-IS messages.
 *
 * Phones announce their presence via APRS-IS with messages like:
 * X1ABCD>X11GEO,TCPIP*:>p2p-12D3KooWFTef23s8k1n68TtY5kzKjeDXu2NhpW3vAxJSesZH3zrz
 *
 * This service:
 * - Listens for "p2p-" announcements on APRS-IS
 * - Caches discovered peers to disk for reuse on app restart
 * - Announces this device's presence periodically (via AprsIsService)
 */
public class AprsPeerDiscovery {
    private static final String TAG = "P2P/APRSDiscovery";

    // Discovery announcement format (shortened to reduce APRS message size)
    private static final String ANNOUNCEMENT_PREFIX = "p2p-";

    // Cache preferences
    private static final String PREFS_NAME = "aprs_peer_cache";
    private static final String PREF_PEERS = "discovered_peers";
    private static final String PREF_LAST_UPDATED = "last_updated_";

    // Cache expiry (7 days - peers don't change IDs often)
    private static final long CACHE_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L;

    private final Context context;
    private final String localPeerId;
    private final String localCallsign;
    private final SharedPreferences cache;

    private List<DiscoveryListener> listeners = new ArrayList<>();

    // In-memory cache of discovered peers
    private Set<PeerInfo> discoveredPeers = new HashSet<>();

    public AprsPeerDiscovery(Context context, String localPeerId, String localCallsign) {
        this.context = context.getApplicationContext();
        this.localPeerId = localPeerId;
        this.localCallsign = localCallsign;
        this.cache = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Load cached peers on startup
        loadCachedPeers();
    }

    /**
     * Start peer discovery (loads cached peers)
     * Note: Actual APRS-IS connection is handled by AprsIsService
     */
    public void start() {
        Log.i(TAG, "Starting APRS-IS peer discovery");
        Log.i(TAG, "Announcement message: " + getAnnouncementMessage());
        Log.i(TAG, "Note: APRS-IS networking is handled by AprsIsService");
    }

    /**
     * Stop peer discovery and save cache
     */
    public void stop() {
        Log.i(TAG, "Stopping APRS-IS peer discovery");

        // Save cache before stopping
        saveCachedPeers();

        Log.i(TAG, "APRS-IS discovery stopped");
    }

    /**
     * Get APRS announcement message for this peer
     * Format: p2p-<peerId>
     */
    public String getAnnouncementMessage() {
        return ANNOUNCEMENT_PREFIX + localPeerId;
    }

    /**
     * Handle incoming APRS message
     * Call this when receiving APRS messages to check for peer announcements
     *
     * @param aprsMessage The raw APRS message
     */
    public void handleAprsMessage(String aprsMessage) {
        // Log all incoming messages for debugging
        Log.d(TAG, "Processing APRS message: " + aprsMessage);

        if (aprsMessage == null) {
            Log.d(TAG, "Ignoring null message");
            return;
        }

        if (!aprsMessage.contains(ANNOUNCEMENT_PREFIX)) {
            Log.d(TAG, "Ignoring message - does not contain announcement prefix '" + ANNOUNCEMENT_PREFIX + "'");
            return;
        }

        try {
            // Extract peer ID from message
            // Expected format: ...>...:>p2p-12D3KooW...
            int prefixIndex = aprsMessage.indexOf(ANNOUNCEMENT_PREFIX);
            if (prefixIndex == -1) {
                Log.d(TAG, "Ignoring message - prefix index not found");
                return;
            }

            // Get everything after "p2p-" and extract just the peer ID
            String afterPrefix = aprsMessage.substring(prefixIndex + ANNOUNCEMENT_PREFIX.length());
            // Peer ID is everything from here until whitespace or end of string
            String peerId = afterPrefix.split("\\s+")[0]; // First token (peer ID)

            Log.d(TAG, "Extracted peer ID: " + peerId);

            // Ignore our own announcements
            if (peerId.equals(localPeerId)) {
                Log.d(TAG, "Ignoring own announcement (peer ID matches local ID)");
                return;
            }

            // Extract callsign if possible (from APRS header)
            String callsign = extractCallsign(aprsMessage);

            // Create peer info
            PeerInfo peerInfo = new PeerInfo(peerId, callsign, System.currentTimeMillis());

            // Add to discovered peers
            if (discoveredPeers.add(peerInfo)) {
                Log.i(TAG, "╔═══════════════════════════════════════════════════════╗");
                Log.i(TAG, "║ NEW PEER DISCOVERED VIA APRS-IS                      ║");
                Log.i(TAG, "╠═══════════════════════════════════════════════════════╣");
                Log.i(TAG, "║ Peer ID:  " + peerId);
                if (callsign != null) {
                    Log.i(TAG, "║ Callsign: " + callsign);
                }
                Log.i(TAG, "║ Time:     " + new java.util.Date().toString());
                Log.i(TAG, "║ Total peers in cache: " + discoveredPeers.size());
                Log.i(TAG, "╚═══════════════════════════════════════════════════════╝");

                // Save to cache
                saveCachedPeers();

                // Notify listeners
                for (DiscoveryListener listener : listeners) {
                    listener.onPeerDiscovered(peerInfo);
                }
            } else {
                // Update timestamp for existing peer
                for (PeerInfo existing : discoveredPeers) {
                    if (existing.peerId.equals(peerId)) {
                        existing.lastSeen = System.currentTimeMillis();
                        Log.d(TAG, "Updated timestamp for existing peer: " + peerId +
                                  (callsign != null ? " (" + callsign + ")" : ""));
                        saveCachedPeers();
                        break;
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing APRS peer announcement: " + e.getMessage());
        }
    }

    /**
     * Extract callsign from APRS message header
     * Format: CALLSIGN>...
     */
    private String extractCallsign(String aprsMessage) {
        try {
            int gt = aprsMessage.indexOf('>');
            if (gt > 0) {
                return aprsMessage.substring(0, gt).trim();
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    /**
     * Get list of discovered peers (including cached)
     */
    public List<PeerInfo> getDiscoveredPeers() {
        return new ArrayList<>(discoveredPeers);
    }

    /**
     * Load cached peers from disk
     */
    private void loadCachedPeers() {
        try {
            String json = cache.getString(PREF_PEERS, null);
            if (json == null) {
                Log.d(TAG, "No cached peers found");
                return;
            }

            JSONArray array = new JSONArray(json);
            long now = System.currentTimeMillis();
            int loaded = 0;
            int expired = 0;

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String peerId = obj.getString("peerId");
                String callsign = obj.optString("callsign", null);
                long lastSeen = obj.getLong("lastSeen");

                // Check if cache entry is still valid
                if (now - lastSeen < CACHE_EXPIRY_MS) {
                    discoveredPeers.add(new PeerInfo(peerId, callsign, lastSeen));
                    loaded++;
                } else {
                    expired++;
                }
            }

            if (loaded > 0) {
                Log.i(TAG, "╔═══════════════════════════════════════════════════════╗");
                Log.i(TAG, "║ LOADED CACHED PEERS FROM STORAGE                     ║");
                Log.i(TAG, "╠═══════════════════════════════════════════════════════╣");
                Log.i(TAG, "║ Valid peers:   " + loaded);
                Log.i(TAG, "║ Expired peers: " + expired);
                Log.i(TAG, "╚═══════════════════════════════════════════════════════╝");
            } else {
                Log.i(TAG, "No cached peers found (expired: " + expired + ")");
            }

            // Notify listeners about cached peers
            for (PeerInfo peer : discoveredPeers) {
                for (DiscoveryListener listener : listeners) {
                    listener.onPeerDiscovered(peer);
                }
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error loading cached peers: " + e.getMessage());
        }
    }

    /**
     * Save discovered peers to disk cache
     */
    private void saveCachedPeers() {
        try {
            JSONArray array = new JSONArray();
            for (PeerInfo peer : discoveredPeers) {
                JSONObject obj = new JSONObject();
                obj.put("peerId", peer.peerId);
                if (peer.callsign != null) {
                    obj.put("callsign", peer.callsign);
                }
                obj.put("lastSeen", peer.lastSeen);
                array.put(obj);
            }

            cache.edit().putString(PREF_PEERS, array.toString()).apply();
            Log.d(TAG, "Saved " + discoveredPeers.size() + " peers to cache");

        } catch (JSONException e) {
            Log.e(TAG, "Error saving peer cache: " + e.getMessage());
        }
    }

    /**
     * Add a discovery listener
     */
    public void addListener(DiscoveryListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a discovery listener
     */
    public void removeListener(DiscoveryListener listener) {
        listeners.remove(listener);
    }

    /**
     * Clear all discovered peers from cache
     */
    public void clearCache() {
        Log.i(TAG, "Clearing peer cache");
        discoveredPeers.clear();
        saveCachedPeers();
        Log.i(TAG, "Peer cache cleared");
    }

    /**
     * Information about a discovered peer
     */
    public static class PeerInfo {
        public final String peerId;
        public final String callsign;  // APRS callsign if available
        public long lastSeen;

        public PeerInfo(String peerId, String callsign, long lastSeen) {
            this.peerId = peerId;
            this.callsign = callsign;
            this.lastSeen = lastSeen;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PeerInfo peerInfo = (PeerInfo) o;
            return peerId.equals(peerInfo.peerId);
        }

        @Override
        public int hashCode() {
            return peerId.hashCode();
        }
    }

    /**
     * Listener interface for peer discovery events
     */
    public interface DiscoveryListener {
        void onPeerDiscovered(PeerInfo peerInfo);
    }
}
