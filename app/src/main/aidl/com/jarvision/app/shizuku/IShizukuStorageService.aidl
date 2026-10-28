package com.jarvision.app.shizuku;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

interface IShizukuStorageService {
    Bundle[] listDirectory(String path) = 1;
    ParcelFileDescriptor openFileReadOnly(String path) = 2;
    int getUid() = 3;
    byte[] loadFolderThumbnail(String path) = 4;
    void createDirectory(String parentPath, String name) = 5;
    void createEmptyFile(String parentPath, String name) = 6;
    void createEmptyZip(String parentPath, String name) = 7;

    void destroy() = 16777114;
}
