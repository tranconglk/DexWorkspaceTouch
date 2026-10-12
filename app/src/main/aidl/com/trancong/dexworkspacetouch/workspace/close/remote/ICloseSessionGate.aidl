package com.trancong.dexworkspacetouch.workspace.close.remote;
interface ICloseSessionGate {
    boolean isAllowed(String operationId, long deadline);
}
