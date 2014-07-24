package com.sentaroh.android.DriveRecorder;


import java.lang.Thread.UncaughtExceptionHandler;

import com.sentaroh.android.DriveRecorder.Log.LogFileListDialogFragment;
import com.sentaroh.android.DriveRecorder.Log.LogUtil;
import com.sentaroh.android.Utilities.NotifyEvent;
import com.sentaroh.android.Utilities.Dialog.CommonDialog;
import com.sentaroh.android.Utilities.NotifyEvent.NotifyEventListener;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;

public class ActivityMain extends FragmentActivity {
    public Activity mActivity=null;
    
    private int mRestartStatus=0;
    private Context mContext=null;

    private GlobalParameters mGp=null;
    
    private Handler mUiHandler=null;
    
    private LogUtil mLog=null;
    
	@Override  
	protected void onSaveInstanceState(Bundle outState) {  
		super.onSaveInstanceState(outState);
	};  

	@Override  
	protected void onRestoreInstanceState(Bundle savedInstanceState) {  
		super.onRestoreInstanceState(savedInstanceState);
		mRestartStatus=2;
	};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mUiHandler=new Handler();
        mGp=(GlobalParameters) this.getApplication();
        mGp.initSettingParms(this);
        mGp.loadSettingParms(this);
        
        mContext=this.getApplicationContext();
        
        mActivity=this;
//        mGp.surfaceView = (SurfaceView) findViewById(R.id.surfaceView1);
//        mGp.surfaceHolder = mGp.surfaceView.getHolder();
//        mGp.surfaceHolder.addCallback(mSurfaceListener);
//        mGp.surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        
        mLog=new LogUtil(mContext, "Main", mGp);
        
        if (mGp.settingsDeviceOrientationPortrait) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        else setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        
        Intent intent = new Intent(this, RecorderService.class);
        startService(intent);

