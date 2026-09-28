package com.trancong.dexworkspacetouch.feature.embeddedapp.remote;
import android.view.Surface;
import android.os.Bundle;
interface IEmbeddedAppService {
    int getUid() = 0;
    Bundle startSession(String sessionId, in Surface surface, String packageName, String componentName, int width, int height, int densityDpi) = 1;
    Bundle sendTouch(String sessionId, int action, float x, float y, float pressure, long eventTimeNanos) = 2;
    Bundle stopSession(String sessionId) = 3;
    Bundle getSessionState(String sessionId) = 4;
    Bundle getServiceState() = 5;
    void destroy() = 16777114;
}
