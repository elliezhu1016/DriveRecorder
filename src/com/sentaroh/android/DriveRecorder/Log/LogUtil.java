package com.sentaroh.android.DriveRecorder.Log;

/*
The MIT License (MIT)
Copyright (c) 2011-2013 Sentaroh

Permission is hereby granted, free of charge, to any person obtaining a copy of 
this software and associated documentation files (the "Software"), to deal 
in the Software without restriction, including without limitation the rights to use,
copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
and to permit persons to whom the Software is furnished to do so, subject to 
the following conditions:

The above copyright notice and this permission notice shall be included in all copies or 
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, 
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR 
PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE 
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, 
TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.

*/

import static com.sentaroh.android.DriveRecorder.Log.LogCommon.*;
import static com.sentaroh.android.DriveRecorder.Constants.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.sentaroh.android.DriveRecorder.GlobalParameters;
import com.sentaroh.android.Utilities.StringUtil;
import com.sentaroh.android.Utilities.MiscUtil;

public final class LogUtil {
	private Context mContext=null;
	
	private GlobalParameters mGlblParms=null;

	private String mLogIdent="";
	
	public LogUtil(Context c, String li, GlobalParameters gp) {
		mContext=c;
		setLogId(li);
		mGlblParms=gp;
	};

	final public void setLogId(String li) {
		mLogIdent=(li+"                 ").substring(0,16)+" ";
	};
	
	final public void resetLogReceiver() {
		resetLogReceiver(mContext);
	};

	final public static void resetLogReceiver(Context c) {
		Intent intent = new Intent(BROADCAST_LOG_RESET);
		c.sendOrderedBroadcast(intent,null);
	};

	final public void flushLog() {
		flushLog(mContext);
	};

	final public static void flushLog(Context c) {
		Intent intent = new Intent(BROADCAST_LOG_FLUSH);
		c.sendOrderedBroadcast(intent,null);
	};

	final public void rotateLogFile() {
		rotateLogFile(mContext);
	};

	final public static void rotateLogFile(Context c) {
		Intent intent = new Intent(BROADCAST_LOG_ROTATE);
		c.sendOrderedBroadcast(intent,null);
	};

    final public void deleteLogFile() {
		Intent intent = new Intent(BROADCAST_LOG_DELETE);
		mContext.sendOrderedBroadcast(intent,null);
	};

	final public void addLogMsg(String cat, String... msg) {
		if (mGlblParms.settingsDebugLevel>0 || mGlblParms.settingsLogEnabled || cat.equals("E")) {
			addLogMsg(mGlblParms, mContext, mLogIdent, cat, msg);
		}
	};
	final public void addDebugMsg(int lvl, String cat, String... msg) {
		if (mGlblParms.settingsDebugLevel>=lvl ) {
			addDebugMsg(mGlblParms, mContext, mLogIdent, lvl, cat, msg);
		}
	};

	final static private void addLogMsg(GlobalParameters gp,
		Context context, String log_id, String cat, String... msg) {
		StringBuilder log_msg=new StringBuilder(512);
		for (int i=0;i<msg.length;i++) log_msg.append(msg[i]);
		if (gp.settingsLogEnabled) {
			Intent intent = new Intent(BROADCAST_LOG_SEND);
			StringBuilder print_msg=new StringBuilder(512)
			.append("M ")
			.append(cat)
			.append(" ")
			.append(StringUtil.convDateTimeTo_YearMonthDayHourMinSecMili(System.currentTimeMillis()))
			.append(" ")
			.append(log_id)
			.append(log_msg.toString());
			intent.putExtra("LOG", print_msg.toString());
			context.sendOrderedBroadcast(intent,null);
		}
		Log.v(APPLICATION_TAG,cat+" "+log_id+log_msg.toString());
	};

	final static private void addDebugMsg(GlobalParameters gp,
		Context context, String log_id, int lvl, String cat, String... msg) {
		StringBuilder print_msg=new StringBuilder(512);
			print_msg.append("D ");
			print_msg.append(cat);
		StringBuilder log_msg=new StringBuilder(512);
		for (int i=0;i<msg.length;i++) log_msg.append(msg[i]);
		if (gp.settingsLogEnabled) {
			Intent intent = new Intent(BROADCAST_LOG_SEND);
			print_msg.append(" ")
			.append(StringUtil.convDateTimeTo_YearMonthDayHourMinSecMili(System.currentTimeMillis()))
			.append(" ")
			.append(log_id)
			.append(log_msg.toString());
			intent.putExtra("LOG", print_msg.toString());
			context.sendOrderedBroadcast(intent,null);
		}
		Log.v(APPLICATION_TAG,cat+" "+log_id+log_msg.toString());
	};

