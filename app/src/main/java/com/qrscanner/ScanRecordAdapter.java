package com.qrscanner;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ScanRecordAdapter extends RecyclerView.Adapter<ScanRecordAdapter.ViewHolder> {

    private final List<ScanRecord> records;
    private final OnDeleteListener deleteListener;
    private final OnEditRemarkListener editListener;

    public interface OnDeleteListener {
        void onDelete(int position);
    }

    public interface OnEditRemarkListener {
        void onEdit(int position);
    }

    public ScanRecordAdapter(List<ScanRecord> records,
                             OnDeleteListener deleteListener,
                             OnEditRemarkListener editListener) {
        this.records = records;
        this.deleteListener = deleteListener;
        this.editListener = editListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_scan_record, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScanRecord record = records.get(position);
        holder.tvSeq.setText(String.valueOf(record.getSeq()));
        holder.tvContent.setText(record.getContent());
        holder.tvTime.setText(record.getTime());

        if (record.getRemark() != null && !record.getRemark().isEmpty()) {
            holder.tvRemark.setVisibility(View.VISIBLE);
            holder.tvRemark.setText("备注: " + record.getRemark());
        } else {
            holder.tvRemark.setVisibility(View.GONE);
        }

        holder.btnDelete.setOnClickListener(v -> deleteListener.onDelete(holder.getAdapterPosition()));
        holder.btnEdit.setOnClickListener(v -> editListener.onEdit(holder.getAdapterPosition()));
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSeq, tvContent, tvTime, tvRemark;
        ImageButton btnDelete, btnEdit;

        ViewHolder(View itemView) {
            super(itemView);
            tvSeq = itemView.findViewById(R.id.tvSeq);
            tvContent = itemView.findViewById(R.id.tvContent);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvRemark = itemView.findViewById(R.id.tvRemark);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            btnEdit = itemView.findViewById(R.id.btnEdit);
        }
    }
}
