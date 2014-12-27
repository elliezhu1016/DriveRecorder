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

import java.io.File;
import java.util.ArrayList;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.sentaroh.android.DriveRecorder.GlobalParameters;
import com.sentaroh.android.DriveRecorder.R;
import com.sentaroh.android.Utilities.LocalMountPoint;
import com.sentaroh.android.Utilities.ZipUtil;
import com.sentaroh.android.Utilities.NotifyEvent;
import com.sentaroh.android.Utilities.NotifyEvent.NotifyEventListener;
import com.sentaroh.android.Utilities.ThreadCtrl;
import com.sentaroh.android.Utilities.Dialog.CommonDialog;
import com.sentaroh.android.Utilities.Dialog.MessageDialogFragment;
import com.sentaroh.android.Utilities.Dialog.ProgressBarDialogFragment;
import com.sentaroh.android.contextbutton.ContextButtonUtil;

public class LogFileListDialogFragment extends DialogFragment{
	private final static boolean DEBUG_ENABLE=false;
	private final static String APPLICATION_TAG="LogFileManagement";

	private Dialog mDialog=null;
	private boolean mTerminateRequired=true;
	private LogFileListDialogFragment mFragment=null;
	private String mDialogTitle=null;
	private GlobalParameters mGlblParms=null;
	
	private LogFileListAdapter mLogFileManagementAdapter=null;
	
	private Context mContext=null;
	
	private Handler mUiHandler=null;

	private ArrayList<LogFileListItem> mLogFileList=null;
	
	public static LogFileListDialogFragment newInstance(boolean retainInstance, String title) {
		if (DEBUG_ENABLE) Log.v(APPLICATION_TAG,"newInstance");
		LogFileListDialogFragment frag = new LogFileListDialogFragment();
        Bundle bundle = new Bundle();
        bundle.putBoolean("retainInstance", retainInstance);
        bundle.putString("title", title);
//        bundle.putString("msgtext", msgtext);
        frag.setArguments(bundle);
        return frag;
    }

	public LogFileListDialogFragment() {
		if (DEBUG_ENABLE) Log.v(APPLICATION_TAG,"Constructor(Default)");
	}; 
	
	@Override
	public void onAttach(Activity activity) {
	    super.onAttach(activity);
	    if (DEBUG_ENABLE) Log.v(APPLICATION_TAG,"onAttach");
	};

	@Override
	public void onSaveInstanceState(Bundle outState) {  
		super.onSaveInstanceState(outState);
		if (DEBUG_ENABLE) Log.v(APPLICATION_TAG,"onSaveInstanceState");
		if(outState.isEmpty()){
	        outState.putBoolean("WORKAROUND_FOR_BUG_19917_KEY", true);
	    }
    	saveViewContents();
	};  
	
