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
    Bundle getOperationCapabilities(String path) = 8;
    void renameExistingItem(String path, String newName) = 9;
    void deleteExistingItem(String path) = 10;
    ParcelFileDescriptor openNewFileForWrite(String parentPath, String name) = 11;
    void copyExistingItem(String sourcePath, String destinationParentPath, String destinationName) = 12;

    void destroy() = 16777114;
}
