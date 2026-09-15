package com.trancong.dexworkspacetouch.shizukuprobe;
interface IProbeService {
    String runtimeInfo();
    String findTask(String packageName);
    String workArea(int displayId);
    String resizeVerified(String packageName, int taskId, int displayId, int left, int top, int right, int bottom, String correlationId);
    void destroy();
}
