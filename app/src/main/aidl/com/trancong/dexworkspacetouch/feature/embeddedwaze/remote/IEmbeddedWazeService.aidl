package com.trancong.dexworkspacetouch.feature.embeddedwaze.remote;
import android.view.Surface;
import android.os.Bundle;
interface IEmbeddedWazeService{int getUid() = 0;Bundle startSession(in Surface surface) = 1;Bundle sendTouch(int action,float x,float y,float pressure,long eventTimeNanos) = 2;Bundle stopSession() = 3;void destroy()=16777114;}
