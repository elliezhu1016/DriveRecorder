package com.sentaroh.android.DriveRecorder.Log;

import static com.sentaroh.android.DriveRecorder.Constants.*;

public class LogCommon {
	public static final int LOG_LIMIT_SIZE=1024*1024*2;
	public static final int LOG_FILE_BUFFER_SIZE=4096*64;
	public static final String LOG_PKG_NAME=PACKAGE_NAME;
	public static final String LOG_DEFAULT_PREFS_FILENAME=DEFAULT_PREFS_FILENAME;

	public static final String BROADCAST_LOG_SEND=LOG_PKG_NAME+".ACTION_LOG_SEND";
	public static final String BROADCAST_LOG_RESET=LOG_PKG_NAME+".ACTION_LOG_RESET";
	public static final String BROADCAST_LOG_ROTATE=LOG_PKG_NAME+".ACTION_LOG_ROTATE";
	public static final String BROADCAST_LOG_DELETE=LOG_PKG_NAME+".ACTION_LOG_DELETE";
	public static final String BROADCAST_LOG_FLUSH=LOG_PKG_NAME+".ACTION_LOG_FLUSH";

}
