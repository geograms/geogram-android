package offgrid.geogram.contacts;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class ContactProfileTest {

    @Test
    public void testCreateProfile() {
        ContactProfile profile = new ContactProfile();
        profile.setCallsign("CR7BBQ");
        profile.setName("Test User");
        profile.setNpub("npub1test...");

        assertEquals("CR7BBQ", profile.getCallsign());
        assertEquals("Test User", profile.getName());
        assertEquals("npub1test...", profile.getNpub());
    }

    @Test
    public void testJsonSerialization() {
        ContactProfile profile = new ContactProfile();
        profile.setCallsign("CR7BBQ");
        profile.setName("Test User");
        profile.setNpub("npub1test...");
        profile.setDescription("Test description");
        profile.setHasProfilePic(true);
        profile.setMessagesArchived(42);
        profile.setFirstTimeSeen(1000000);
        profile.setLastUpdated(2000000);

        // Serialize to JSON
        String json = profile.toJson();
        assertNotNull(json);
        assertTrue(json.contains("CR7BBQ"));
        assertTrue(json.contains("Test User"));

        // Deserialize from JSON
        ContactProfile loaded = ContactProfile.fromJson(json);
        assertNotNull(loaded);
        assertEquals("CR7BBQ", loaded.getCallsign());
        assertEquals("Test User", loaded.getName());
        assertEquals("npub1test...", loaded.getNpub());
        assertEquals("Test description", loaded.getDescription());
        assertTrue(loaded.isHasProfilePic());
        assertEquals(42, loaded.getMessagesArchived());
        assertEquals(1000000, loaded.getFirstTimeSeen());
        assertEquals(2000000, loaded.getLastUpdated());
    }

    @Test
    public void testFromJsonNull() {
        ContactProfile profile = ContactProfile.fromJson(null);
        assertNull(profile);
    }

    @Test
    public void testFromJsonEmpty() {
        ContactProfile profile = ContactProfile.fromJson("");
        assertNull(profile);
    }

    @Test
    public void testFromJsonInvalid() {
        ContactProfile profile = ContactProfile.fromJson("not valid json");
        assertNull(profile);
    }

    @Test
    public void testGetNpubReturnsNullWhenEmpty() {
        ContactProfile profile = new ContactProfile();
        assertNull(profile.getNpub());

        profile.setNpub("");
        assertNull(profile.getNpub());

        profile.setNpub("   ");
        assertNull(profile.getNpub());
    }

    @Test
    public void testIncrementMessagesArchived() {
        ContactProfile profile = new ContactProfile();
        assertEquals(0, profile.getMessagesArchived());

        profile.incrementMessagesArchived();
        assertEquals(1, profile.getMessagesArchived());

        profile.incrementMessagesArchived();
        assertEquals(2, profile.getMessagesArchived());
    }

    @Test
    public void testMergeFrom_NullOther() {
        ContactProfile profile = new ContactProfile();
        profile.setCallsign("CR7BBQ");

        boolean changed = profile.mergeFrom(null);
        assertFalse(changed);
        assertEquals("CR7BBQ", profile.getCallsign());
    }

    @Test
    public void testMergeFrom_FillMissingCallsign() {
        ContactProfile profile = new ContactProfile();
        ContactProfile other = new ContactProfile();
        other.setCallsign("CR7BBQ");

        boolean changed = profile.mergeFrom(other);
        assertTrue(changed);
        assertEquals("CR7BBQ", profile.getCallsign());
    }

    @Test
    public void testMergeFrom_NewerTimestamp() {
        ContactProfile profile = new ContactProfile();
        profile.setCallsign("CR7BBQ");
        profile.setName("Old Name");
        profile.setLastUpdated(1000000);

        ContactProfile other = new ContactProfile();
        other.setCallsign("CR7BBQ");
        other.setName("New Name");
        other.setLastUpdated(2000000);

        boolean changed = profile.mergeFrom(other);
        assertTrue(changed);
        assertEquals("New Name", profile.getName());
        assertEquals(2000000, profile.getLastUpdated());
    }

    @Test
    public void testMergeFrom_OlderTimestamp() {
        ContactProfile profile = new ContactProfile();
        profile.setCallsign("CR7BBQ");
        profile.setName("Current Name");
        profile.setLastUpdated(2000000);

        ContactProfile other = new ContactProfile();
        other.setCallsign("CR7BBQ");
        other.setName("Old Name");
        other.setLastUpdated(1000000);

        boolean changed = profile.mergeFrom(other);
        assertFalse(changed); // Name shouldn't change
        assertEquals("Current Name", profile.getName());
    }

    @Test
    public void testMergeFrom_FillMissingFieldsEvenIfOlder() {
        ContactProfile profile = new ContactProfile();
        profile.setCallsign("CR7BBQ");
        profile.setLastUpdated(2000000);
        // No name set

        ContactProfile other = new ContactProfile();
        other.setCallsign("CR7BBQ");
        other.setName("Filled Name");
        other.setLastUpdated(1000000); // Older

        boolean changed = profile.mergeFrom(other);
        assertTrue(changed);
        assertEquals("Filled Name", profile.getName()); // Should fill even though older
    }

    @Test
    public void testMergeFrom_MessagesArchivedMax() {
        ContactProfile profile = new ContactProfile();
        profile.setMessagesArchived(10);

        ContactProfile other = new ContactProfile();
        other.setMessagesArchived(20);

        boolean changed = profile.mergeFrom(other);
        assertTrue(changed);
        assertEquals(20, profile.getMessagesArchived());

        // Reverse: no change
        ContactProfile profile2 = new ContactProfile();
        profile2.setMessagesArchived(30);

        ContactProfile other2 = new ContactProfile();
        other2.setMessagesArchived(15);

        changed = profile2.mergeFrom(other2);
        assertFalse(changed); // Already has higher value
        assertEquals(30, profile2.getMessagesArchived());
    }

    @Test
    public void testMergeFrom_FirstTimeSeenEarliest() {
        ContactProfile profile = new ContactProfile();
        profile.setFirstTimeSeen(2000000);

        ContactProfile other = new ContactProfile();
        other.setFirstTimeSeen(1000000);

        boolean changed = profile.mergeFrom(other);
        assertTrue(changed);
        assertEquals(1000000, profile.getFirstTimeSeen()); // Earlier time

        // Reverse: no change
        ContactProfile profile2 = new ContactProfile();
        profile2.setFirstTimeSeen(500000);

        ContactProfile other2 = new ContactProfile();
        other2.setFirstTimeSeen(1000000);

        changed = profile2.mergeFrom(other2);
        assertFalse(changed); // Already has earlier value
        assertEquals(500000, profile2.getFirstTimeSeen());
    }

    @Test
    public void testMergeFrom_LastUpdatedLatest() {
        ContactProfile profile = new ContactProfile();
        profile.setLastUpdated(1000000);

        ContactProfile other = new ContactProfile();
        other.setLastUpdated(2000000);

        boolean changed = profile.mergeFrom(other);
        assertTrue(changed);
        assertEquals(2000000, profile.getLastUpdated()); // Later time

        // Reverse: no change
        ContactProfile profile2 = new ContactProfile();
        profile2.setLastUpdated(3000000);

        ContactProfile other2 = new ContactProfile();
        other2.setLastUpdated(2000000);

        changed = profile2.mergeFrom(other2);
        assertFalse(changed); // Already has later value
        assertEquals(3000000, profile2.getLastUpdated());
    }

    @Test
    public void testMergeFrom_UnknownTimestamps() {
        ContactProfile profile = new ContactProfile();
        profile.setFirstTimeSeen(-1);
        profile.setLastUpdated(-1);

        ContactProfile other = new ContactProfile();
        other.setFirstTimeSeen(1000000);
        other.setLastUpdated(2000000);

        boolean changed = profile.mergeFrom(other);
        assertTrue(changed);
        assertEquals(1000000, profile.getFirstTimeSeen());
        assertEquals(2000000, profile.getLastUpdated());
    }

    @Test
    public void testMergeFrom_ProfilePicFlag() {
        ContactProfile profile = new ContactProfile();
        profile.setHasProfilePic(false);
        profile.setLastUpdated(2000000);

        ContactProfile other = new ContactProfile();
        other.setHasProfilePic(true);
        other.setLastUpdated(3000000);

        boolean changed = profile.mergeFrom(other);
        assertTrue(changed);
        assertTrue(profile.isHasProfilePic());
    }

    @Test
    public void testMergeFrom_ProfileType() {
        ContactProfile profile = new ContactProfile();
        profile.setProfileType(ContactProfile.ProfileType.PERSON);
        profile.setLastUpdated(1000000);

        ContactProfile other = new ContactProfile();
        other.setProfileType(ContactProfile.ProfileType.DEVICE);
        other.setLastUpdated(2000000);

        boolean changed = profile.mergeFrom(other);
        assertTrue(changed);
        assertEquals(ContactProfile.ProfileType.DEVICE, profile.getProfileType());
    }

    @Test
    public void testMergeFrom_ProfileVisibility() {
        ContactProfile profile = new ContactProfile();
        profile.setProfileVisibility(ContactProfile.ProfileVisibility.PUBLIC);
        profile.setLastUpdated(1000000);

        ContactProfile other = new ContactProfile();
        other.setProfileVisibility(ContactProfile.ProfileVisibility.PRIVATE);
        other.setLastUpdated(2000000);

        boolean changed = profile.mergeFrom(other);
        assertTrue(changed);
        assertEquals(ContactProfile.ProfileVisibility.PRIVATE, profile.getProfileVisibility());
    }
}
