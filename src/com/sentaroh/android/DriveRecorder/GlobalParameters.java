package com.sentaroh.android.DriveRecorder;


import static com.sentaroh.android.DriveRecorder.Constants.*;

import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

@SuppressWarnings("unused")
public class GlobalParameters extends Application{
	public boolean settingsDebugEnabled=true;
	public int settingsDebugLevel=1;
	public boolean settingsExitCleanly=true;

	public boolean settingsLogEnabled=false;
	
	public boolean isRecording=false;
	
	public boolean screenIsLocked=false;
	
	public String logFileDir=null;
//	public String settingsLogTag="BluetoothWidget";
	@SuppressLint("SdCardPath")
	public String settingsLogFileDir="/mnt/sdcard/DriveRecorder/";
	public String settingsLogFileName="DriveRecorder_log";
	public int settingsLogFileBufferSize=1024*32;
	public int settingsLogMaxFileCount=10;
	
	public boolean settingsDeviceOrientationPortrait=false;
	public boolean settingsRecordSound=true;

	public int settingsRecordingDuration=3;
	public int settingsMaxVideoKeepGeneration=100;
	
	public int settingsVideoBitRate=1024*1024;

	public String settingsRecordVideoQuality=RECORD_VIDEO_QUALITY_LOW;//1280_720;//720_480;
	
	public boolean settingsVideoPlaybackKeepAspectRatio=false;
	
//	public int settingsVideoFrameRate=30;
    
	public String videoRecordDir="", videoFileNamePrefix="drive_record_", videoArchiveDir="";;
	public String currentRecordedFileName="";
	
	public int settingHeartBeatIntervalTime=1000*60*1;

	public void loadSettingParms(Context c) {
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
		String vf=Environment.getExternalStorageDirectory().toString()+"/DriveRecorder/videos/";
		
		videoArchiveDir=Environment.getExternalStorageDirectory().toString()+"/DriveRecorder/archive/";
		
		videoRecordDir=
				prefs.getString(c.getString(R.string.settings_video_folder),vf);
		settingsDebugEnabled=
				prefs.getBoolean(c.getString(R.string.settings_debug_enable),false);
		if (settingsDebugEnabled) {
			settingsDebugLevel=1;
			settingsLogEnabled=true;
		} else {
			settingsDebugLevel=0;
			settingsLogEnabled=false;
		}
		
		settingsVideoPlaybackKeepAspectRatio=
				prefs.getBoolean(c.getString(R.string.settings_video_playback_keep_aspect_ratio),true);
		
		settingsExitCleanly=
				prefs.getBoolean(c.getString(R.string.settings_exit_cleanly),false);
		settingsRecordingDuration=Integer.parseInt(
				prefs.getString(c.getString(R.string.settings_recording_duration),"3"));
		settingsMaxVideoKeepGeneration=Integer.parseInt(
				prefs.getString(c.getString(R.string.settings_max_video_keep_generation),"100"));
		
		settingsDeviceOrientationPortrait=
				prefs.getBoolean(c.getString(R.string.settings_device_orientation_portrait),false);

		settingsRecordVideoQuality=
				prefs.getString(c.getString(R.string.settings_video_record_quality),RECORD_VIDEO_QUALITY_LOW);

		
	};
	
	public void initSettingParms(Context c) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
		String vf=Environment.getExternalStorageDirectory().toString()+"/DriveRecorder/videos/";
		if (prefs.getString(c.getString(R.string.settings_video_folder),"").equals("")) {
			prefs.edit().putString(c.getString(R.string.settings_video_folder),vf).commit();
			
			prefs.edit().putBoolean(c.getString(R.string.settings_debug_enable),false).commit();
			prefs.edit().putBoolean(c.getString(R.string.settings_exit_cleanly),false).commit();
			
			prefs.edit().putString(c.getString(R.string.settings_recording_duration),"3").commit();
			prefs.edit().putString(c.getString(R.string.settings_max_video_keep_generation),"100").commit();
			prefs.edit().putBoolean(c.getString(R.string.settings_record_sound),true).commit();
		}
	};
}
