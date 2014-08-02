package com.sentaroh.android.DriveRecorder;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import com.sentaroh.android.DriveRecorder.Log.LogFileListDialogFragment;
import com.sentaroh.android.DriveRecorder.Log.LogUtil;
import com.sentaroh.android.Utilities.MiscUtil;
import com.sentaroh.android.Utilities.NotifyEvent;
import com.sentaroh.android.Utilities.ContextMenu.CustomContextMenu;
import com.sentaroh.android.Utilities.ContextMenu.CustomContextMenuItem.CustomContextMenuOnClickListener;
import com.sentaroh.android.Utilities.Dialog.CommonDialog;
import com.sentaroh.android.Utilities.NotifyEvent.NotifyEventListener;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.MimeTypeMap;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class ActivityMain extends FragmentActivity {
    public Activity mActivity=null;
    
    private int mRestartStatus=0;
    private Context mContext=null;

    private GlobalParameters mGp=null;
    
    private Handler mUiHandler=null;
    
    private LogUtil mLog=null;
    private CustomContextMenu mCcMenu=null;
    private CommonDialog mCommonDlg=null;

    private LinearLayout mMainUiView=null;
    
    private String mCurrentSelectedDayList="";
    private ListView mDayListView=null;
    private ListView mFileListView=null;
    private AdapterDayList mDayListAdapter=null;
    private AdapterFileList mFileListAdapter=null;
    
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
        
        mMainUiView=(LinearLayout)findViewById(R.id.main_ui_view);
        
        mActivity=this;
//        mGp.surfaceView = (SurfaceView) findViewById(R.id.surfaceView1);
//        mGp.surfaceHolder = mGp.surfaceView.getHolder();
//        mGp.surfaceHolder.addCallback(mSurfaceListener);
//        mGp.surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        
        mLog=new LogUtil(mContext, "Main", mGp);
        
        mLog.addDebugMsg(1, "I","onCreate entered");
        
        mCcMenu = new CustomContextMenu(getResources(),getSupportFragmentManager());
        mCommonDlg=new CommonDialog(mContext, getSupportFragmentManager());

        loadThumnailList();
        
        if (mGp.settingsDeviceOrientationPortrait) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        else setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        
        Intent intent = new Intent(this, RecorderService.class);
        startService(intent);

        mDayListView=(ListView)findViewById(R.id.main_day_listview);
        mFileListView=(ListView)findViewById(R.id.main_file_listview);
        
    };
    
    @Override
    public void onResume() {
    	super.onResume();
    	mLog.addDebugMsg(1, "I","onResume entered, restartStatus="+mRestartStatus);
		refreshOptionMenu();
    	if (mRestartStatus==1) {
        	if (isRecording()) {
        		showPreview();
        		setUiEnabled(false);
        	} else {
        		hidePreview();
        		setUiEnabled(true);
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
	            		setUiEnabled(false);
	            	} else {
	            		hidePreview();
	            		setUiEnabled(true);
	            	};
			        mRestartStatus=1;

			        createDayList();
			        if (mDayListAdapter.getCount()>0) {
			        	Handler hndl=new Handler();
			        	hndl.postDelayed(new Runnable(){
							@Override
							public void run() {
//								Log.v("","lv="+mDayListView+", ch="+mDayListView.getChildAt(0));
//					    		if (mDayListView.getChildAt(0)!=null) 
//					    			mDayListView.getChildAt(0).setBackgroundColor(Color.DKGRAY);
					    		createFileList(mDayListAdapter.getItem(0).day);
							}
			        	}, 100);
			        }
			        setMainListener();
			        setDayListListener();
			        setFileListListener();
				}
				@Override
				public void negativeResponse(Context c, Object[] o) {}
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
	
	private void setStartStopBtnEnabled(boolean p) {
		mStartStopBtnEnabled=p;
	};

	private boolean mUiEnabled=true;
	private void setUiEnabled(boolean p) {
		mUiEnabled=p;
		if (p) mMainUiView.setVisibility(LinearLayout.VISIBLE);
		else  mMainUiView.setVisibility(LinearLayout.GONE);
	}
	@SuppressWarnings("unused")
	private boolean isUiEnabled() {
		return mUiEnabled;
	}
	
	private boolean isStartStopBtnEnabled() {
		return mStartStopBtnEnabled;
	}

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	mLog.addDebugMsg(1, "I","onPrepareOptionsMenu entered");
    	super.onPrepareOptionsMenu(menu);
    	if (isStartStopBtnEnabled()) {
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
    		menu.findItem(R.id.menu_top_about_drive_recorder).setEnabled(true);
    		menu.findItem(R.id.menu_top_manage_log).setEnabled(true);
    	} else {
    		menu.findItem(R.id.menu_top_start_recorder).setVisible(false);
    		menu.findItem(R.id.menu_top_stop_recorder).setVisible(true);
    		menu.findItem(R.id.menu_top_settings).setEnabled(false);
    		menu.findItem(R.id.menu_top_about_drive_recorder).setEnabled(false);
    		menu.findItem(R.id.menu_top_manage_log).setEnabled(false);
    	}
        return true;
    };
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		mLog.addDebugMsg(1, "I","onOptionsItemSelected entered");
		switch (item.getItemId()) {
			case R.id.menu_top_start_recorder:
				setStartStopBtnEnabled(false);
				setUiEnabled(false);
				showPreview();
				startRecorderThread();
				refreshOptionMenu();
				return true;
			case R.id.menu_top_stop_recorder:
				setStartStopBtnEnabled(false);
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

    private void setMainListener() {
    	final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    	CheckBox cb_main_portrait=(CheckBox)findViewById(R.id.main_orientation_portrait);
    	CheckBox cb_main_rec_sound=(CheckBox)findViewById(R.id.main_record_audio);
    	cb_main_portrait.setChecked(mGp.settingsDeviceOrientationPortrait);
    	cb_main_portrait.setOnCheckedChangeListener(new OnCheckedChangeListener(){
			@Override
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				mGp.settingsDeviceOrientationPortrait=isChecked;
		        if (mGp.settingsDeviceOrientationPortrait) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		        else setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		        
				prefs.edit().putBoolean(mContext.getString(R.string.settings_device_orientation_portrait),
						mGp.settingsDeviceOrientationPortrait).commit();

			}
    	});
    	cb_main_rec_sound.setChecked(mGp.settingsRecordSound);
    	cb_main_rec_sound.setOnCheckedChangeListener(new OnCheckedChangeListener(){
			@Override
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				mGp.settingsRecordSound=isChecked;
				prefs.edit().putBoolean(mContext.getString(R.string.settings_record_sound),
						mGp.settingsRecordSound).commit();
			}
    	});
    };
    
    private void setDayListListener() {
    	mDayListView.setOnItemClickListener(new OnItemClickListener(){
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				for (int j = 0; j < parent.getChildCount(); j++)
	                parent.getChildAt(j).setBackgroundColor(Color.TRANSPARENT);
//	            view.setBackgroundColor(Color.DKGRAY);
				createFileList(mDayListAdapter.getItem(position).day);
			}
    	});
    	
    	mDayListView.setOnItemLongClickListener(new OnItemLongClickListener(){
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view,
					final int position, long id) {
				mCcMenu.addMenuItem(mContext.getString(R.string.msgs_main_ccmenu_day_delete)+
						" "+mDayListAdapter.getItem(position).day,R.drawable.menu_trash)
			  		.setOnClickListener(new CustomContextMenuOnClickListener() {
					  @Override
					  public void onClick(CharSequence menuTitle) {
						  NotifyEvent ntfy=new NotifyEvent(mContext);
						  ntfy.setListener(new NotifyEventListener(){
							@Override
							public void positiveResponse(Context c, Object[] o) {
						    	File lf=new File(mGp.videoFileDir);
						    	File[] tfl=lf.listFiles();
						    	if (tfl!=null && tfl.length>0) {
						    		for (int i=0;i<tfl.length;i++) {
						        		String tfn=tfl[i].getName().substring(13,23);
						        		if (mDayListAdapter.getItem(position).day.equals(tfn)) {
						        			mLog.addLogMsg("I", "File was deleted. name="+tfl[i].getName());
						        			deleteMediaStoreItem(tfl[i].getPath());
						        			tfl[i].delete();
						        		}
						    		}
						    	}
						    	mDayListAdapter.remove(mDayListAdapter.getItem(position));
						    	housekeepThumnailCache();
						    	createDayList();
						        if (mDayListAdapter.getCount()>0) {
						        	Handler hndl=new Handler();
						        	hndl.postDelayed(new Runnable(){
										@Override
										public void run() {
//								    		mDayListView.getChildAt(0).setBackgroundColor(Color.DKGRAY);
								    		createFileList(mDayListAdapter.getItem(0).day);
										}
						        	}, 100);
						        } else {
						        	mFileListAdapter.clear();
						        }

							}
							@Override
							public void negativeResponse(Context c, Object[] o) {}
						  });
						  mCommonDlg.showCommonDialog(true, "W", String.format(
								  mContext.getString(R.string.msgs_main_ccmenu_day_delete_day_confirm),
								  mDayListAdapter.getItem(position).day), "", ntfy);
					  	}
				});
				mCcMenu.createMenu();
				return true;
			}
    	});
    };

	private int deleteMediaStoreItem(String fp) {
		int dc_image=0, dc_audio=0, dc_video=0, dc_files=0;
		String mt=isMediaFile(fp);
		if (mt!=null && 
				(mt.startsWith("audio") ||
				 mt.startsWith("video") ||
				 mt.startsWith("image") )) {
	    	ContentResolver cri = mContext.getContentResolver();
	    	ContentResolver cra = mContext.getContentResolver();
	    	ContentResolver crv = mContext.getContentResolver();
	    	ContentResolver crf = mContext.getContentResolver();
	    	dc_image=cri.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
	          		MediaStore.Images.Media.DATA + "=?", new String[]{fp} );
	       	dc_audio=cra.delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
	           		MediaStore.Audio.Media.DATA + "=?", new String[]{fp} );
	       	dc_video=crv.delete(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
	           		MediaStore.Video.Media.DATA + "=?", new String[]{fp} );
	        if(Build.VERSION.SDK_INT >= 11) {
	        	dc_files=crf.delete(MediaStore.Files.getContentUri("external"), 
	          		MediaStore.Files.FileColumns.DATA + "=?", new String[]{fp} );
	        }