	@Override
	public void onConfigurationChanged(final Configuration newConfig) {
	    // Ignore orientation change to keep activity from restarting
	    super.onConfigurationChanged(newConfig);
	    if (DEBUG_ENABLE) Log.v(APPLICATION_TAG,"onConfigurationChanged");

	    reInitViewWidget();
	};

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
	    super.onActivityCreated(savedInstanceState);
	    if (DEBUG_ENABLE) Log.v(APPLICATION_TAG,"onActivityCreated");
	};
	
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    	if (DEBUG_ENABLE) Log.v(APPLICATION_TAG,"onCreateView");
    	View view=super.onCreateView(inflater, container, savedInstanceState);
    	return view;
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        if (DEBUG_ENABLE) Log.v(APPLICATION_TAG,"onCreate");
        mUiHandler=new Handler();
    	mFragment=this;
        if (!mTerminateRequired) {
            mGlblParms=(GlobalParameters)getActivity().getApplication();

            Bundle bd=getArguments();
            setRetainInstance(bd.getBoolean("retainInstance"));
            mDialogTitle=bd.getString("title");
        	mContext=getActivity().getApplicationContext();
        	
        	mLogFileList=LogUtil.createLogFileList(mGlblParms);
        }
    };
    
	@Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
    	if (DEBUG_ENABLE) Log.v(APPLICATION_TAG,"onCreateDialog");

    	mDialog=new Dialog(getActivity());
		mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		mDialog.setCanceledOnTouchOutside(false);

		if (!mTerminateRequired) {
			initViewWidget();
		}
		
        return mDialog;
    };
    
	@Override
	public void onStart() {
    	CommonDialog.setDlgBoxSizeLimit(mDialog,true);
	    super.onStart();
	    if (DEBUG_ENABLE) Log.v(APPLICATION_TAG,"onStart");
	    if (mTerminateRequired) mDialog.cancel();
	    else {
	    	mDialog.setOnKeyListener(new OnKeyListener(){
    	        @Override
	    	    public boolean onKey ( DialogInterface dialog , int keyCode , KeyEvent event ){
	    	        // disable search button action
	    	        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction()==KeyEvent.ACTION_DOWN){
	    	        	if (mLogFileManagementAdapter.isShowCheckBox()) {
		    	        	for(int i=0;i<mLogFileManagementAdapter.getCount();i++) {
		    	        		mLogFileManagementAdapter.getItem(i).isChecked=false;
		    	        	}
		    	        	mLogFileManagementAdapter.setShowCheckBox(false);
		    	        	mLogFileManagementAdapter.notifyDataSetChanged();
		    	        	setContextButtonNormalMode(mLogFileManagementAdapter);
		    	        	return true;
	    	        	}
	    	        }
	    	        return false;
	    	    }
	    	});
	    }
	};
	
	@Override
	public void onCancel(DialogInterface di) {
		if (DEBUG_ENABLE) Log.v(APPLICATION_TAG,"onCancel");
		mFragment.dismiss();
		super.onCancel(di);
	};
	
	@Override
	public void onDismiss(DialogInterface di) {
		if (DEBUG_ENABLE) Log.v(APPLICATION_TAG,"onDismiss");
		super.onDismiss(di);
	};

	@Override
	public void onStop() {
	    super.onStop();
	    if (DEBUG_ENABLE) Log.v(APPLICATION_TAG,"onStop");
	};
	
	@Override
	public void onDestroyView() {
		if (DEBUG_ENABLE) Log.v(APPLICATION_TAG,"onDestroyView");
	    if (getDialog() != null && getRetainInstance())
	        getDialog().setDismissMessage(null);
	    super.onDestroyView();
	};
	
	@Override
	public void onDetach() {
	    super.onDetach();
	    if (DEBUG_ENABLE) Log.v(APPLICATION_TAG,"onDetach");
	};


    private void reInitViewWidget() {
    	if (DEBUG_ENABLE) Log.v(APPLICATION_TAG,"reInitViewWidget");
    	if (!mTerminateRequired) {
    		Handler hndl=new Handler();
    		hndl.post(new Runnable(){
				@Override
				public void run() {
		        	saveViewContents();
		        	initViewWidget();
		        	restoreViewContents();
		        	if (mLogFileManagementAdapter.isAnyItemSelected()) {
		        		setContextButtonSelecteMode(mLogFileManagementAdapter);
		        	} else {
		        		setContextButtonNormalMode(mLogFileManagementAdapter);
		        	}

				}
    		});
    	}
    };
    
    private void saveViewContents() {
    	
    };
    
    private void restoreViewContents() {
    	
    };
    
    private void initViewWidget() {
    	if (DEBUG_ENABLE) Log.v(APPLICATION_TAG,"initViewWidget");

    	mDialog.setContentView(R.layout.log_file_list_dlg);
    	
    	final TextView dlg_title=(TextView)mDialog.findViewById(R.id.log_file_list_dlg_title);
    	dlg_title.setText(mDialogTitle);
    	final ImageButton dlg_done=(ImageButton)mDialog.findViewById(R.id.log_file_list_dlg_done);
    	dlg_done.setVisibility(ImageButton.GONE);
    	
    	final ListView lv_log_file=(ListView)mDialog.findViewById(R.id.log_file_list_dlg_log_listview);
    	final Button btn_close=(Button)mDialog.findViewById(R.id.log_file_list_dlg_log_close);
    	
    	NotifyEvent ntfy_cb_listener=new NotifyEvent(mContext);
    	ntfy_cb_listener.setListener(new NotifyEventListener(){
			@Override
			public void positiveResponse(Context c, Object[] o) {
				if (mLogFileManagementAdapter.isShowCheckBox()) {
//		        	if (mLogFileManagementAdapter.isAnyItemSelected()) {
//		        		setContextButtonSelecteMode(mLogFileManagementAdapter);
//		        	} else {
//		        		setContextButtonNormalMode(mLogFileManagementAdapter);
//		        	}
					setContextButtonSelecteMode(mLogFileManagementAdapter);
				}
			};

			@Override
			public void negativeResponse(Context c, Object[] o) {}
    	});
    	
    	mLogFileManagementAdapter=
    				new LogFileListAdapter(mContext, R.layout.log_file_list_item,mLogFileList,  
    						ntfy_cb_listener);
    	lv_log_file.setAdapter(mLogFileManagementAdapter);
    	
    	setContextButtonListener();
    	setContextButtonNormalMode(mLogFileManagementAdapter);
    	
    	lv_log_file.setOnItemClickListener(new OnItemClickListener(){
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int pos, long arg3) {
				if (mLogFileManagementAdapter.getItem(0).log_file_name==null) return;
				if (mLogFileManagementAdapter.isShowCheckBox()) {
					mLogFileManagementAdapter.getItem(pos).isChecked=
							!mLogFileManagementAdapter.getItem(pos).isChecked;
					mLogFileManagementAdapter.notifyDataSetChanged();
//		        	if (mLogFileManagementAdapter.isAnyItemSelected()) {
//		        		setContextButtonSelecteMode(mLogFileManagementAdapter);
//		        	} else {
//		        		setContextButtonNormalMode(mLogFileManagementAdapter);
//		        	}
		        	setContextButtonSelecteMode(mLogFileManagementAdapter);
				} else {
					showLogFile(mLogFileManagementAdapter,pos);
				}
			}
    	});
    	
    	lv_log_file.setOnItemLongClickListener(new OnItemLongClickListener(){
			@Override
			public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int pos, long arg3) {
				if (mLogFileManagementAdapter.isEmptyAdapter()) return true;
				if (!mLogFileManagementAdapter.getItem(pos).isChecked) {
					if (mLogFileManagementAdapter.isAnyItemSelected()) {
						int down_sel_pos=-1, up_sel_pos=-1;
						int tot_cnt=mLogFileManagementAdapter.getCount();
						if (pos+1<=tot_cnt) {
							for(int i=pos+1;i<tot_cnt;i++) {
								if (mLogFileManagementAdapter.getItem(i).isChecked) {
									up_sel_pos=i;
									break;
								}
							}
						}
						if (pos>0) {
							for(int i=pos;i>=0;i--) {
								if (mLogFileManagementAdapter.getItem(i).isChecked) {
									down_sel_pos=i;
									break;
								}
							}
						}
//						Log.v("","up="+up_sel_pos+", down="+down_sel_pos);
						if (up_sel_pos!=-1 && down_sel_pos==-1) {
							for (int i=pos;i<up_sel_pos;i++) 
								mLogFileManagementAdapter.getItem(i).isChecked=true;
						} else if (up_sel_pos!=-1 && down_sel_pos!=-1) {
							for (int i=down_sel_pos+1;i<up_sel_pos;i++) 
								mLogFileManagementAdapter.getItem(i).isChecked=true;
						} else if (up_sel_pos==-1 && down_sel_pos!=-1) {
							for (int i=down_sel_pos+1;i<=pos;i++) 
								mLogFileManagementAdapter.getItem(i).isChecked=true;
						}
						mLogFileManagementAdapter.notifyDataSetChanged();
					} else {
						mLogFileManagementAdapter.setShowCheckBox(true);
						mLogFileManagementAdapter.getItem(pos).isChecked=true;
						mLogFileManagementAdapter.notifyDataSetChanged();
					}
					setContextButtonSelecteMode(mLogFileManagementAdapter);
				}
				return true;
			}
    	});

    	btn_close.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View arg0) {
				mFragment.dismiss();
			}
    	});
    	
