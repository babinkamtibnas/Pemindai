package com.pemindai;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
    private List<DatabaseHelper.ScanItem> historyList;

    public HistoryAdapter(List<DatabaseHelper.ScanItem> historyList) {
        this.historyList = historyList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DatabaseHelper.ScanItem item = historyList.get(position);
        holder.tvText.setText(item.text);
        holder.tvDate.setText(item.timestamp);
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    public void updateData(List<DatabaseHelper.ScanItem> newList) {
        this.historyList = newList;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvText, tvDate;

        ViewHolder(View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tv_history_text);
            tvDate = itemView.findViewById(R.id.tv_history_date);
        }
    }
}
