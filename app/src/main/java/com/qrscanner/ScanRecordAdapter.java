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

    private List<ScanRecord> records;
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

    public void setRecords(List<ScanRecord> records) {
        this.records = records;
        notifyDataSetChanged();
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

        String formatLabel = record.getFormat() != null && !record.getFormat().isEmpty()
            ? holder.itemView.getContext()
                .getString(BarcodeFactory.labelRes(record.getFormat()))
            : "";
        holder.tvContent.setText(record.isBlocked()
            ? holder.itemView.getContext().getString(R.string.records_blocked_prefix,
                record.getContent())
            : record.getContent());
        holder.tvContent.setTextColor(record.isBlocked() ? 0xFFC62828 : 0xFF212121);
        holder.tvTime.setText(formatLabel.isEmpty()
            ? record.getTime()
            : formatLabel + "  |  " + record.getTime());
        holder.itemView.setBackgroundColor(record.isBlocked() ? 0xFFFFEBEE : 0x00000000);

        if (record.getRemark() != null && !record.getRemark().isEmpty()) {
            holder.tvRemark.setVisibility(View.VISIBLE);
            holder.tvRemark.setText(
                holder.itemView.getContext().getString(R.string.records_remark_prefix,
                    record.getRemark()));
        } else {
            holder.tvRemark.setVisibility(View.GONE);
        }

        holder.btnDelete.setOnClickListener(v -> deleteListener.onDelete(holder.getBindingAdapterPosition()));
        holder.btnEdit.setOnClickListener(v -> editListener.onEdit(holder.getBindingAdapterPosition()));
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
