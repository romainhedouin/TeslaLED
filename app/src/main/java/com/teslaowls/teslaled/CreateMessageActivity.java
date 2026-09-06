package com.teslaowls.teslaled;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.teslaowls.teslaled.model.Frame;
import com.teslaowls.teslaled.model.PanelMessage;
import com.teslaowls.teslaled.ppm.PpmBitmap;
import com.teslaowls.teslaled.render.PixelFontRenderer;
import com.teslaowls.teslaled.render.TextChunker;
import com.teslaowls.teslaled.storage.MessageStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One screen for creating a custom message: a label/category/language plus a
 * list of "frames" (each just text + a duration). Adding frames to the list
 * *is* the sequence/animation builder - there's no separate concept for it.
 * A frame whose text doesn't fit the panel width is auto-paginated into
 * multiple actual PPM frames at save time (see TextChunker), so a single
 * frame row already covers "long message" without the user doing anything.
 */
public class CreateMessageActivity extends AppCompatActivity {

    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int BACKGROUND_COLOR = 0x000000;
    private static final int DEFAULT_DURATION_MS = 3000;

    private static class FrameRow {
        final View view;
        final EditText textInput;
        final EditText durationInput;
        final ImageView preview;

        FrameRow(View view, EditText textInput, EditText durationInput, ImageView preview) {
            this.view = view;
            this.textInput = textInput;
            this.durationInput = durationInput;
            this.preview = preview;
        }
    }

    private final List<FrameRow> frameRows = new ArrayList<>();
    private PixelFontRenderer renderer;
    private LinearLayout framesContainer;
    private MessageStore messageStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_message);

        messageStore = new MessageStore(this);
        try {
            messageStore.load();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            renderer = new PixelFontRenderer(getAssets(), "fonts/9x18B.bdf");
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to load font.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Spinner categorySpinner = findViewById(R.id.category_spinner);
        categorySpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                Arrays.asList(PanelMessage.CATEGORY_CUSTOM, PanelMessage.CATEGORY_GREETINGS,
                        PanelMessage.CATEGORY_COURTESY, PanelMessage.CATEGORY_TRAFFIC_SAFETY, PanelMessage.CATEGORY_FUN)));

        Spinner languageSpinner = findViewById(R.id.language_spinner);
        languageSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                Arrays.asList("No language (universal)", "fr", "en")));

        framesContainer = findViewById(R.id.frames_container);
        addFrameRow();

        findViewById(R.id.add_frame_button).setOnClickListener(v -> addFrameRow());

        findViewById(R.id.save_button).setOnClickListener(v -> {
            String label = ((EditText) findViewById(R.id.message_label)).getText().toString().trim();
            if (label.isEmpty()) {
                Toast.makeText(this, "Enter a label.", Toast.LENGTH_SHORT).show();
                return;
            }

            List<Frame> frames = buildFrames();
            if (frames.isEmpty()) {
                Toast.makeText(this, "Enter at least one frame's text.", Toast.LENGTH_SHORT).show();
                return;
            }

            String category = (String) categorySpinner.getSelectedItem();
            String languageChoice = (String) languageSpinner.getSelectedItem();
            String language = "No language (universal)".equals(languageChoice) ? PanelMessage.LANGUAGE_NONE : languageChoice;

            try {
                messageStore.createUserMessage(label, category, language, frames);
                finish();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to save message.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private List<Frame> buildFrames() {
        List<Frame> frames = new ArrayList<>();
        for (FrameRow row : frameRows) {
            String text = row.textInput.getText().toString().trim();
            if (text.isEmpty()) {
                continue;
            }
            int durationMs = parseDurationOrDefault(row.durationInput.getText().toString());
            for (String chunk : TextChunker.chunk(text, renderer)) {
                frames.add(new Frame(renderer.renderText(chunk, TEXT_COLOR, BACKGROUND_COLOR), durationMs));
            }
        }
        return frames;
    }

    private int parseDurationOrDefault(String text) {
        try {
            int value = Integer.parseInt(text.trim());
            return value > 0 ? value : DEFAULT_DURATION_MS;
        } catch (NumberFormatException e) {
            return DEFAULT_DURATION_MS;
        }
    }

    private void addFrameRow() {
        View view = LayoutInflater.from(this).inflate(R.layout.item_frame_editor, framesContainer, false);
        EditText textInput = view.findViewById(R.id.frame_text);
        EditText durationInput = view.findViewById(R.id.frame_duration);
        ImageView preview = view.findViewById(R.id.frame_preview);
        Button removeButton = view.findViewById(R.id.frame_remove);

        FrameRow row = new FrameRow(view, textInput, durationInput, preview);
        frameRows.add(row);
        framesContainer.addView(view);

        textInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePreview(row);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        removeButton.setOnClickListener(v -> {
            framesContainer.removeView(row.view);
            frameRows.remove(row);
        });
    }

    private void updatePreview(FrameRow row) {
        String text = row.textInput.getText().toString().trim();
        if (text.isEmpty()) {
            row.preview.setImageDrawable(null);
            return;
        }
        // Preview just the first chunk - full pagination is only meaningful
        // once actually sent to the panel.
        List<String> chunks = TextChunker.chunk(text, renderer);
        byte[] ppm = renderer.renderText(chunks.get(0), TEXT_COLOR, BACKGROUND_COLOR);
        row.preview.setImageBitmap(PpmBitmap.toBitmap(ppm, 2));
    }
}
