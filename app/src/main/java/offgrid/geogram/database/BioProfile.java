package offgrid.geogram.database;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.Expose;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import offgrid.geogram.core.Log;

public class BioProfile {

    @Expose
    private String id; // Limit: 15 characters

    @Expose
    private String nick; // Limit: 15 characters

    @Expose
    private String color;

    @Expose
    private String npub;

    @Expose
    private String extra;

    // only used during runtime
    private String distance = null;

    /**
     * Converts this BioProfile object to a compact JSON string.
     *
     * @return JSON string representation of this object.
     */
    public String toJson() {
        Gson gson = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .create();
        return gson.toJson(this);
    }

    /**
     * Creates a BioProfile object from a JSON string.
     *
     * @param json The JSON string representing a BioProfile.
     * @return A BioProfile object.
     */
    public static BioProfile fromJson(String json) {
        Gson gson = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .create();
        return gson.fromJson(json, BioProfile.class);
    }

    public static BioProfile fromJson(File file) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileReader reader = new FileReader(file)) {
            return gson.fromJson(reader, BioProfile.class);
        } catch (JsonSyntaxException e) {
            Log.e("BioProfile", "Error parsing JSON: " +
                    e.getMessage());
        } catch (IOException e) {
            Log.e("BioProfile", "Error reading file: " +
                    e.getMessage());
        }
        return null;
    }

    /**
     * Saves the current BeaconReachable object to a JSON file.
     *
     * @param file The file to save the JSON representation.
     * @param appContext The application context, if needed.
     */
    public void saveToFile(File file, Context appContext) {
        Gson gson = new GsonBuilder().create();
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(this, writer);
        } catch (IOException e) {
            Log.e("BioProfile", "Error saving to file: " +
                    e.getMessage());
        }
    }

    // Getters and setters (if required)
    public String getDeviceId() {
        return id;
    }

    public void setDeviceId(String id) {
        this.id = id;
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getNpub() {
        return npub;
    }

    public void setNpub(String npub) {
        this.npub = npub;
    }

//    public String getMacAddress() {
//        return macAddress;
//    }
//
//    public void setMacAddress(String macAddress) {
//        this.macAddress = macAddress;
//    }

    public void merge(BioProfile bioProfileFromFile) {
//        if (bioProfileFromFile == null) {
//            return;
//        }
//        if (this.deviceId == null) {
//            this.deviceId = bioProfileFromFile.getDeviceId();
//        }
//        if (this.nickname == null) {
//            this.nickname = bioProfileFromFile.getNickname();
//        }
    }

    /**
     * Extra is just used internally
     * @return a special value
     */
    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public String getDistance() {
        return distance;
    }

    public void setDistance(String distance) {
        this.distance = distance;
    }

    @NonNull
    @Override
    public String toString() {
        if(distance == null){
            return nick;
        }
        return nick + " (" + distance + ")"; // This will be displayed in the list
    }
}
