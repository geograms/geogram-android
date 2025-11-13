package offgrid.geogram.models;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Model for collection security settings (security.json)
 */
public class CollectionSecurity implements Serializable {

    public enum Visibility {
        PUBLIC("public"),
        PRIVATE("private"),
        PASSWORD("password"),
        GROUP("group");

        private final String value;

        Visibility(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Visibility fromString(String value) {
            for (Visibility v : values()) {
                if (v.value.equals(value)) {
                    return v;
                }
            }
            return PUBLIC; // Default
        }
    }

    private String version = "1.0";
    private Visibility visibility = Visibility.PUBLIC;

    // Permissions
    private boolean publicRead = true;
    private boolean allowSubmissions = false;
    private boolean submissionRequiresApproval = true;
    private boolean canUsersComment = true;
    private boolean canUsersLike = true;
    private boolean canUsersDislike = true;
    private boolean canUsersRate = true;
    private List<String> whitelistedUsers = new ArrayList<>();
    private List<String> blockedUsers = new ArrayList<>();

    // Additional fields
    private List<String> contentWarnings = new ArrayList<>();
    private boolean ageRestrictionEnabled = false;
    private int minimumAge = 0;
    private boolean encryptionEnabled = false;

    public CollectionSecurity() {
    }

    // Getters and Setters
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public boolean isPublicRead() {
        return publicRead;
    }

    public void setPublicRead(boolean publicRead) {
        this.publicRead = publicRead;
    }

    public boolean isAllowSubmissions() {
        return allowSubmissions;
    }

    public void setAllowSubmissions(boolean allowSubmissions) {
        this.allowSubmissions = allowSubmissions;
    }

    public boolean isSubmissionRequiresApproval() {
        return submissionRequiresApproval;
    }

    public void setSubmissionRequiresApproval(boolean submissionRequiresApproval) {
        this.submissionRequiresApproval = submissionRequiresApproval;
    }

    public boolean isCanUsersComment() {
        return canUsersComment;
    }

    public void setCanUsersComment(boolean canUsersComment) {
        this.canUsersComment = canUsersComment;
    }

    public boolean isCanUsersLike() {
        return canUsersLike;
    }

    public void setCanUsersLike(boolean canUsersLike) {
        this.canUsersLike = canUsersLike;
    }

    public boolean isCanUsersDislike() {
        return canUsersDislike;
    }

    public void setCanUsersDislike(boolean canUsersDislike) {
        this.canUsersDislike = canUsersDislike;
    }

    public boolean isCanUsersRate() {
        return canUsersRate;
    }

    public void setCanUsersRate(boolean canUsersRate) {
        this.canUsersRate = canUsersRate;
    }

    public List<String> getWhitelistedUsers() {
        return whitelistedUsers;
    }

    public void setWhitelistedUsers(List<String> whitelistedUsers) {
        this.whitelistedUsers = whitelistedUsers;
    }

    public List<String> getBlockedUsers() {
        return blockedUsers;
    }

    public void setBlockedUsers(List<String> blockedUsers) {
        this.blockedUsers = blockedUsers;
    }

    public List<String> getContentWarnings() {
        return contentWarnings;
    }

    public void setContentWarnings(List<String> contentWarnings) {
        this.contentWarnings = contentWarnings;
    }

    public boolean isAgeRestrictionEnabled() {
        return ageRestrictionEnabled;
    }

    public void setAgeRestrictionEnabled(boolean ageRestrictionEnabled) {
        this.ageRestrictionEnabled = ageRestrictionEnabled;
    }

    public int getMinimumAge() {
        return minimumAge;
    }

    public void setMinimumAge(int minimumAge) {
        this.minimumAge = minimumAge;
    }

    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }

    public void setEncryptionEnabled(boolean encryptionEnabled) {
        this.encryptionEnabled = encryptionEnabled;
    }

    /**
     * Check if this collection is accessible via public HTTP API
     * @return true if collection is public and has public read access
     */
    public boolean isPubliclyAccessible() {
        return visibility == Visibility.PUBLIC && publicRead;
    }

