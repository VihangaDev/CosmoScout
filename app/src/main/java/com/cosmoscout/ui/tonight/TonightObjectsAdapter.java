package com.cosmoscout.ui.tonight;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cosmoscout.databinding.ItemTonightObjectBinding;

import java.util.ArrayList;
import java.util.List;

final class TonightObjectsAdapter extends RecyclerView.Adapter<TonightObjectsAdapter.ObjectViewHolder>{
    private final List<TonightObjectItem> items = new ArrayList<>();
    @NonNull
    @Override

    public ObjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent,int viewType){
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemTonightObjectBinding binding = ItemTonightObjectBinding.inflate(inflater,parent,false);
        return new ObjectViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ObjectViewHolder holder,int position)
    {
        TonightObjectItem item = items.get(position);
        boolean showDivider = position < items.size() -1;
        holder.bind(item, showDivider);
    }

    @Override
    public int getItemCount(){return items.size();}

    public void submitList(@NonNull List<TonightObjectItem> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    static final class ObjectViewHolder extends RecyclerView.ViewHolder {
        private final ItemTonightObjectBinding binding;

        ObjectViewHolder(@NonNull ItemTonightObjectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull TonightObjectItem item, boolean showDivider) {
            binding.setItem(item);
            binding.setShowDivider(showDivider);
            binding.executePendingBindings();
        }
    }
}