//	        Log.v("","fp="+fp);
		} else {
//       		sendDebugLogMsg(1,"I","deleMediaStoreItem not MediaStore library. fn="+
//	       				fp+"");
		}
		return dc_image+dc_audio+dc_video+dc_files;
	};

	@SuppressLint("DefaultLocale")
	private static String isMediaFile(String fp) {
		String mt=null;
		String fid="";
		if (fp.lastIndexOf(".")>0) {
			fid=fp.substring(fp.lastIndexOf(".")+1,fp.length());
			fid=fid.toLowerCase();
		}
		mt=MimeTypeMap.getSingleton().getMimeTypeFromExtension(fid);
		if (mt==null) return "";
		else return mt;
	};


    private void setFileListListener() {
    	mFileListView.setOnItemClickListener(new OnItemClickListener(){
			@SuppressLint("DefaultLocale")
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				for (int j = 0; j < parent.getChildCount(); j++)
	                parent.getChildAt(j).setBackgroundColor(Color.TRANSPARENT);
//	            view.setBackgroundColor(Color.DKGRAY);
	            
				FileListItem fli=mFileListAdapter.getItem(position);
				Intent intent;
				intent = new Intent(mContext,ActivityVideoPlayer.class);
				intent.putExtra("fp",fli.file_name);
				startActivity(intent);

	            
//				String fid="";
//	    		if (fli.file_name.lastIndexOf(".") > 0) {
//	    			fid = fli.file_name.substring(fli.file_name.lastIndexOf(".") + 1,
//	    					fli.file_name.length());
//	    			fid=fid.toLowerCase();
//	    		}
//	    		String mt=MimeTypeMap.getSingleton().getMimeTypeFromExtension(fid);
//	    		if (mt != null) {
//    				Intent intent;
//    				intent = new Intent(android.content.Intent.ACTION_VIEW);
//    				intent.setDataAndType(
//    						Uri.parse("file://"+mGp.videoFileDir+fli.file_name), mt);
//   					startActivity(intent);
//	    		}
			}
    	});
    	
    	mFileListView.setOnItemLongClickListener(new OnItemLongClickListener(){
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view,
					final int position, long id) {
				  NotifyEvent ntfy=new NotifyEvent(mContext);
				  ntfy.setListener(new NotifyEventListener(){
					@Override
					public void positiveResponse(Context c, Object[] o) {
						String fp=mGp.videoFileDir+mFileListAdapter.getItem(position).file_name;
				    	File lf=new File(fp);
	        			mLog.addLogMsg("I", "File was deleted. name="+mFileListAdapter.getItem(position).file_name);
				        deleteMediaStoreItem(fp);
				        lf.delete();
				    	mFileListAdapter.remove(mFileListAdapter.getItem(position));
				    	housekeepThumnailCache();
				    	if (mFileListAdapter.getCount()==0) {
				    		createDayList();
					        if (mDayListAdapter.getCount()>0) {
					        	Handler hndl=new Handler();
					        	hndl.postDelayed(new Runnable(){
									@Override
									public void run() {
//							    		mDayListView.getChildAt(0).setBackgroundColor(Color.DKGRAY);
							    		createFileList(mDayListAdapter.getItem(0).day);
									}
					        	}, 100);
					        }
				    	} 
					}
					@Override
					public void negativeResponse(Context c, Object[] o) {}
				  });
				  mCommonDlg.showCommonDialog(true, "W", String.format(
						  mContext.getString(R.string.msgs_main_ccmenu_day_delete_file_confirm),
						  mFileListAdapter.getItem(position).file_name), "", ntfy);
				
				return true;
			}
    	});
    };

    private void createDayList() {
    	mLog.addDebugMsg(1, "I","createDayList entered");
    	ArrayList<DayListItem> fl=new ArrayList<DayListItem>();
    	File lf=new File(mGp.videoFileDir);
    	File[] tfl=lf.listFiles();
    	if (tfl!=null && tfl.length>0) {
    		ArrayList<String> sfl=new ArrayList<String>();
    		for (int i=0;i<tfl.length;i++) sfl.add(tfl[i].getName());
    		
    		Collections.sort(sfl);
    		
    		String c_day="";
    		for (int i=0;i<sfl.size();i++) {
    			String tfn=sfl.get(i).substring(13,23);
    			if (!c_day.equals(tfn)) {
    				DayListItem dli=new DayListItem();
    				dli.day=tfn;
    				fl.add(dli);
    				c_day=tfn;
    				mLog.addDebugMsg(1, "I","createDayList Day "+tfn+" added");
    			}
    		}
    		
    		for (int i=0;i<fl.size();i++) {
    			int cnt=0;
    			for(int j=0;j<sfl.size();j++) {
    				String tfn=sfl.get(j).substring(13,23);
    				if (tfn.equals(fl.get(i).day)) {
    					cnt++;
    				}
    			}
    			fl.get(i).no_of_file=""+cnt+"ファイル";
    		}
    	}
    	mDayListAdapter=new AdapterDayList(mContext, R.layout.day_list_item, fl);
    	mDayListView.setAdapter(mDayListAdapter);
    };

    private void createFileList(String sel_day) {
    	mLog.addDebugMsg(1, "I","createFileList entered, day="+sel_day);
    	ArrayList<FileListItem> fl=new ArrayList<FileListItem>();
    	File lf=new File(mGp.videoFileDir);
    	File[] tfl=lf.listFiles();
    	ContentResolver crv = mContext.getContentResolver();
    	String[] query_proj=new String[] {MediaStore.Video.VideoColumns.DURATION};
    	if (tfl!=null && tfl.length>0) {
        	for (int i=0;i<tfl.length;i++) {
        		String tfn=tfl[i].getName().substring(13,23);
        		if (sel_day.equals(tfn) && !mGp.currentRecordedFileName.equals(tfl[i].getName())) {
        			FileListItem fli=new FileListItem();
        			fli.file_name=tfl[i].getName();
        			fli.file_size=MiscUtil.convertFileSize(tfl[i].length());
        			fli.thumbnail=readThumnailCache(tfl[i]);
        			Cursor cursor=crv.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, query_proj, "_data=?", 
        	    			new String[]{tfl[i].getPath()}, null);
        			if (cursor!=null && cursor.getCount()>0) {
            			cursor.moveToNext();
    			        int dur=Integer.parseInt(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)));
    					int mm=dur/1000/60;
    					int ss=(dur-(mm*1000*60))/1000;
    			        fli.duration=String.format("%02d",mm)+":"+String.format("%02d",ss);
        			}
                	cursor.close();
                	mLog.addDebugMsg(1, "I","createFileList File "+fli.file_name+" added");
        			fl.add(fli);	
        		}
    		}
    		Collections.sort(fl, new Comparator<FileListItem>(){
				@Override
				public int compare(FileListItem lhs, FileListItem rhs) {
					return lhs.file_name.compareToIgnoreCase(rhs.file_name);
				}
    		});
    	}
    	
    	mFileListAdapter=new AdapterFileList(mContext, R.layout.file_list_item, fl);
    	mFileListView.setAdapter(mFileListAdapter);
    	mCurrentSelectedDayList=sel_day;
    	saveThumnailList();
    };

    class ThumnailListItem {
    	public String file_name="";
    	public byte[] thumnail_byte_array=null;
    }
    private ArrayList<ThumnailListItem> mThumnailList=null;
    private boolean mThumnailListModified=false;
    private void loadThumnailList() {
    	mThumnailList=new ArrayList<ThumnailListItem>();
    	File lf=new File(Environment.getExternalStorageDirectory().toString()+"/DriveRecorder/thumnail_cache");
    	if (lf.exists()) {
        	try {
    			FileInputStream fis=new FileInputStream(lf);
    			BufferedInputStream bis=new BufferedInputStream(fis);
    			ObjectInputStream ois=new ObjectInputStream(bis);
    			int l_cnt=ois.readInt();
    	    	for(int i=0;i<l_cnt;i++) {
    	    		ThumnailListItem tli=new ThumnailListItem();
    	    		tli.file_name=ois.readUTF();
    	    		int b_cnt=ois.readInt();
    	    		if (b_cnt!=0) {
    	    			tli.thumnail_byte_array=new byte[b_cnt];
    	    			ois.readFully(tli.thumnail_byte_array);
    	    		}
                	File tlf=new File(mGp.videoFileDir+tli.file_name);
                	if (tlf.exists()) {
                		mThumnailList.add(tli);
                		mLog.addDebugMsg(1, "I","ThumnailList file "+tli.file_name+" was added");
                	} else {
                		mThumnailListModified=true;
                	}
    	    	}
           		saveThumnailList();
    		} catch (FileNotFoundException e) {
    			e.printStackTrace();
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
    	}
    	mLog.addDebugMsg(1, "I","ThumnailList loaded count="+mThumnailList.size());
    };

    private void housekeepThumnailCache() {
    	if (mThumnailList.size()>0) {
        	for(int i=mThumnailList.size()-1;i>=0;i--) {
        		ThumnailListItem tli=mThumnailList.get(i);
            	File tlf=new File(mGp.videoFileDir+tli.file_name);
            	if (!tlf.exists()) {
            		mThumnailList.remove(i);
            		mThumnailListModified=true;
            	}
        	}
    	}
    };
    
    private void saveThumnailList() {
    	mLog.addDebugMsg(1, "I","saveThumnailList entered, mThumnailListModified="+mThumnailListModified);
    	if (mThumnailListModified) {
    		mThumnailListModified=false;
    		housekeepThumnailCache();
        	File lf=new File(Environment.getExternalStorageDirectory().toString()+"/DriveRecorder/");
        	if (!lf.exists()) lf.mkdirs();
        	lf=new File(Environment.getExternalStorageDirectory().toString()+"/DriveRecorder/thumnail_cache");
        	lf.delete();
        	try {
    			if (mThumnailList.size()>0) {
        			FileOutputStream fos=new FileOutputStream(lf);
        			BufferedOutputStream bos=new BufferedOutputStream(fos);
        			ObjectOutputStream oos=new ObjectOutputStream(bos);
        			oos.writeInt(mThumnailList.size());
        	    	for(int i=0;i<mThumnailList.size();i++) {
        	    		ThumnailListItem tli=mThumnailList.get(i);
        	    		if (!tli.file_name.equals("")) {
        		    		oos.writeUTF(tli.file_name);
        		    		if (tli.thumnail_byte_array!=null) {
        		    			oos.writeInt(tli.thumnail_byte_array.length);
        			    		oos.write(tli.thumnail_byte_array,0,tli.thumnail_byte_array.length);
        		    		} else {
        		    			oos.writeInt(0);
        		    		}
        	    		}
        	    	}
        	    	oos.flush();
        	    	oos.close();
    			}
    		} catch (FileNotFoundException e) {
    			e.printStackTrace();
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
    	}
    };
    
    private Bitmap readThumnailCache(File vf) {
    	boolean found=false;
    	Bitmap bm=null;
		ThumnailListItem tli=null;
    	for(int i=0;i<mThumnailList.size();i++) {
    		tli=mThumnailList.get(i);
//    		Log.v("","list name="+tli.file_name);
    		if (tli.file_name.equals(vf.getName())) {
    			found=true;
//    			Log.v("","founded name="+vf.getName());
    			break;
    		}
    	}
    	if (found) {
    		if (tli.thumnail_byte_array!=null) 
    			bm=BitmapFactory.decodeByteArray(tli.thumnail_byte_array, 0, tli.thumnail_byte_array.length);
    	} else {
    		tli=new ThumnailListItem();
    		tli.file_name=vf.getName();
    		bm=ThumbnailUtils.createVideoThumbnail(vf.getPath(), MediaStore.Images.Thumbnails.MICRO_KIND);
//    		Log.v("","bm="+bm);
    		if (bm!=null) {
        		ByteArrayOutputStream baos=new ByteArrayOutputStream();
        		bm.compress(CompressFormat.PNG, 50, baos);
        		try {
    				baos.flush();
    	    		baos.close();
    	    		tli.thumnail_byte_array=baos.toByteArray();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    		}
    		mThumnailListModified=true;
    		mThumnailList.add(tli);
    	}
    	mLog.addDebugMsg(1, "I","readThumnailCache File "+vf.getName()+" Bitmap="+bm+", mThumnailListModified="+mThumnailListModified);
    	return bm;
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
					setStartStopBtnEnabled(true);
					refreshOptionMenu();
				}
			},500);
		};
		@Override
		public void notifyRecordingStopped() throws RemoteException {
			mUiHandler.postDelayed(new Runnable(){
				@Override
				public void run() {
					setStartStopBtnEnabled(true);
					setUiEnabled(true);
					refreshOptionMenu();
			    	createDayList();
			        if (mDayListAdapter.getCount()>0) {
			        	Handler hndl=new Handler();
			        	hndl.postDelayed(new Runnable(){
							@Override
							public void run() {
//					    		mDayListView.getChildAt(0).setBackgroundColor(Color.DKGRAY);
								if (mCurrentSelectedDayList.equals("")) {
						    		createFileList(mDayListAdapter.getItem(0).day);
								} else {
						    		createFileList(mCurrentSelectedDayList);
								}
					    		for (int i=0;i<mDayListAdapter.getCount();i++) {
					    			if (mDayListAdapter.getItem(i).day.equals(mCurrentSelectedDayList)) {
//					    				mDayListView.getChildAt(i).setBackgroundColor(Color.DKGRAY);
					    				break;
					    			}
					    		}
							}
			        	}, 100);
			        } else {
			        	mFileListAdapter.clear();
			        }

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
 
}