	    defaultUEH = Thread.currentThread().getUncaughtExceptionHandler();
        Thread.currentThread().setUncaughtExceptionHandler(unCaughtExceptionHandler);

    };

    @Override
    public void onResume() {
    	super.onResume();
		refreshOptionMenu();
    	if (mRestartStatus==1) {
        	if (isRecording()) {
        		showPreview();
        	} else {
        		hidePreview();
        	};
    	} else {
    		NotifyEvent ntfy=new NotifyEvent(mContext);
    		ntfy.setListener(new NotifyEventListener(){
				@Override
				public void positiveResponse(Context c, Object[] o) {
					setCallbackListener();
					setActivityStarted(true);
					if (mRestartStatus==0) {
						
					} else if (mRestartStatus==2) {
						
					}
	            	if (isRecording()) {
	            		showPreview();
	            	} else {
	            		hidePreview();
	            	};
			        mRestartStatus=1;
				}
				@Override
				public void negativeResponse(Context c, Object[] o) {
					
				}
    		});
    		openService(ntfy);
    	}
    };

    @Override
    public void onPause() {
    	super.onPause();
    	mLog.addDebugMsg(1,"I","onPause entered");
    	hidePreview();
    };

    @Override
    public void onStop() {
    	super.onStop();
    	mLog.addDebugMsg(1,"I","onStop entered");
    };

    @Override
    public void onDestroy() {
    	super.onDestroy();
    	mLog.addDebugMsg(1,"I","onDestroy entered");
    	unsetCallbackListener();
    	closeService();
    	mLog.flushLog();
    };

	final private void refreshOptionMenu() {
		if (Build.VERSION.SDK_INT>=11) {
			mActivity.invalidateOptionsMenu();
		}
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		mLog.addDebugMsg(1, "I","onCreateOptionsMenu entered");
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu_top, menu);
		return true;
	};

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	mLog.addDebugMsg(1, "I","onPrepareOptionsMenu entered");
    	super.onPrepareOptionsMenu(menu);
    	if (mStartStopBtnEnabled) {
    		menu.findItem(R.id.menu_top_start_recorder).setEnabled(true);
    		menu.findItem(R.id.menu_top_stop_recorder).setEnabled(true);
    	} else {
    		menu.findItem(R.id.menu_top_start_recorder).setEnabled(false);
    		menu.findItem(R.id.menu_top_stop_recorder).setEnabled(false);
    	}
    	if (!isRecording()) {
    		menu.findItem(R.id.menu_top_start_recorder).setVisible(true);
    		menu.findItem(R.id.menu_top_stop_recorder).setVisible(false);
    		menu.findItem(R.id.menu_top_settings).setEnabled(true);
    	} else {
    		menu.findItem(R.id.menu_top_start_recorder).setVisible(false);
    		menu.findItem(R.id.menu_top_stop_recorder).setVisible(true);
    		menu.findItem(R.id.menu_top_settings).setEnabled(false);
    	}
        return true;
    };
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		mLog.addDebugMsg(1, "I","onOptionsItemSelected entered");
		switch (item.getItemId()) {
			case R.id.menu_top_start_recorder:
				mStartStopBtnEnabled=false;
				showPreview();
				startRecorderThread();
				refreshOptionMenu();
				return true;
			case R.id.menu_top_stop_recorder:
				mStartStopBtnEnabled=false;
				stopRecorderThread();
//				hidePreview();
				refreshOptionMenu();
				return true;
			case R.id.menu_top_show_log:
				invokeShowLogActivity();
				return true;
			case R.id.menu_top_settings:
				invokeSettingsActivity();
				return true;				
			case R.id.menu_top_manage_log:
				mLog.flushLog();
				LogFileListDialogFragment lfm=
						LogFileListDialogFragment.newInstance(false,getString(R.string.msgs_log_file_list_title));
				lfm.showDialog(getSupportFragmentManager(), lfm, mGp);
				return true;				
			case R.id.menu_top_about_drive_recorder:
				about();
				return true;				
		}
		return false;
	};
	
	private void about() {
		// common カスタムダイアログの生成
		final Dialog dialog = new Dialog(this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setCanceledOnTouchOutside(false);
		dialog.setContentView(R.layout.about_dialog);
		((TextView)dialog.findViewById(R.id.about_dialog_title)).setText(
			getString(R.string.msgs_about_drive_recorder)+" Ver "+getApplVersionName());
		final WebView func_view=(WebView)dialog.findViewById(R.id.about_dialog_function);
//	    func_view.setWebViewClient(new WebViewClient());
//	    func_view.getSettings().setJavaScriptEnabled(true); 
		func_view.getSettings().setSupportZoom(true);
//		func_view.setVerticalScrollbarOverlay(true);
		func_view.setBackgroundColor(Color.LTGRAY);
//		func_view.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
		func_view.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET); 
		func_view.setVerticalScrollBarEnabled(true);
		func_view.setScrollbarFadingEnabled(false);
		if (Build.VERSION.SDK_INT>10) {
			func_view.getSettings().setDisplayZoomControls(true); 
			func_view.getSettings().setBuiltInZoomControls(true);
		} else {
			func_view.getSettings().setBuiltInZoomControls(true);
		}
		func_view.loadUrl("file:///android_asset/"+
				getString(R.string.msgs_about_dlg_func_html));

		func_view.getSettings().setTextZoom(120);
		func_view.getSettings().setDisplayZoomControls(true);
		func_view.getSettings().setBuiltInZoomControls(true);
		
		final Button btnOk = (Button) dialog.findViewById(R.id.about_dialog_btn_ok);
		
		func_view.setVisibility(TextView.VISIBLE);
		
		CommonDialog.setDlgBoxSizeLimit(dialog,true);
		
		// OKボタンの指定
		btnOk.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				dialog.dismiss();
			}
		});
		// Cancelリスナーの指定
		dialog.setOnCancelListener(new Dialog.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface arg0) {
				btnOk.performClick();
			}
		});
