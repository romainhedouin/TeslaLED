package com.teslaowls.teslaled.storage;

import android.content.Context;
import android.content.res.AssetManager;

import com.teslaowls.teslaled.model.Frame;
import com.teslaowls.teslaled.model.PanelMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Holds every message the app knows about: built-in defaults (bundled as an
 * asset, one .ppm per frame) plus user-created ones (persisted under the
 * app's internal files dir). Message counts here are in the dozens, so a
 * plain JSON index + in-memory list/filter is simpler than a real database
 * and entirely sufficient.
 */
public class MessageStore {

    private static final String DEFAULT_MESSAGES_ASSET = "default_messages.json";
    private static final String USER_MESSAGES_INDEX = "messages.json";
    private static final String USER_MESSAGES_DIR = "messages";

    private final Context context;
    private final List<PanelMessage> messages = new ArrayList<>();

    public MessageStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public void load() throws IOException, JSONException {
        messages.clear();
        messages.addAll(loadDefaultMessages());
        messages.addAll(loadUserMessages());
    }

    public List<PanelMessage> getAll() {
        return Collections.unmodifiableList(messages);
    }

    public List<PanelMessage> getFiltered(String category, String language) {
        List<PanelMessage> result = new ArrayList<>();
        for (PanelMessage message : messages) {
            boolean categoryMatches = category == null || category.isEmpty() || category.equals(message.category);
            // A language-neutral message (no language tag) is valid for every
            // language filter, not just "All" - it shouldn't disappear just
            // because the user is filtered to FR or EN.
            boolean languageMatches = language == null || language.isEmpty()
                    || message.language == null || message.language.isEmpty()
                    || language.equals(message.language);
            if (categoryMatches && languageMatches) {
                result.add(message);
            }
        }
        return result;
    }

    /** Creates a brand-new user message: writes each frame's PPM to its own file and updates the index. */
    public PanelMessage createUserMessage(String label, String category, String language, List<Frame> frames)
            throws IOException, JSONException {
        String id = UUID.randomUUID().toString();
        File messageDir = userMessageDir(id);
        if (!messageDir.mkdirs()) {
            throw new IOException("Couldn't create message directory: " + messageDir);
        }

        for (int i = 0; i < frames.size(); i++) {
            try (FileOutputStream out = new FileOutputStream(new File(messageDir, "frame" + i + ".ppm"))) {
                out.write(frames.get(i).ppmBytes);
            }
        }

        PanelMessage message = new PanelMessage(id, label, category, language, frames, false);
        messages.add(message);
        persistUserMessagesIndex();
        return message;
    }

    public void deleteUserMessage(String id) throws IOException, JSONException {
        PanelMessage toRemove = null;
        for (PanelMessage message : messages) {
            if (message.id.equals(id) && !message.builtIn) {
                toRemove = message;
                break;
            }
        }
        if (toRemove == null) {
            return;
        }
        messages.remove(toRemove);
        deleteRecursively(userMessageDir(id));
        persistUserMessagesIndex();
    }

    private List<PanelMessage> loadDefaultMessages() throws IOException, JSONException {
        AssetManager assetManager = context.getAssets();
        String json = readAssetAsString(assetManager, DEFAULT_MESSAGES_ASSET);
        JSONArray array = new JSONArray(json);

        List<PanelMessage> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            JSONArray framesJson = obj.getJSONArray("frames");
            List<Frame> frames = new ArrayList<>();
            for (int f = 0; f < framesJson.length(); f++) {
                JSONObject frameObj = framesJson.getJSONObject(f);
                byte[] ppmBytes = readAssetAsBytes(assetManager, frameObj.getString("asset"));
                frames.add(new Frame(ppmBytes, frameObj.getInt("durationMs")));
            }
            result.add(new PanelMessage(
                    obj.getString("id"),
                    obj.getString("label"),
                    obj.getString("category"),
                    obj.getString("language"),
                    frames,
                    true));
        }
        return result;
    }

    private List<PanelMessage> loadUserMessages() throws IOException, JSONException {
        File indexFile = new File(context.getFilesDir(), USER_MESSAGES_INDEX);
        if (!indexFile.exists()) {
            return Collections.emptyList();
        }

        String json = readFileAsString(indexFile);
        JSONArray array = new JSONArray(json);

        List<PanelMessage> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            String id = obj.getString("id");
            JSONArray framesJson = obj.getJSONArray("frames");
            List<Frame> frames = new ArrayList<>();
            for (int f = 0; f < framesJson.length(); f++) {
                JSONObject frameObj = framesJson.getJSONObject(f);
                File frameFile = new File(userMessageDir(id), frameObj.getString("file"));
                frames.add(new Frame(readFileAsBytes(frameFile), frameObj.getInt("durationMs")));
            }
            result.add(new PanelMessage(
                    id,
                    obj.getString("label"),
                    obj.getString("category"),
                    obj.getString("language"),
                    frames,
                    false));
        }
        return result;
    }

    private void persistUserMessagesIndex() throws IOException, JSONException {
        JSONArray array = new JSONArray();
        for (PanelMessage message : messages) {
            if (message.builtIn) {
                continue;
            }
            JSONObject obj = new JSONObject();
            obj.put("id", message.id);
            obj.put("label", message.label);
            obj.put("category", message.category);
            obj.put("language", message.language);
            JSONArray framesJson = new JSONArray();
            for (int i = 0; i < message.frames.size(); i++) {
                JSONObject frameObj = new JSONObject();
                frameObj.put("file", "frame" + i + ".ppm");
                frameObj.put("durationMs", message.frames.get(i).durationMs);
                framesJson.put(frameObj);
            }
            obj.put("frames", framesJson);
            array.put(obj);
        }

        File indexFile = new File(context.getFilesDir(), USER_MESSAGES_INDEX);
        try (FileOutputStream out = new FileOutputStream(indexFile)) {
            out.write(array.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private File userMessageDir(String id) {
        return new File(new File(context.getFilesDir(), USER_MESSAGES_DIR), id);
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private static String readAssetAsString(AssetManager assetManager, String path) throws IOException {
        return new String(readAssetAsBytes(assetManager, path), StandardCharsets.UTF_8);
    }

    private static byte[] readAssetAsBytes(AssetManager assetManager, String path) throws IOException {
        try (InputStream in = assetManager.open(path)) {
            return readAllBytes(in);
        }
    }

    private static String readFileAsString(File file) throws IOException {
        return new String(readFileAsBytes(file), StandardCharsets.UTF_8);
    }

    private static byte[] readFileAsBytes(File file) throws IOException {
        try (InputStream in = new java.io.FileInputStream(file)) {
            return readAllBytes(in);
        }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
