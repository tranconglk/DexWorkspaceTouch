package com.trancong.dexworkspacetouch.workspace.close.remote;
import com.trancong.dexworkspacetouch.workspace.close.remote.ICloseSessionGate;
interface IPackageCloseService {
    int getUid() = 0;
    String openSession(String requestJson, long deadline, ICloseSessionGate gate) = 1;
    String enumerate(String operationId) = 2;
    String removeVerified(String operationId, String expectedJson) = 3;
    void destroy() = 16777114;
}
