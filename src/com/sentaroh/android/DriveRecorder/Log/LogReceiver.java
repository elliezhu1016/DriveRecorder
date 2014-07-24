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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

import com.sentaroh.android.DriveRecorder.GlobalParameters;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;

@SuppressLint("SdCardPath")
public class LogReceiver extends BroadcastReceiver{

	private static PrintWriter printWriter=null;
//	private static BufferedWriter bufferedWriter;
	private static FileWriter fileWriter ;	
	private static String log_dir=null;
	private static int debug_level=1;
	private static boolean log_option=true;
	private static boolean shutdown_received=false;
	private static File logFile=null;
	private static boolean mediaUsable=false;
	private static final SimpleDateFormat sdfDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.getDefault());

	private static GlobalParameters mGlblParms=null;
	
	private static String log_id="";
	
	@Override
	final public void onReceive(Context c, Intent in) {
//		StrictMode.allowThreadDiskWrites();
//		StrictMode.allowThreadDiskReads();
		if (!mediaUsable) {
			if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
				mediaUsable=true;
			}
		} 
		if (log_dir==null) {
			setLogId("LogReceiver");
			initParms(c);
			if (debug_level>0) {
				String line="initialized dir="+log_dir+", debug="+debug_level;
				Log.v(APPLICATION_TAG,"I "+log_id+line);
				putLogMsg(c,"M I "+sdfDateTime.format(System.currentTimeMillis())+" "+log_id+line);
			}
		}
//		Log.v("","media="+mediaUsable);
		if (in.getAction().equals(Intent.ACTION_MEDIA_EJECT)) {
			if (Environment.getExternalStorageState().equals(Environment.MEDIA_UNMOUNTED)) {
				mediaUsable=false;
				closeLogFile();
			}
		} else if (in.getAction().equals(Intent.ACTION_SHUTDOWN)) {
			shutdown_received=true;
			if (printWriter!=null) printWriter.flush();
		} else if (in.getAction().equals(BROADCAST_LOG_SEND)) {
			String line=in.getExtras().getString("LOG");
			putLogMsg(c,line);
		} else if (in.getAction().equals(BROADCAST_LOG_RESET)) {
			initParms(c);
			closeLogFile();
			if (log_option) openLogFile(c);
			if (debug_level>0) {
				String line="re-initialized dir="+log_dir+", debug="+debug_level;
				Log.v(APPLICATION_TAG,"I "+log_id+line);
				putLogMsg(c,"M I "+sdfDateTime.format(System.currentTimeMillis())+" "+log_id+line);
			}
		} else if (in.getAction().equals(BROADCAST_LOG_DELETE)) {
			closeLogFile();
			logFile.delete();
			if (log_option) openLogFile(c);
		} else if (in.getAction().equals(BROADCAST_LOG_ROTATE)) {
			rotateLogFileForce(c);
		} else if (in.getAction().equals(BROADCAST_LOG_FLUSH)) {
			if (printWriter!=null) printWriter.flush();
		}
//		StrictMode.enableDefaults();
	};

	final static private void setLogId(String li) {
		log_id=(li+"                 ").substring(0,16)+" ";
	};

	final static private void putLogMsg(Context c,String msg) {
//		Log.v("","log_option="+log_option+", mu="+mediaUsable+", pw="+printWriter);
		if (log_option && mediaUsable) {
			rotateLogFileConditional(c);
			if (printWriter==null) {
				openLogFile(c);
				if (printWriter!=null) {
					printWriter.println(msg);
					if (shutdown_received) printWriter.flush();
				}
			} else {
				printWriter.println(msg);
				if (shutdown_received) printWriter.flush();
			}
		}
	}
	
	@SuppressLint("InlinedApi")
	final static private void initParms(Context c) {
		mGlblParms=new GlobalParameters();
		mGlblParms.loadSettingParms(c);

		log_dir=mGlblParms.settingsLogFileDir;
		debug_level=mGlblParms.settingsDebugLevel;
		log_option=mGlblParms.settingsLogEnabled;
		logFile=new File(log_dir+mGlblParms.settingsLogFileName+".txt");
	};
	
	final static private void rotateLogFileConditional(Context c) {
		if (printWriter!=null && mediaUsable && logFile.length()>=LOG_LIMIT_SIZE) {
			rotateLogFileForce(c);
		}
	};

	@SuppressLint("SimpleDateFormat")
	final static private void rotateLogFileForce(Context c) {
		if (printWriter!=null && mediaUsable) {
			printWriter.flush();
			closeLogFile();
			SimpleDateFormat sdf =new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS");
			File lf=new File(log_dir+mGlblParms.settingsLogFileName+"_"+sdf.format(System.currentTimeMillis())+".txt");
			logFile.renameTo(lf);
			openLogFile(c);
			logFile=new File(log_dir+mGlblParms.settingsLogFileName+".txt");
			if (debug_level>0) {
				String line="Logfile was rotated "+log_dir+mGlblParms.settingsLogFileName+sdf.format(System.currentTimeMillis())+".txt";
				Log.v(APPLICATION_TAG,"I "+log_id+line);
				putLogMsg(c,"M I "+sdfDateTime.format(System.currentTimeMillis())+" "+log_id+line);
			}
		} else if (printWriter==null && mediaUsable) {
			File tlf=new File(log_dir+mGlblParms.settingsLogFileName+".txt");
			if (tlf.exists()) {
				SimpleDateFormat sdf =new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS");
				File lf=new File(log_dir+mGlblParms.settingsLogFileName+"_"+sdf.format(System.currentTimeMillis())+".txt");
				tlf.renameTo(lf);
			}
		}
	};

	
	final static private void closeLogFile() {
		if (printWriter!=null && mediaUsable) {
			printWriter.flush();
			printWriter.close(); 
			try {
//				bufferedWriter.close();
				fileWriter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			printWriter=null;
		}
	};
	
	final static private void openLogFile(Context c) { 
		if (printWriter==null && mediaUsable) {
			BufferedWriter bw=null;
			try {
				File lf=new File(log_dir);
				if (!lf.exists()) lf.mkdirs();
				fileWriter=new FileWriter(log_dir+mGlblParms.settingsLogFileName+".txt",true);
				bw=new BufferedWriter(fileWriter,LOG_FILE_BUFFER_SIZE);
			} catch (IOException e) {
				e.printStackTrace();
			}
			printWriter=new PrintWriter(bw,true);//false);
			
			houseKeepLogFile(c);
		}
	};
	
	final static private void houseKeepLogFile(Context c) {
		ArrayList<LogFileListItem> lfml=LogUtil.createLogFileList(mGlblParms);
		Collections.sort(lfml,new Comparator<LogFileListItem>(){
			@Override
			public int compare(LogFileListItem arg0,
					LogFileListItem arg1) {
				int result=0;
				long comp=arg0.log_file_last_modified-arg1.log_file_last_modified;
				if (comp==0) result=0;
				else if(comp<0) result=-1;
				else if(comp>0) result=1;
				return result;
			}
		});
		
		int l_epos=lfml.size()-(mGlblParms.settingsLogMaxFileCount+1);
		if (l_epos>0) {
			for (int i=0;i<l_epos;i++) {
				String line="Logfile was deleted "+lfml.get(0).log_file_path;
				Log.v(APPLICATION_TAG,"I "+log_id+line);
				putLogMsg(c,"M I "+sdfDateTime.format(System.currentTimeMillis())+log_id+line);
				File lf=new File(lfml.get(0).log_file_path);
				lf.delete();
				lfml.remove(0);
			}
			
		}
	};

}
