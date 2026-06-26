package com.quata.documentreader.widgets.dynamicgrid;


public interface DynamicGridAdapterInterface {

    void reorderItems(int originalPosition, int newPosition);

    int getColumnCount();

    boolean canReorder(int position);

}