//		dialog.setOnKeyListener(new DialogOnKeyListener(context));
//		dialog.setCancelable(false);
		dialog.show();
				
	};

	private String getApplVersionName() {
		try {
		    String packegeName = getPackageName();
		    PackageInfo packageInfo = getPackageManager().getPackageInfo(packegeName, PackageManager.GET_META_DATA);
		    return packageInfo.versionName;
		} catch (NameNotFoundException e) {
			return "";
		}
	};

	
	private void invokeSettingsActivity() {
		Intent intent = new Intent(this, ActivitySetting.class);
		startActivityForResult(intent,0);
	}
	
	protected void onActivityResult(int rc, int resultCode, Intent data) {
		if (rc==0) applySettingParms();
	};
	
	private void applySettingParms() {
        mGp.loadSettingParms(this);
        if (mGp.settingsDeviceOrientationPortrait) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        else setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
	};

	private void invokeShowLogActivity() {
		mLog.flushLog();
//		enableBrowseLogFileMenu=false;
		if (mLog.isLogFileExists()) {
			Intent intent = new Intent(android.content.Intent.ACTION_VIEW);
			intent.setDataAndType(Uri.parse("file://"+mLog.getLogFilePath()), "text/plain");
			startActivity(intent);
		}
	};

    private boolean isRecording() {
    	boolean result=false;
    	try {
    		if (mRecoderClient!=null) result=mRecoderClient.aidlIsRecording();
    		else mLog.addDebugMsg(1, "I","isRecording is not excuted");
		} catch (RemoteException e) {
			e.printStackTrace();
		}
    	return result;
    };
    
    private void showPreview() {
    	try {
    		if (mRecoderClient!=null) mRecoderClient.aidlShowPreview();
    		else mLog.addDebugMsg(1, "I","showPreview is not excuted");
		} catch (RemoteException e) {
			e.printStackTrace();
		}
    };
    
    private void hidePreview() {
    	try {
    		if (mRecoderClient!=null) mRecoderClient.aidlHidePreview();
    		else mLog.addDebugMsg(1, "I","hidePreview is not excuted");
		} catch (RemoteException e) {
			e.printStackTrace();
		}
    };

    private void startRecorderThread() {
    	try {
			if (mRecoderClient!=null) mRecoderClient.aidlStartRecorderThread();
    		else mLog.addDebugMsg(1, "I","startRecorderThread is not excuted");
		} catch (RemoteException e) {
			e.printStackTrace();
		}
    };

    private void stopRecorderThread() {
    	try {
			if (mRecoderClient!=null) mRecoderClient.aidlStopRecorderThread();
    		else mLog.addDebugMsg(1, "I","stopRecorderThread is not excuted");
		} catch (RemoteException e) {
			e.printStackTrace();
		}
    };

    private void setActivityStarted(boolean started) {
    	try {
			if (mRecoderClient!=null) mRecoderClient.aidlSetActivityStarted(started);
    		else mLog.addDebugMsg(1, "I","aidlSetActivityStarted is not excuted");
		} catch (RemoteException e) {
			e.printStackTrace();
		}
    };

    private boolean mStartStopBtnEnabled=true;
    
	private IRecorderCallback mRecorderCallbackStub=new IRecorderCallback.Stub() {
		@Override
		public void notifyRecordingStarted() throws RemoteException {
			mUiHandler.postDelayed(new Runnable(){
				@Override
				public void run() {
					mStartStopBtnEnabled=true;
					refreshOptionMenu();
				}
			},500);
		};
		@Override
		public void notifyRecordingStopped() throws RemoteException {
			mUiHandler.postDelayed(new Runnable(){
				@Override
				public void run() {
					mStartStopBtnEnabled=true;
					refreshOptionMenu();
				}
			},500);
		};

    };

	private IRecorderClient mRecoderClient=null;
	private ServiceConnection mSvcConnection=null;
	
	private void openService(final NotifyEvent p_ntfy) {
		mLog.addDebugMsg(1,"I","openService entered");
        mSvcConnection = new ServiceConnection(){
    		public void onServiceConnected(ComponentName arg0, IBinder service) {
    			mLog.addDebugMsg(1,"I","onServiceConnected entered");
    	    	mRecoderClient=IRecorderClient.Stub.asInterface(service);
   	    		p_ntfy.notifyToListener(true, null);
    		}
    		public void onServiceDisconnected(ComponentName name) {
    			mSvcConnection = null;
    			mLog.addDebugMsg(1,"I","onServiceDisconnected entered");
    	    	mRecoderClient=null;
    		}
        };
    	
		Intent intmsg = new Intent(mContext, RecorderService.class);
		intmsg.setAction("Connection");
        bindService(intmsg, mSvcConnection, BIND_AUTO_CREATE);
	};

	private void closeService() {
		mLog.addDebugMsg(1,"I","closeService entered");
    	if (mSvcConnection!=null) {
        	setActivityStarted(false);
//    		if (!isRecording()) {
//        		try {
//    				mRecoderClient.aidlStopService();
//    			} catch (RemoteException e) {
//    				e.printStackTrace();
//    			}
//    		}
    		mRecoderClient=null;
    		unbindService(mSvcConnection);
	    	mSvcConnection=null;
    	}
//        Intent intent = new Intent(this, RecorderService.class);
//        stopService(intent);
	};
	
	final private void setCallbackListener() {
		mLog.addDebugMsg(1, "I", "setCallbackListener entered");
		try{
			mRecoderClient.setCallBack(mRecorderCallbackStub);
		} catch (RemoteException e){
			e.printStackTrace();
			mLog.addDebugMsg(0,"E", "setCallbackListener error :"+e.toString());
		}
	};

	final private void unsetCallbackListener() {
		if (mRecoderClient!=null) {
			try{
				mRecoderClient.removeCallBack(mRecorderCallbackStub);
			} catch (RemoteException e){
				e.printStackTrace();
				mLog.addDebugMsg(0,"E", "unsetCallbackListener error :"+e.toString());
			}
		}
	};
 
	// Default uncaught exception handler variable
    private UncaughtExceptionHandler defaultUEH;
    private Thread.UncaughtExceptionHandler unCaughtExceptionHandler =
        new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
            	Thread.currentThread().setUncaughtExceptionHandler(defaultUEH);
