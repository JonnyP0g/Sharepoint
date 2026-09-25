package com.jonnyp0g.sharepoint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;

/**
 * Best-effort summary of a shared waypoints.json file's contents, for
 * previewing before import.
 * <p>
 * This does NOT rely on a confirmed Lunar Client schema - it's a heuristic
 * that walks the JSON tree looking for objects that look like a waypoint
 * (a "name" field alongside something coordinate-like). If Lunar changes
 * their format, or this guess is simply wrong, it falls back to reporting
 * the raw file size instead of a count.
 */
final class WaypointPreview {
    private WaypointPreview() {
    }

    static String summarize(byte[] data) {
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(json);
            int count = countWaypointLikeObjects(root);
            if (count > 0) {
                return count + " waypoint" + (count == 1 ? "" : "s") + " (best guess - " + data.length + " bytes)";
            }
            return "Valid JSON, but couldn't identify waypoints in it (" + data.length + " bytes)";
        } catch (Exception e) {
            return data.length + " bytes (couldn't parse as JSON to preview)";
        }
    }

    private static int countWaypointLikeObjects(JsonElement element) {
        int count = 0;

        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (looksLikeWaypoint(object)) {
                count++;
            }
            for (String key : object.keySet()) {
                count += countWaypointLikeObjects(object.get(key));
            }
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                count += countWaypointLikeObjects(item);
            }
        }

        return count;
    }

    private static boolean looksLikeWaypoint(JsonObject object) {
        boolean hasName = object.has("name");
        boolean hasCoordinate = object.has("x") || object.has("y") || object.has("z") || object.has("location");
        return hasName && hasCoordinate;
    }
}
