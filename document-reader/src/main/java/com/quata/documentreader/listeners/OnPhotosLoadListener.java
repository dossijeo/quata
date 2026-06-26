package com.quata.documentreader.listeners;

import   com.quata.documentreader.dataType.FolderDataType;

import java.util.HashMap;

 public interface OnPhotosLoadListener {
    void onPhotoDataLoadedCompleted(HashMap<String, FolderDataType> hashMap);
}