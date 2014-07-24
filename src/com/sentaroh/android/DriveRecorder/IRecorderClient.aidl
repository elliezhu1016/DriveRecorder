package com.sentaroh.android.DriveRecorder;

import com.sentaroh.android.DriveRecorder.IRecorderCallback;

interface IRecorderClient{
	
	void setCallBack(IRecorderCallback callback);
	void removeCallBack(IRecorderCallback callback);

	void aidlStartRecorderThread();
	void aidlStopRecorderThread();
	void aidlStopService();
	boolean aidlIsRecording();
	
	void aidlShowPreview();
	void aidlHidePreview();
	
	void aidlSetActivityStarted(boolean started);
}