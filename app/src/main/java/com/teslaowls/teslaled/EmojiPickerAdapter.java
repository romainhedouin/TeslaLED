package com.teslaowls.teslaled;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.teslaowls.teslaled.ppm.PpmBitmap;
import com.teslaowls.teslaled.render.EmojiRenderer;

import java.util.List;

/**
 * Grid of emoji thumbnails, each rendered through the same EmojiRenderer ->
 * PpmBitmap path used to send/preview any other panel message - but always
 * at normal (1.0) gamma, regardless of the adjustable panel-brightness
 * setting: this grid is meant to show what the emoji IS, not a live preview
 * of that adjustment. sendEmoji() (in MainActivity) is what applies the
 * actual setting to what gets sent.
 */
public class EmojiPickerAdapter extends RecyclerView.Adapter<EmojiPickerAdapter.ViewHolder> {

    private static final float NEUTRAL_GAMMA = 1.0f;

    public interface OnEmojiClickListener {
        void onEmojiClick(String emoji);
    }

    private final List<String> emojis;
    private final OnEmojiClickListener listener;

    public EmojiPickerAdapter(List<String> emojis, OnEmojiClickListener listener) {
        this.emojis = emojis;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_emoji, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String emoji = emojis.get(position);
        byte[] ppm = EmojiRenderer.render(emoji, NEUTRAL_GAMMA);
        Bitmap bitmap = PpmBitmap.toBitmap(ppm, 3);
        holder.thumbnail.setImageBitmap(bitmap);
        holder.itemView.setOnClickListener(v -> listener.onEmojiClick(emoji));
    }

    @Override
    public int getItemCount() {
        return emojis.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView thumbnail;

        ViewHolder(View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.emoji_thumbnail);
        }
    }
}
