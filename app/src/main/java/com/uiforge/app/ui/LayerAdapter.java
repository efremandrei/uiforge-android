package com.uiforge.app.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.uiforge.app.R;
import com.uiforge.app.databinding.ItemLayerBinding;
import com.uiforge.app.model.UiComponent;

import java.util.List;

public class LayerAdapter extends RecyclerView.Adapter<LayerAdapter.LayerViewHolder> {
    public interface LayerActionListener {
        void onSelect(int position);
        void onDelete(int position);
    }

    private final List<UiComponent> items;
    private final LayerActionListener listener;
    private int selectedPosition = -1;

    public LayerAdapter(List<UiComponent> items, LayerActionListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void setSelectedPosition(int selectedPosition) {
        this.selectedPosition = selectedPosition;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LayerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemLayerBinding binding = ItemLayerBinding.inflate(inflater, parent, false);
        return new LayerViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull LayerViewHolder holder, int position) {
        UiComponent item = items.get(position);
        boolean isSelected = position == selectedPosition;
        holder.binding.layerTitle.setText(item.getTitle());
        holder.binding.layerSubtitle.setText(item.getSummary());
        holder.binding.layerCard.setStrokeWidth(isSelected ? 4 : 1);
        holder.binding.layerCard.setStrokeColor(holder.itemView.getResources().getColor(
                isSelected ? R.color.accent_cobalt : R.color.stroke_soft,
                holder.itemView.getContext().getTheme()));
        holder.binding.layerCard.setCardBackgroundColor(holder.itemView.getResources().getColor(
                isSelected ? R.color.surface_selected : R.color.surface_primary,
                holder.itemView.getContext().getTheme()));
        holder.binding.getRoot().setOnClickListener(v -> listener.onSelect(holder.getBindingAdapterPosition()));
        holder.binding.deleteButton.setOnClickListener(v -> listener.onDelete(holder.getBindingAdapterPosition()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class LayerViewHolder extends RecyclerView.ViewHolder {
        private final ItemLayerBinding binding;

        LayerViewHolder(ItemLayerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