//            	ex.printStackTrace();
            	StackTraceElement[] st=ex.getStackTrace();
            	String st_msg="";
            	for (int i=0;i<st.length;i++) {
            		st_msg+="\n at "+st[i].getClassName()+"."+
            				st[i].getMethodName()+"("+st[i].getFileName()+
            				":"+st[i].getLineNumber()+")";
            	}
    			String end_msg=ex.toString()+st_msg;
    			
    			Throwable cause=ex.getCause();
    			st=cause.getStackTrace();
    			st_msg="";
            	for (int i=0;i<st.length;i++) {
            		st_msg+="\n at "+st[i].getClassName()+"."+
            				st[i].getMethodName()+"("+st[i].getFileName()+
            				":"+st[i].getLineNumber()+")";
            	}
    			String end_msg2="Caused by:"+cause.toString()+st_msg;
    			mLog.addDebugMsg(1, "E", end_msg);
    			mLog.addDebugMsg(1, "E", end_msg2);
  
//    			File ldir=new File(mGp.settingsLogFileDir);
//    			if (!ldir.exists()) ldir.mkdirs();
//    			
//        		File lf=new File(mGp.settingsLogFileDir+"exception.txt");
//        		try {
//        			FileWriter fw=new FileWriter(lf,true);
//					PrintWriter pw=new PrintWriter(fw);
//					pw.println(end_msg);
//					pw.println(end_msg2);
//					pw.flush();
//					pw.close();
//				} catch (FileNotFoundException e) {
//					e.printStackTrace();
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
                // re-throw critical exception further to the os (important)
                defaultUEH.uncaughtException(thread, ex);
            }
    };


}