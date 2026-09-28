package com.qrscanner;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class AdAdapter extends RecyclerView.Adapter<AdAdapter.VH> {

    public interface OnAction {
        void onEdit(int position);

        void onDelete(int position);
    }

    private final List<AdEntry> items = new ArrayList<>();
    private final OnAction action;
    private int currentHour;

    public AdAdapter(OnAction action) {
        this.action = action;
    }

    public void submit(List<AdEntry> list) {
        items.clear();
        items.addAll(list);
        currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_ad_entry, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        AdEntry e = items.get(position);
        holder.tvRange.setText(e.rangeLabel());
        holder.tvText.setText(e.text);
        if (e.link.isEmpty()) {
            holder.tvLink.setVisibility(View.GONE);
        } else {
            holder.tvLink.setVisibility(View.VISIBLE);
            holder.tvLink.setText(e.link);
        }
        holder.tvActive.setVisibility(e.matches(currentHour) ? View.VISIBLE : View.GONE);
        holder.btnEdit.setOnClickListener(v -> action.onEdit(holder.getBindingAdapterPosition()));
        holder.btnDelete.setOnClickListener(v -> action.onDelete(holder.getBindingAdapterPosition()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvRange, tvText, tvLink, tvActive;
        ImageButton btnEdit, btnDelete;

        VH(View itemView) {
            super(itemView);
            tvRange = itemView.findViewById(R.id.tvRange);
            tvText = itemView.findViewById(R.id.tvText);
            tvLink = itemView.findViewById(R.id.tvLink);
            tvActive = itemView.findViewById(R.id.tvActive);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
