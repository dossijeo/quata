package com.quata.documentreader.adapters_All.viewholder;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import   com.quata.documentreader.R;
import com.quata.documentreader.dataType.Cell;
import   com.quata.documentreader.widgets.tableview.adapter.recyclerview.holder.AbstractViewHolder;

public class TableCellViewHolder extends AbstractViewHolder {
    @NonNull
    private final TextView cell_textview;
    @NonNull
    private final LinearLayout cell_container;

    public TableCellViewHolder(@NonNull View itemView) {
        super(itemView);
        cell_textview = itemView.findViewById(R.id.cell_data);
        cell_container = itemView.findViewById(R.id.cell_container);
    }

    public void setCell(@Nullable Cell cell) {
        cell_textview.setText(String.valueOf(cell.getData()));
        cell_container.getLayoutParams().width = LinearLayout.LayoutParams.WRAP_CONTENT;
        cell_textview.requestLayout();
    }
}
