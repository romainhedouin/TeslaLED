package com.teslaowls.teslaled;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.teslaowls.teslaled.model.PanelMessage;
import com.teslaowls.teslaled.ppm.PpmBitmap;
import com.teslaowls.teslaled.storage.MessageStore;

import java.util.LinkedHashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_BLUETOOTH_CONNECT = 1;

    private static final Map<String, String> CATEGORY_CHIPS = new LinkedHashMap<String, String>() {{
        put("All", null);
        put("Greetings", PanelMessage.CATEGORY_GREETINGS);
        put("Courtesy", PanelMessage.CATEGORY_COURTESY);
        put("Traffic-safety", PanelMessage.CATEGORY_TRAFFIC_SAFETY);
        put("Fun", PanelMessage.CATEGORY_FUN);
        put("Custom", PanelMessage.CATEGORY_CUSTOM);
    }};

    private static final Map<String, String> LANGUAGE_CHIPS = new LinkedHashMap<String, String>() {{
        put("All", null);
        put("FR", PanelMessage.LANGUAGE_FR);
        put("EN", PanelMessage.LANGUAGE_EN);
    }};

    BluetoothClient bluetoothClient = new BluetoothClient(this);
    Settings settings;
    MessageSender messageSender;
    MessageStore messageStore;
    MessageAdapter messageAdapter;

    private String selectedCategory = null;
    private String selectedLanguage = null;

    private ImageView nowShowingThumbnail;
    private TextView nowShowingLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_CONNECT);
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

        nowShowingThumbnail = findViewById(R.id.now_showing_thumbnail);
        nowShowingLabel = findViewById(R.id.now_showing_label);
        messageSender.setListener(new MessageSender.Listener() {
            @Override
            public void onMessageStarted(PanelMessage message) {
                nowShowingThumbnail.setImageBitmap(PpmBitmap.toBitmap(message.frames.get(0).ppmBytes, 4));
                nowShowingLabel.setText(message.label);
            }

            @Override
            public void onMessageFinished(PanelMessage message) {
                nowShowingThumbnail.setImageDrawable(null);
                nowShowingLabel.setText("Nothing showing");
            }
        });

        RecyclerView recyclerView = findViewById(R.id.message_grid);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        messageAdapter = new MessageAdapter(messageStore.getFiltered(null, null), this::sendMessage);
        recyclerView.setAdapter(messageAdapter);

        setUpChipGroup(R.id.category_filter, CATEGORY_CHIPS, value -> {
            selectedCategory = value;
            refreshMessageList();
        });
        setUpChipGroup(R.id.language_filter, LANGUAGE_CHIPS, value -> {
            selectedLanguage = value;
            refreshMessageList();
        });

        FloatingActionButton fab = findViewById(R.id.create_message_fab);
        fab.setOnClickListener(v -> startActivity(new Intent(this, CreateMessageActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // A message may have been created/edited/deleted in CreateMessageActivity.
        try {
            messageStore.load();
        } catch (Exception e) {
            System.out.println("[-] Failed to reload messages.");
            e.printStackTrace();
        }
        refreshMessageList();
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
        messageAdapter.setMessages(messageStore.getFiltered(selectedCategory, selectedLanguage));
    }

    private void sendMessage(PanelMessage message) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bluetooth permission not granted.", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean success = messageSender.send(message);
        if (!success) {
            Toast.makeText(this, "Failed to send message.", Toast.LENGTH_SHORT).show();
        }
    }
}
