package com.trancong.dexworkspacetouch.feature.embeddedapp.remote;
import android.view.Surface;
import android.os.Bundle;
interface IEmbeddedAppService {
    int getUid() = 0;
    Bundle startSession(in Surface surface, String packageName, String componentName, int width, int height, int densityDpi) = 1;
    Bundle sendTouch(int action, float x, float y, float pressure, long eventTimeNanos) = 2;
    Bundle stopSession() = 3;
    void destroy() = 16777114;
}