    /**
     * Parse CollectionSecurity from security.json content
     */
    public static CollectionSecurity fromJSON(JSONObject json) {
        CollectionSecurity security = new CollectionSecurity();

        try {
            security.setVersion(json.optString("version", "1.0"));
            security.setVisibility(Visibility.fromString(json.optString("visibility", "public")));

            JSONObject permissions = json.optJSONObject("permissions");
            if (permissions != null) {
                security.setPublicRead(permissions.optBoolean("public_read", true));
                security.setAllowSubmissions(permissions.optBoolean("allow_submissions", false));
                security.setSubmissionRequiresApproval(permissions.optBoolean("submission_requires_approval", true));
                security.setCanUsersComment(permissions.optBoolean("can_users_comment", true));
                security.setCanUsersLike(permissions.optBoolean("can_users_like", true));
                security.setCanUsersDislike(permissions.optBoolean("can_users_dislike", true));
                security.setCanUsersRate(permissions.optBoolean("can_users_rate", true));

                JSONArray whitelisted = permissions.optJSONArray("whitelisted_users");
                if (whitelisted != null) {
                    List<String> users = new ArrayList<>();
                    for (int i = 0; i < whitelisted.length(); i++) {
                        users.add(whitelisted.getString(i));
                    }
                    security.setWhitelistedUsers(users);
                }

                JSONArray blocked = permissions.optJSONArray("blocked_users");
                if (blocked != null) {
                    List<String> users = new ArrayList<>();
                    for (int i = 0; i < blocked.length(); i++) {
                        users.add(blocked.getString(i));
                    }
                    security.setBlockedUsers(users);
                }
            }

            JSONArray warnings = json.optJSONArray("content_warnings");
            if (warnings != null) {
                List<String> warningsList = new ArrayList<>();
                for (int i = 0; i < warnings.length(); i++) {
                    warningsList.add(warnings.getString(i));
                }
                security.setContentWarnings(warningsList);
            }

            JSONObject ageRestriction = json.optJSONObject("age_restriction");
            if (ageRestriction != null) {
                security.setAgeRestrictionEnabled(ageRestriction.optBoolean("enabled", false));
                security.setMinimumAge(ageRestriction.optInt("minimum_age", 0));
            }

            JSONObject encryption = json.optJSONObject("encryption");
            if (encryption != null) {
                security.setEncryptionEnabled(encryption.optBoolean("enabled", false));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return security;
    }

    /**
     * Convert to JSON format for writing to security.json
     */
    public JSONObject toJSON() {
        try {
            JSONObject json = new JSONObject();
            json.put("version", version);
            json.put("visibility", visibility.getValue());

            JSONObject permissions = new JSONObject();
            permissions.put("public_read", publicRead);
            permissions.put("allow_submissions", allowSubmissions);
            permissions.put("submission_requires_approval", submissionRequiresApproval);
            permissions.put("can_users_comment", canUsersComment);
            permissions.put("can_users_like", canUsersLike);
            permissions.put("can_users_dislike", canUsersDislike);
            permissions.put("can_users_rate", canUsersRate);
            permissions.put("whitelisted_users", new JSONArray(whitelistedUsers));
            permissions.put("blocked_users", new JSONArray(blockedUsers));
            json.put("permissions", permissions);

            json.put("content_warnings", new JSONArray(contentWarnings));

            JSONObject ageRestriction = new JSONObject();
            ageRestriction.put("enabled", ageRestrictionEnabled);
            ageRestriction.put("minimum_age", minimumAge);
            ageRestriction.put("verification_required", false);
            json.put("age_restriction", ageRestriction);

            JSONObject encryption = new JSONObject();
            encryption.put("enabled", encryptionEnabled);
            encryption.put("method", JSONObject.NULL);
            encryption.put("encrypted_files", new JSONArray());
            json.put("encryption", encryption);

            return json;
        } catch (Exception e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }
}