	final public boolean isLogFileExists() {
		boolean result = false;
		File lf = new File(getLogFilePath());
		result=lf.exists();
		if (mGlblParms.settingsDebugLevel>=3) addDebugMsg(3,"I","Log file exists="+result);
		return result;
	};

//	final public boolean getSettingsLogOption() {
//		boolean result = false;
////		result=getPrefMgr().getBoolean(mContext.getString(R.string.settings_main_log_option), false);
//		if (mGlblParms.settingsDebugLevel>=3) addDebugMsg(3,"I","LogOption="+result);
//		return result;
//	};
//
//	final public boolean setSettingsLogOption(boolean enabled) {
//		boolean result = false;
////		getPrefMgr().edit().putBoolean(mContext.getString(R.string.settings_main_log_option), enabled).commit();
//		if (mGlblParms.settingsDebugLevel>=3) addDebugMsg(3,"I","setLLogOption="+result);
//		return result;
//	};
//
	final public String getLogFilePath() {
		return mGlblParms.settingsLogFileDir+mGlblParms.settingsLogFileName+".txt";
	};
	
    final static public ArrayList<LogFileListItem> createLogFileList(GlobalParameters gp) {
    	ArrayList<LogFileListItem> lfm_fl=new ArrayList<LogFileListItem>();
    	
    	File lf=new File(gp.settingsLogFileDir);
    	File[] file_list=lf.listFiles();
    	if (file_list!=null) {
    		for (int i=0;i<file_list.length;i++) {
    			if (file_list[i].getName().startsWith(gp.settingsLogFileName)) {
    				if (file_list[i].getName().startsWith(gp.settingsLogFileName+"_20")) {
        		    	LogFileListItem t=new LogFileListItem();
        		    	t.log_file_name=file_list[i].getName();
        		    	t.log_file_path=file_list[i].getPath();
        		    	t.log_file_size=MiscUtil.convertFileSize(file_list[i].length());
        		    	t.log_file_last_modified=file_list[i].lastModified();
        		    	String lm_date=StringUtil.convDateTimeTo_YearMonthDayHourMinSec(file_list[i].lastModified());
        		    	if (file_list[i].getPath().equals(gp.settingsLogFileDir+gp.settingsLogFileName+".txt"))
        		    		t.isCurrentLogFile=true;
        		    	t.log_file_last_modified_date=lm_date.substring(0,10);
        		    	t.log_file_last_modified_time=lm_date.substring(11);
        		    	lfm_fl.add(t);
    				} else if (file_list[i].getName().equals(gp.settingsLogFileName+".txt")){
        		    	LogFileListItem t=new LogFileListItem();
        		    	t.log_file_name=file_list[i].getName();
        		    	t.log_file_path=file_list[i].getPath();
        		    	t.log_file_size=MiscUtil.convertFileSize(file_list[i].length());
        		    	t.log_file_last_modified=file_list[i].lastModified();
        		    	if (file_list[i].getPath().equals(gp.settingsLogFileDir+gp.settingsLogFileName+".txt"))
        		    		t.isCurrentLogFile=true;
        		    	String lm_date=StringUtil.convDateTimeTo_YearMonthDayHourMinSec(file_list[i].lastModified());
        		    	t.log_file_last_modified_date=lm_date.substring(0,10);
        		    	t.log_file_last_modified_time=lm_date.substring(11);
        		    	lfm_fl.add(t);
    				}
    			}
    		}
    		Collections.sort(lfm_fl,new Comparator<LogFileListItem>(){
				@Override
				public int compare(LogFileListItem arg0,
						LogFileListItem arg1) {
					int result=0;
					long comp=arg1.log_file_last_modified-arg0.log_file_last_modified;
					if (comp==0) result=0;
					else if(comp<0) result=-1;
					else if(comp>0) result=1;
					return result;
				}
    			
    		});
    	}
    	if (lfm_fl.size()==0) {
    		LogFileListItem t=new LogFileListItem();
    		lfm_fl.add(t);
    	}
    	return lfm_fl;
    };

}
