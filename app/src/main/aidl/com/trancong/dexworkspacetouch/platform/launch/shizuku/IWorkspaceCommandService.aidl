package com.trancong.dexworkspacetouch.platform.launch.shizuku;
import android.os.ParcelFileDescriptor;
// Private to Workspace Control. Both ends validate the exact command whitelist.
interface IWorkspaceCommandService {
    int getUid() = 0;
    ParcelFileDescriptor execute(in String[] arguments, long deadlineElapsedRealtimeMs, String requestId) = 1;
    void cancel(String requestId) = 2;
    void destroy() = 16777114;
}
