package com.teslaowls.teslaled;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.teslaowls.teslaled.data.LocationSpeedProvider;
import com.teslaowls.teslaled.data.SpeedDataSource;
import com.teslaowls.teslaled.data.TimeDataSource;
import com.teslaowls.teslaled.model.EasterEggDataSource;
import com.teslaowls.teslaled.model.Frame;
import com.teslaowls.teslaled.model.PanelMessage;
import com.teslaowls.teslaled.ppm.PpmBitmap;
import com.teslaowls.teslaled.render.EmojiRenderer;
import com.teslaowls.teslaled.render.PixelFontRenderer;
import com.teslaowls.teslaled.storage.MessageStore;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_BLUETOOTH_CONNECT = 1;
    private static final int REQUEST_LOCATION = 2;

    private static final Map<String, String> CATEGORY_CHIPS = new LinkedHashMap<String, String>() {{
        put("All", null);
        put("Greetings", PanelMessage.CATEGORY_GREETINGS);
        put("Courtesy", PanelMessage.CATEGORY_COURTESY);
        put("Traffic-safety", PanelMessage.CATEGORY_TRAFFIC_SAFETY);
        put("Fun", PanelMessage.CATEGORY_FUN);
        put("Data", PanelMessage.CATEGORY_DATA);
        put("Custom", PanelMessage.CATEGORY_CUSTOM);
        put("Emoji", PanelMessage.CATEGORY_EMOJI);
    }};

    // Menu item titles shown for the current language filter, in cycle order.
    private static final Map<String, String> LANGUAGE_OPTIONS = new LinkedHashMap<String, String>() {{
        put("ALL", null);
        put("FR", PanelMessage.LANGUAGE_FR);
        put("EN", PanelMessage.LANGUAGE_EN);
    }};

    private static final int[] BRIGHTNESS_OPTIONS = {5, 25, 50, 75, 90, 100};
    private static final long CONNECTION_POLL_MS = 3000;
    private static final float EMOJI_GAMMA_STEP = 0.10f;

    // Curated rather than a full system emoji list - this is a car dashboard,
    // not a keyboard, so it stays to a scrollable but bounded set of the most
    // popular/useful ones. Anything else is reachable via emojiKeyboardInput.
    // Weighted toward faces/hands/hearts per request; no animals except
    // monkeys, no food.
    private static final List<String> EMOJIS = Arrays.asList(
            // Faces (110)
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃", "🫠", "😉", "😊", "😇", "🥰", "😍",
            "🤩", "😘", "😗", "😚", "😙", "🥲", "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🫢", "🫣",
            "🤫", "🤔", "🫡", "🤐", "🤨", "😐", "😑", "😶", "🫥", "😏", "😒", "🙄", "😬", "🤥", "😌", "😔",
            "😪", "🤤", "😴", "😷", "🤒", "🤕", "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "😵‍💫", "🤯", "🤠",
            "🥳", "🥸", "😎", "🤓", "🧐", "😕", "🫤", "😟", "🙁", "😮", "😯", "😲", "😳", "🥺", "🥹", "😦",
            "😧", "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤", "😡",
            "😠", "🤬", "😈", "👿", "💀", "☠️", "💩", "🤡", "👹", "👺", "👻", "👽", "👾", "🤖",
            // Hands (30)
            "👍", "👎", "👊", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳", "💪", "🖕", "✌️", "🤞",
            "🫰", "🤟", "🤘", "👌", "🤌", "🤏", "👈", "👉", "👆", "👇", "☝️", "✋", "🖖", "👋",
            // Hearts (21)
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❤️‍🔥", "❤️‍🩹", "💕", "💞", "💓", "💗",
            "💖", "💘", "💝", "💟", "❣️",
            // Weather (8)
            "☀️", "⛅", "☁️", "🌧️", "⛈️", "⚡", "❄️", "🌈",
            // Symbols (18)
            "⭐", "🔥", "💯", "⚠️", "✅", "❌", "💤", "🎉", "❗", "❓", "💥", "🔔", "🚫", "✨", "🎶", "🆘",
            "🔊", "🏆",
            // Transport (9)
            "🚗", "🚙", "🚓", "🚨", "🚕", "🚲", "🏍️", "🚀", "✈️",
            // Monkeys only, no other animals (4)
            "🐵", "🙈", "🙉", "🙊");

    // Hidden feature: long-pressing the Emoji chip cycles through these
    // until Stop is pressed, via the same LiveDataSource loop Data's
    // Time/Speed sources use.
    private static final List<String> EASTER_EGG_EMOJIS = Arrays.asList(
            "😀", "😂", "😍", "😜", "😎", "😈", "🤌",
            "❤️", "💚", "💜", "❤️‍🔥", "🔥", "⚠️", "🚨", "🏍️", "🐵");

    BluetoothClient bluetoothClient = new BluetoothClient(this);
    Settings settings;
    MessageSender messageSender;
    MessageStore messageStore;
    MessageAdapter messageAdapter;
    LocationManager locationManager;
    LocationSpeedProvider speedProvider;
    List<PanelMessage> liveMessages;

    private String selectedCategory = null;
    private String selectedLanguageLabel = "ALL";
    private MenuItem languageMenuItem;
    private MenuItem brightnessMenuItem;

    private ImageView nowShowingThumbnail;
    private TextView nowShowingLabel;
    private Button stopButton;
    private RecyclerView messageGrid;
    private FloatingActionButton createMessageFab;
    private View emojiPanel;
    private TextView emojiDurationLabel;
    private TextView emojiGammaLabel;
    private EmojiPickerAdapter emojiPickerAdapter;
    private int emojiDurationSeconds;
    private float emojiGamma;
    private boolean isDisplaying = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable connectionPoll = new Runnable() {
        @Override
        public void run() {
            updateIdleLabel();
            mainHandler.postDelayed(this, CONNECTION_POLL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_CONNECT);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
        }

        settings = new Settings(this);
        messageSender = new MessageSender(bluetoothClient, settings);
        messageStore = new MessageStore(this);
        try {
            messageStore.load();
        } catch (Exception e) {
            System.out.println("[-] Failed to load messages.");
            e.printStackTrace();
        }

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        speedProvider = new LocationSpeedProvider();
        liveMessages = buildLiveMessages();

        nowShowingThumbnail = findViewById(R.id.now_showing_thumbnail);
        nowShowingLabel = findViewById(R.id.now_showing_label);
        stopButton = findViewById(R.id.stop_button);
        stopButton.setOnClickListener(v -> messageSender.stop());
        messageSender.setListener(new MessageSender.Listener() {
            @Override
            public void onMessageStarted(PanelMessage message) {
                isDisplaying = true;
                nowShowingLabel.setText("Displaying...");
                stopButton.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFrameUpdated(PanelMessage message, byte[] ppmBytes) {
                nowShowingThumbnail.setImageBitmap(PpmBitmap.toBitmap(ppmBytes, 4));
            }

            @Override
            public void onMessageFinished(PanelMessage message) {
                isDisplaying = false;
                nowShowingThumbnail.setImageDrawable(null);
                stopButton.setVisibility(View.GONE);
                updateIdleLabel();
            }

            @Override
            public void onSendFailed(PanelMessage message, String reason) {
                isDisplaying = false;
                updateIdleLabel();
                Toast.makeText(MainActivity.this, "ERROR: " + reason, Toast.LENGTH_LONG).show();
            }
        });

        messageGrid = findViewById(R.id.message_grid);
        messageGrid.setLayoutManager(new GridLayoutManager(this, 2));
        messageAdapter = new MessageAdapter(messageStore.getFiltered(null, null), this::sendMessage, this::confirmDeleteMessage);
        messageGrid.setAdapter(messageAdapter);

        setUpEmojiPanel();

        createMessageFab = findViewById(R.id.create_message_fab);
        createMessageFab.setOnClickListener(v -> startActivity(new Intent(this, CreateMessageActivity.class)));

        setUpChipGroup(R.id.category_filter, CATEGORY_CHIPS, value -> {
            selectedCategory = value;
            boolean isEmoji = PanelMessage.CATEGORY_EMOJI.equals(value);
            emojiPanel.setVisibility(isEmoji ? View.VISIBLE : View.GONE);
            messageGrid.setVisibility(isEmoji ? View.GONE : View.VISIBLE);
            // Doesn't apply to Emoji (nothing to save there), and it would
            // otherwise float on top of the duration/gamma controls.
            createMessageFab.setVisibility(isEmoji ? View.GONE : View.VISIBLE);
            if (!isEmoji) {
                refreshMessageList();
            }
        });

        // Hidden feature: long-pressing the Emoji chip (rather than just
        // tapping it) starts the cycling easter egg instead of switching
        // categories - returning true from the long-click listener consumes
        // the gesture so the chip doesn't also toggle checked/selected.
        ChipGroup categoryChipGroup = findViewById(R.id.category_filter);
        for (int i = 0; i < categoryChipGroup.getChildCount(); i++) {
            View child = categoryChipGroup.getChildAt(i);
            if (child instanceof Chip && "Emoji".contentEquals(((Chip) child).getText())) {
                child.setOnLongClickListener(v -> {
                    startEasterEgg();
                    return true;
                });
            }
        }
    }

    private void startEasterEgg() {
        EasterEggDataSource dataSource = new EasterEggDataSource(EASTER_EGG_EMOJIS, emojiGamma);
        PanelMessage message = new PanelMessage("easter-egg", "Easter Egg", PanelMessage.CATEGORY_EMOJI, PanelMessage.LANGUAGE_NONE,
                new Frame(dataSource.renderFrame(), 0), true, dataSource);
        sendMessage(message);
    }

    private void setUpEmojiPanel() {
        emojiPanel = findViewById(R.id.emoji_panel);
        emojiDurationLabel = findViewById(R.id.emoji_duration_label);
        emojiGammaLabel = findViewById(R.id.emoji_gamma_label);

        emojiDurationSeconds = settings.getEmojiDurationSeconds();
        emojiDurationLabel.setText(emojiDurationSeconds + "s");
        emojiGamma = settings.getEmojiGamma();
        emojiGammaLabel.setText(formatMultiplier(emojiGamma));

        findViewById(R.id.emoji_duration_minus).setOnClickListener(v -> adjustEmojiDuration(-1));
        findViewById(R.id.emoji_duration_plus).setOnClickListener(v -> adjustEmojiDuration(1));
        findViewById(R.id.emoji_gamma_minus).setOnClickListener(v -> adjustEmojiGamma(-EMOJI_GAMMA_STEP));
        findViewById(R.id.emoji_gamma_plus).setOnClickListener(v -> adjustEmojiGamma(EMOJI_GAMMA_STEP));

        // The grid always previews at normal (1.0) gamma regardless of the
        // adjustable setting below - only the actual send to the panel uses
        // emojiGamma. The picker is meant to show what the emoji IS, not a
        // live preview of the panel adjustment.
        RecyclerView emojiGrid = findViewById(R.id.emoji_grid);
        emojiGrid.setLayoutManager(new GridLayoutManager(this, 5));
        emojiPickerAdapter = new EmojiPickerAdapter(EMOJIS, this::sendEmoji);
        emojiGrid.setAdapter(emojiPickerAdapter);

        // Covers anything not in the curated grid above: tapping this box
        // just brings up the keyboard's own emoji picker. A tap there
        // inserts one grapheme (possibly several UTF-16 chars for a ZWJ
        // sequence/skin-tone modifier) in one shot, so send as soon as
        // anything appears, then clear the field so it's ready for the next
        // pick rather than accumulating text.
        EditText emojiKeyboardInput = findViewById(R.id.emoji_keyboard_input);
        emojiKeyboardInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String emoji = s.toString();
                if (emoji.isEmpty()) {
                    return;
                }
                sendEmoji(emoji);
                emojiKeyboardInput.setText("");
            }
        });
    }

    private void adjustEmojiDuration(int delta) {
        emojiDurationSeconds = Math.max(Settings.MIN_EMOJI_DURATION_SECONDS,
                Math.min(Settings.MAX_EMOJI_DURATION_SECONDS, emojiDurationSeconds + delta));
        settings.setEmojiDurationSeconds(emojiDurationSeconds);
        emojiDurationLabel.setText(emojiDurationSeconds + "s");
    }

    private void adjustEmojiGamma(float delta) {
        emojiGamma = Math.max(Settings.MIN_EMOJI_GAMMA,
                Math.min(Settings.MAX_EMOJI_GAMMA, emojiGamma + delta));
        settings.setEmojiGamma(emojiGamma);
        emojiGammaLabel.setText(formatMultiplier(emojiGamma));
    }

    private static String formatMultiplier(float value) {
        return String.format(java.util.Locale.US, "%.2fx", value);
    }

    private void sendEmoji(String emoji) {
        byte[] ppm = EmojiRenderer.render(emoji, emojiGamma);
        PanelMessage message = new PanelMessage("emoji-adhoc", emoji, PanelMessage.CATEGORY_EMOJI, PanelMessage.LANGUAGE_NONE,
                new Frame(ppm, emojiDurationSeconds * 1000), true, null);
        sendMessage(message);
    }

    private List<PanelMessage> buildLiveMessages() {
        List<PanelMessage> result = new ArrayList<>();
        try {
            PixelFontRenderer dataRenderer = new PixelFontRenderer(getAssets(), "fonts/6x13B.bdf");

            TimeDataSource timeSource = new TimeDataSource(dataRenderer, settings);
            result.add(new PanelMessage("data-time", "Time", PanelMessage.CATEGORY_DATA, PanelMessage.LANGUAGE_NONE,
                    new Frame(timeSource.renderFrame(), 0), true, timeSource));

            SpeedDataSource speedSource = new SpeedDataSource(dataRenderer, settings, speedProvider);
            result.add(new PanelMessage("data-speed", "Speed", PanelMessage.CATEGORY_DATA, PanelMessage.LANGUAGE_NONE,
                    new Frame(speedSource.renderFrame(), 0), true, speedSource));
        } catch (Exception e) {
            System.out.println("[-] Failed to build live data messages.");
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        languageMenuItem = menu.findItem(R.id.action_language);
        languageMenuItem.setTitle(selectedLanguageLabel);
        brightnessMenuItem = menu.findItem(R.id.action_brightness);
        brightnessMenuItem.setTitle(settings.getBrightness() + "%");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_language) {
            showLanguageMenu();
            return true;
        }
        if (item.getItemId() == R.id.action_brightness) {
            showBrightnessMenu();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showBrightnessMenu() {
        View anchor = findViewById(R.id.action_brightness);
        PopupMenu popup = new PopupMenu(this, anchor != null ? anchor : findViewById(android.R.id.content));
        for (int value : BRIGHTNESS_OPTIONS) {
            popup.getMenu().add(value + "%");
        }
        popup.setOnMenuItemClickListener(item -> {
            String label = item.getTitle().toString();
            int value = Integer.parseInt(label.substring(0, label.length() - 1));
            settings.setBrightness(value);
            brightnessMenuItem.setTitle(label);
            messageSender.syncBrightness();
            return true;
        });
        popup.show();
    }

    private void updateIdleLabel() {
        if (isDisplaying) {
            return;
        }
        nowShowingLabel.setText(bluetoothClient.isConnected() ? "Connected" : "Not connected");
    }

    private void showLanguageMenu() {
        View anchor = findViewById(R.id.action_language);
        PopupMenu popup = new PopupMenu(this, anchor != null ? anchor : findViewById(android.R.id.content));
        for (String label : LANGUAGE_OPTIONS.keySet()) {
            popup.getMenu().add(label);
        }
        popup.setOnMenuItemClickListener(item -> {
            String label = item.getTitle().toString();
            selectedLanguageLabel = label;
            languageMenuItem.setTitle(label);
            // FR/EN also becomes the language used for dynamically-generated
            // content (Data category labels) - "All" leaves that unchanged,
            // since dynamic labels can't render in "all languages at once".
            String language = LANGUAGE_OPTIONS.get(label);
            if (language != null) {
                settings.setDisplayLanguage(language);
            }
            refreshMessageList();
            return true;
        });
        popup.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startLocationUpdates();
        connectionPoll.run();
        // A message may have been created/edited/deleted in CreateMessageActivity.
        try {
            messageStore.load();
        } catch (Exception e) {
            System.out.println("[-] Failed to reload messages.");
            e.printStackTrace();
        }
        refreshMessageList();
    }

    @Override
    protected void onPause() {
        super.onPause();
        locationManager.removeUpdates(speedProvider);
        mainHandler.removeCallbacks(connectionPoll);
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, speedProvider);
        } catch (IllegalArgumentException e) {
            // GPS_PROVIDER not available on this device; network provider's
            // speed estimate is much less reliable but better than nothing.
            try {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, speedProvider);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private interface ChipSelectionListener {
        void onSelected(String value);
    }

    private void setUpChipGroup(int chipGroupId, Map<String, String> labelToValue, ChipSelectionListener listener) {
        ChipGroup chipGroup = findViewById(chipGroupId);
        boolean first = true;
        for (Map.Entry<String, String> entry : labelToValue.entrySet()) {
            Chip chip = new Chip(this);
            chip.setText(entry.getKey());
            chip.setCheckable(true);
            chip.setChecked(first);
            first = false;
            chip.setOnCheckedChangeListener((button, isChecked) -> {
                if (isChecked) {
                    listener.onSelected(entry.getValue());
                }
            });
            chipGroup.addView(chip);
        }
    }

    private void refreshMessageList() {
        String language = LANGUAGE_OPTIONS.get(selectedLanguageLabel);
        List<PanelMessage> result = new ArrayList<>(messageStore.getFiltered(selectedCategory, language));
        for (PanelMessage live : liveMessages) {
            if (selectedCategory == null || selectedCategory.equals(live.category)) {
                result.add(live);
            }
        }
        messageAdapter.setMessages(result);
    }

    private void sendMessage(PanelMessage message) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bluetooth permission not granted.", Toast.LENGTH_SHORT).show();
            return;
        }
        messageSender.send(message);
    }

    private void confirmDeleteMessage(PanelMessage message) {
        new AlertDialog.Builder(this)
                .setTitle("Delete \"" + message.label + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    try {
                        messageStore.deleteUserMessage(message.id);
                        refreshMessageList();
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Failed to delete message.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