//    	CommonDialog.setDlgBoxSizeLimit(mDialog, true);
    };

	private void setContextButtonListener() {
		LinearLayout ll_prof=(LinearLayout) mDialog.findViewById(R.id.log_context_view);
        ImageButton ib_delete=(ImageButton)ll_prof.findViewById(R.id.log_context_button_delete);
        ImageButton ib_share=(ImageButton)ll_prof.findViewById(R.id.log_context_button_share);
        ImageButton ib_rotate_log=(ImageButton)ll_prof.findViewById(R.id.log_context_button_rotate_log);
        ImageButton ib_select_all=(ImageButton)ll_prof.findViewById(R.id.log_context_button_select_all);
        ImageButton ib_unselect_all=(ImageButton)ll_prof.findViewById(R.id.log_context_button_unselect_all);
    	final ImageButton dlg_done=(ImageButton)mDialog.findViewById(R.id.log_file_list_dlg_done);
    	
    	dlg_done.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				mLogFileManagementAdapter.setAllItemChecked(false);
				mLogFileManagementAdapter.setShowCheckBox(false);
				mLogFileManagementAdapter.notifyDataSetChanged();
				setContextButtonNormalMode(mLogFileManagementAdapter);
			}
        });
        
        ib_delete.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				confirmDeleteLogFile(mLogFileManagementAdapter);
			}
        });
        ContextButtonUtil.setButtonLabelListener(mContext, ib_delete, 
        		mContext.getString(R.string.msgs_log_file_list_label_delete));

        ib_share.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				sendLogFile(mLogFileManagementAdapter);
			}
        });
        ContextButtonUtil.setButtonLabelListener(mContext, ib_share, 
        		mContext.getString(R.string.msgs_log_file_list_label_share));

        ib_rotate_log.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				rotateLogFile(mLogFileManagementAdapter);
			}
        });
        ContextButtonUtil.setButtonLabelListener(mContext, ib_rotate_log, 
        		mContext.getString(R.string.msgs_log_file_list_label_rotate_log));
        
        ib_select_all.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				mLogFileManagementAdapter.setAllItemChecked(true);
				mLogFileManagementAdapter.setShowCheckBox(true);
				setContextButtonSelecteMode(mLogFileManagementAdapter);
			}
        });
        ContextButtonUtil.setButtonLabelListener(mContext, ib_select_all, 
        		mContext.getString(R.string.msgs_log_file_list_label_select_all));
        
        ib_unselect_all.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				mLogFileManagementAdapter.setAllItemChecked(false);
