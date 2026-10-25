package com.jarvision.app.shizuku;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

interface IShizukuStorageService {
    Bundle[] listDirectory(String path);
    ParcelFileDescriptor openFileReadOnly(String path);
    int getUid();

    void destroy() = 16777114;
}
