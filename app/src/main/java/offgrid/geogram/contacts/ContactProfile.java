package offgrid.geogram.contacts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Contact profile data model.
 *
 * Mirrors the server's Profile.java structure but adapted for Android.
 * Contains contact metadata, NOSTR identity, and message statistics.
 */
public class ContactProfile {

    // Shared GSON instance for serialization
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .setPrettyPrinting()
            .create();

    // Identity
    private String callsign = "";
    private String name = "";
    private String npub = "";

    // Metadata
    private String description = "";
    private boolean hasProfilePic = false;
    private long messagesArchived = 0;

    // Timestamps
    private long lastUpdated = -1;    // -1 means unknown
    private long firstTimeSeen = -1;  // -1 means unknown

    // Profile type and visibility (for future use)
    private ProfileType profileType = ProfileType.PERSON;
    private ProfileVisibility profileVisibility = ProfileVisibility.PUBLIC;

    // --- JSON Serialization ---

    /**
     * Export this profile as JSON string.
     */
    public String toJson() {
        return GSON.toJson(this);
    }

    /**
     * Create a new ContactProfile from JSON string.
     */
    public static ContactProfile fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return GSON.fromJson(json, ContactProfile.class);
        } catch (Exception e) {
            return null;
        }
    }

    // --- Merge Logic (from server's Profile.java) ---

    /**
     * Merge fields from another profile into this one, preferring the most recent information.
     *
     * Rules:
     * - If other.lastUpdated is newer, copy current fields
     * - Fill missing important fields even if not newer
     * - messagesArchived = max(this, other)
     * - firstTimeSeen = earliest known
     * - lastUpdated = latest known
     *
     * @return true if any field changed
     */
    public boolean mergeFrom(ContactProfile other) {
        if (other == null) {
            return false;
        }

        boolean changed = false;

        // Callsign: fill if missing
        if ((this.callsign == null || this.callsign.isBlank()) &&
            other.callsign != null && !other.callsign.isBlank()) {
            this.callsign = other.callsign;
            changed = true;
        }

        // Determine if other is newer
        boolean otherIsNewer = isOtherNewer(this.lastUpdated, other.lastUpdated);

        // Update fields if newer
        if (otherIsNewer) {
            changed |= setIfDifferent(this.name, other.name, v -> this.name = v);
            changed |= setIfDifferent(this.npub, other.npub, v -> this.npub = v);
            changed |= setIfDifferent(this.description, other.description, v -> this.description = v);

            if (this.hasProfilePic != other.hasProfilePic) {
                this.hasProfilePic = other.hasProfilePic;
                changed = true;
            }

            if (this.profileType != other.profileType && other.profileType != null) {
                this.profileType = other.profileType;
                changed = true;
            }

            if (this.profileVisibility != other.profileVisibility && other.profileVisibility != null) {
                this.profileVisibility = other.profileVisibility;
                changed = true;
            }
        } else {
            // Fill missing important fields even if not newer
            if ((this.npub == null || this.npub.isBlank()) &&
                other.npub != null && !other.npub.isBlank()) {
                this.npub = other.npub;
                changed = true;
            }

            if ((this.name == null || this.name.isBlank()) &&
                other.name != null && !other.name.isBlank()) {
                this.name = other.name;
                changed = true;
            }

            if ((this.description == null || this.description.isBlank()) &&
                other.description != null && !other.description.isBlank()) {
                this.description = other.description;
                changed = true;
            }

            if (!this.hasProfilePic && other.hasProfilePic) {
                this.hasProfilePic = true;
                changed = true;
            }
        }

        // Messages: take max
        long newMsg = Math.max(this.messagesArchived, other.messagesArchived);
        if (newMsg != this.messagesArchived) {
            this.messagesArchived = newMsg;
            changed = true;
        }

        // First time seen: earliest known
        long earliest = minKnown(this.firstTimeSeen, other.firstTimeSeen);
        if (earliest != this.firstTimeSeen) {
            this.firstTimeSeen = earliest;
            changed = true;
        }

        // Last updated: latest known
        long latest = maxKnown(this.lastUpdated, other.lastUpdated);
        if (latest != this.lastUpdated) {
            this.lastUpdated = latest;
            changed = true;
        }

        // Defensive null checks
        if (this.profileType == null && other.profileType != null) {
            this.profileType = other.profileType;
            changed = true;
        }

        if (this.profileVisibility == null && other.profileVisibility != null) {
            this.profileVisibility = other.profileVisibility;
            changed = true;
        }

        return changed;
    }

    // --- Helper Methods ---

    private static boolean isOtherNewer(long current, long other) {
        if (other < 0) return false;    // other unknown -> not newer
        if (current < 0) return true;   // ours unknown -> other is newer
        return other > current;
    }

    private static long maxKnown(long a, long b) {
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.max(a, b);
    }

    private static long minKnown(long a, long b) {
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    @FunctionalInterface
    private interface Setter<T> {
        void set(T value);
    }

    private static <T> boolean setIfDifferent(T current, T newVal, Setter<T> setter) {
        if (current == null ? newVal != null : !current.equals(newVal)) {
            setter.set(newVal);
            return true;
        }
        return false;
    }

    // --- Getters and Setters ---

    public String getCallsign() {
        return callsign;
    }

    public void setCallsign(String callsign) {
        this.callsign = callsign;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNpub() {
        if (npub == null || npub.trim().isEmpty()) {
            return null;
        }
        return npub;
    }

    public void setNpub(String npub) {
        this.npub = npub;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isHasProfilePic() {
        return hasProfilePic;
    }

    public void setHasProfilePic(boolean hasProfilePic) {
        this.hasProfilePic = hasProfilePic;
    }

    public long getMessagesArchived() {
        return messagesArchived;
    }

    public void setMessagesArchived(long messagesArchived) {
        this.messagesArchived = messagesArchived;
    }

    public void incrementMessagesArchived() {
        this.messagesArchived++;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public long getFirstTimeSeen() {
        return firstTimeSeen;
    }

    public void setFirstTimeSeen(long firstTimeSeen) {
        this.firstTimeSeen = firstTimeSeen;
    }

    public ProfileType getProfileType() {
        return profileType;
    }

    public void setProfileType(ProfileType profileType) {
        this.profileType = profileType;
    }

    public ProfileVisibility getProfileVisibility() {
        return profileVisibility;
    }

    public void setProfileVisibility(ProfileVisibility profileVisibility) {
        this.profileVisibility = profileVisibility;
    }

    // --- Enums ---

    public enum ProfileType {
        PERSON,
        DEVICE,
        LOCATION,
        GROUP
    }

    public enum ProfileVisibility {
        PUBLIC,
        PRIVATE,
        UNLISTED
    }
}