//				mLogFileManagementAdapter.setShowCheckBox(false);
//				setContextButtonNormalMode(mLogFileManagementAdapter);
			}
        });
        ContextButtonUtil.setButtonLabelListener(mContext, ib_unselect_all, 
        		mContext.getString(R.string.msgs_log_file_list_label_unselect_all));

	};

	private void setContextButtonSelecteMode(LogFileListAdapter lfm_adapter) {
		final TextView dlg_title=(TextView)mDialog.findViewById(R.id.log_file_list_dlg_title);
    	String sel=""+lfm_adapter.getItemSelectedCount()+"/"+lfm_adapter.getCount();
    	dlg_title.setText(sel);

    	final ImageButton dlg_done=(ImageButton)mDialog.findViewById(R.id.log_file_list_dlg_done);
    	dlg_done.setVisibility(ImageButton.VISIBLE);
		
		LinearLayout ll_prof=(LinearLayout) mDialog.findViewById(R.id.log_context_view);
		LinearLayout ll_delete=(LinearLayout)ll_prof.findViewById(R.id.log_context_button_delete_view);
		LinearLayout ll_share=(LinearLayout)ll_prof.findViewById(R.id.log_context_button_share_view);
		LinearLayout ll_rotate_log=(LinearLayout)ll_prof.findViewById(R.id.log_context_button_rotate_log_view);
		LinearLayout ll_select_all=(LinearLayout)ll_prof.findViewById(R.id.log_context_button_select_all_view);
		LinearLayout ll_unselect_all=(LinearLayout)ll_prof.findViewById(R.id.log_context_button_unselect_all_view);

		ll_rotate_log.setVisibility(LinearLayout.GONE);
		
		boolean deletable_log_selected=false;
		for(int i=0;i<lfm_adapter.getCount();i++) {
			if (lfm_adapter.getItem(i).isChecked && !lfm_adapter.getItem(i).isCurrentLogFile) {
				deletable_log_selected=true;
				break;
			}
		}
		if (deletable_log_selected) ll_delete.setVisibility(LinearLayout.VISIBLE);
		else ll_delete.setVisibility(LinearLayout.GONE);
		
		if (lfm_adapter.getItemSelectedCount()>0) ll_share.setVisibility(LinearLayout.VISIBLE);
		else ll_share.setVisibility(LinearLayout.GONE);
        
        ll_select_all.setVisibility(LinearLayout.VISIBLE);
        if (lfm_adapter.isAnyItemSelected()) ll_unselect_all.setVisibility(LinearLayout.VISIBLE);
        else ll_unselect_all.setVisibility(LinearLayout.GONE);
	};

	private void setContextButtonNormalMode(LogFileListAdapter lfm_adapter) {
		final TextView dlg_title=(TextView)mDialog.findViewById(R.id.log_file_list_dlg_title);
		dlg_title.setText(mDialogTitle);

    	final ImageButton dlg_done=(ImageButton)mDialog.findViewById(R.id.log_file_list_dlg_done);
    	dlg_done.setVisibility(ImageButton.GONE);

		LinearLayout ll_prof=(LinearLayout) mDialog.findViewById(R.id.log_context_view);
		LinearLayout ll_delete=(LinearLayout)ll_prof.findViewById(R.id.log_context_button_delete_view);
		LinearLayout ll_share=(LinearLayout)ll_prof.findViewById(R.id.log_context_button_share_view);
		LinearLayout ll_rotate_log=(LinearLayout)ll_prof.findViewById(R.id.log_context_button_rotate_log_view);
		LinearLayout ll_select_all=(LinearLayout)ll_prof.findViewById(R.id.log_context_button_select_all_view);
		LinearLayout ll_unselect_all=(LinearLayout)ll_prof.findViewById(R.id.log_context_button_unselect_all_view);

		ll_delete.setVisibility(LinearLayout.GONE);
		ll_share.setVisibility(LinearLayout.GONE);
        
		ll_rotate_log.setVisibility(LinearLayout.VISIBLE);
		
    	if (lfm_adapter.isEmptyAdapter()) {
            ll_select_all.setVisibility(LinearLayout.GONE);
            ll_unselect_all.setVisibility(LinearLayout.GONE);
    	} else {
            ll_select_all.setVisibility(LinearLayout.VISIBLE);
            ll_unselect_all.setVisibility(LinearLayout.GONE);
    	}
	};

    private void showLogFile(LogFileListAdapter lfm_adapter, int pos) {
		Intent intent = new Intent(android.content.Intent.ACTION_VIEW);
		intent.setDataAndType(Uri.parse("file://"+lfm_adapter.getItem(pos).log_file_path), "text/plain");
		startActivity(intent);
    };

    private void sendLogFile(final LogFileListAdapter lfm_adapter) {
		final String zip_file_name=mGlblParms.settingsLogFileDir+"log.zip";
		
		int no_of_files=0;
		for (int i=0;i<lfm_adapter.getCount();i++) {
			if (lfm_adapter.getItem(i).isChecked) no_of_files++;
		}
		final String[] file_name=new String[no_of_files];
		int files_pos=0;
		for (int i=0;i<lfm_adapter.getCount();i++) {
			if (lfm_adapter.getItem(i).isChecked) {
				file_name[files_pos]=lfm_adapter.getItem(i).log_file_path;
				files_pos++;
			}
		}
		final ThreadCtrl tc=new ThreadCtrl();
		NotifyEvent ntfy=new NotifyEvent(mContext);
		ntfy.setListener(new NotifyEventListener(){
			@Override
			public void positiveResponse(Context c, Object[] o) {
			}
			@Override
			public void negativeResponse(Context c, Object[] o) {
				tc.setDisabled();
			}
		});

		final ProgressBarDialogFragment pbdf=ProgressBarDialogFragment.newInstance(
				mContext.getString(R.string.msgs_log_file_list_dlg_send_zip_file_creating), 
				"",
				mContext.getString(R.string.msgs_common_dialog_cancel),
				mContext.getString(R.string.msgs_common_dialog_cancel));
		pbdf.showDialog(getFragmentManager(), pbdf, ntfy,true);
		Thread th=new Thread() {
			@Override
			public void run() {
				File lf=new File(zip_file_name);
				lf.delete();
				String[] lmp=LocalMountPoint.convertFilePathToMountpointFormat(mContext, file_name[0]);
				ZipUtil.createZipFile(mContext, tc,pbdf,zip_file_name,lmp[0],file_name);
				if (tc.isEnabled()) {
				    Intent intent=new Intent();
				    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				    intent.setAction(Intent.ACTION_SEND);  
//				    intent.setType("message/rfc822");  
//				    intent.setType("text/plain");
				    intent.setType("application/zip");
				    intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(lf)); 
				    mFragment.getActivity().startActivity(intent);

				    mUiHandler.post(new Runnable(){
						@Override
						public void run() {
							lfm_adapter.setAllItemChecked(false);
							lfm_adapter.setShowCheckBox(false);
							lfm_adapter.notifyDataSetChanged();
							setContextButtonNormalMode(lfm_adapter);
						}
				    });
				} else {
					lf.delete();

					MessageDialogFragment mdf =MessageDialogFragment.newInstance(false, "W",
							mContext.getString(R.string.msgs_log_file_list_dlg_send_zip_file_cancelled),
			        		"");
			        mdf.showDialog(mFragment.getFragmentManager(), mdf, null);

				}
				pbdf.dismiss();
			};
		};
		th.start();
    };
    
    private void confirmDeleteLogFile(final LogFileListAdapter lfm_adapter) {
    	String delete_list="",sep="";
    	final ArrayList<String> file_path_list=new ArrayList<String>();
    	for (int i=0;i<lfm_adapter.getCount();i++) {
    		LogFileListItem item=lfm_adapter.getItem(i);
    		if (item.isChecked && !item.isCurrentLogFile) {
    			delete_list+=sep+item.log_file_name;
    			sep="\n";
    			file_path_list.add(item.log_file_path);
    		}
    	}
    	
    	NotifyEvent ntfy=new NotifyEvent(null);
    	ntfy.setListener(new NotifyEventListener(){
			@Override
			public void positiveResponse(Context c, Object[] o) {
				for (int i=0;i<file_path_list.size();i++) {
					File lf=new File(file_path_list.get(i));
					lf.delete();
				}
				
				lfm_adapter.setAllItemChecked(false);
				lfm_adapter.setShowCheckBox(false);
				mLogFileList=LogUtil.createLogFileList(mGlblParms);
				lfm_adapter.replaceDataList(mLogFileList);
				lfm_adapter.notifyDataSetChanged();
				setContextButtonNormalMode(lfm_adapter);

			}

			@Override
			public void negativeResponse(Context c, Object[] o) {}
    	});
        MessageDialogFragment cdf =MessageDialogFragment.newInstance(true, "W",
        		mContext.getString(R.string.msgs_log_file_list_delete_confirm_msg),
        		delete_list);
        cdf.showDialog(mFragment.getFragmentManager(),cdf,ntfy);
    };

    private void rotateLogFile(final LogFileListAdapter lfm_adapter) {
    	LogUtil.rotateLogFile(mContext);

    	mUiHandler.postDelayed(new Runnable(){
			@Override
			public void run() {
				lfm_adapter.setAllItemChecked(false);
				lfm_adapter.setShowCheckBox(false);
				mLogFileList=LogUtil.createLogFileList(mGlblParms);
				lfm_adapter.replaceDataList(mLogFileList);
				lfm_adapter.notifyDataSetChanged();
				setContextButtonNormalMode(lfm_adapter);
			}
    	},100);
    };

    public void showDialog(FragmentManager fm, Fragment frag, GlobalParameters gp) {
    	if (DEBUG_ENABLE) Log.v(APPLICATION_TAG,"showDialog");
    	mTerminateRequired=false;
    	mGlblParms=gp;
	    FragmentTransaction ft = fm.beginTransaction();
	    ft.add(frag,null);
	    ft.commitAllowingStateLoss();
//    	show(fm, APPLICATION_TAG);
    };


}
