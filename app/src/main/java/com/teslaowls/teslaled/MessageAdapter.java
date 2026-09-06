package com.teslaowls.teslaled;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.teslaowls.teslaled.model.PanelMessage;
import com.teslaowls.teslaled.ppm.PpmBitmap;

import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    public interface OnMessageClickListener {
        void onMessageClick(PanelMessage message);
    }

    public interface OnMessageLongClickListener {
        void onMessageLongClick(PanelMessage message);
    }

    private List<PanelMessage> messages;
    private final OnMessageClickListener listener;
    private final OnMessageLongClickListener longClickListener;

    public MessageAdapter(List<PanelMessage> messages, OnMessageClickListener listener,
                          OnMessageLongClickListener longClickListener) {
        this.messages = messages;
        this.listener = listener;
        this.longClickListener = longClickListener;
    }

    public void setMessages(List<PanelMessage> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PanelMessage message = messages.get(position);
        Bitmap bitmap = PpmBitmap.toBitmap(message.frames.get(0).ppmBytes, 4);
        holder.thumbnail.setImageBitmap(bitmap);
        holder.label.setText(message.label);
        holder.itemView.setOnClickListener(v -> listener.onMessageClick(message));
        holder.itemView.setOnLongClickListener(v -> {
            if (!message.builtIn) {
                longClickListener.onMessageLongClick(message);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView thumbnail;
        final TextView label;

        ViewHolder(View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.message_thumbnail);
            label = itemView.findViewById(R.id.message_label);
        }
    }
}
