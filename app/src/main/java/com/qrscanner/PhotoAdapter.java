package com.qrscanner;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.ViewHolder> {

    public interface OnPhotoClick {
        void onPhotoClick(File file, int position);
    }

    private final List<File> files = new ArrayList<>();
    private final OnPhotoClick clickListener;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final SimpleDateFormat fmt =
        new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    public PhotoAdapter(OnPhotoClick clickListener) {
        this.clickListener = clickListener;
    }

    public void submit(List<File> newFiles) {
        files.clear();
        files.addAll(newFiles);
        notifyDataSetChanged();
    }

    public File getFile(int position) {
        return position >= 0 && position < files.size() ? files.get(position) : null;
    }

    public int positionOf(String path) {
        for (int i = 0; i < files.size(); i++) {
            if (files.get(i).getAbsolutePath().equals(path)) return i;
        }
        return -1;
    }

    public void removeAt(int position) {
        if (position >= 0 && position < files.size()) {
            files.remove(position);
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_photo, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final File file = files.get(position);
        final String path = file.getAbsolutePath();

        holder.tvTime.setText(fmt.format(new Date(file.lastModified())));
        holder.ivPhoto.setImageDrawable(null);
        holder.ivPhoto.setTag(path);

        executor.execute(() -> {
            Bitmap bmp = ScanPhotoStore.decodeSampled(path, 240, 240);
            if (bmp == null) return;
            holder.ivPhoto.post(() -> {
                // 防止 RecyclerView 复用时把旧图贴到新 item 上
                if (path.equals(holder.ivPhoto.getTag())) {
                    holder.ivPhoto.setImageBitmap(bmp);
                }
            });
        });

        holder.itemView.setOnClickListener(v -> clickListener.onPhotoClick(file, position));
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPhoto;
        TextView tvTime;

        ViewHolder(View itemView) {
            super(itemView);
            ivPhoto = itemView.findViewById(R.id.ivPhoto);
            tvTime = itemView.findViewById(R.id.tvTime);
        }
    }
}
