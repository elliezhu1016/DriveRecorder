package com.sentaroh.android.DriveRecorder;

import java.io.BufferedOutputStream;
import java.io.File;	
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import com.sentaroh.android.DriveRecorder.Log.LogUtil;
import com.sentaroh.android.Utilities.DateUtil;
import com.sentaroh.android.Utilities.MiscUtil;
import com.sentaroh.android.Utilities.NotifyEvent;
import com.sentaroh.android.Utilities.NotifyEvent.NotifyEventListener;
import com.sentaroh.android.Utilities.ThreadCtrl;
import com.sentaroh.android.Utilities.Dialog.CommonDialog;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnVideoSizeChangedListener;
import android.media.MediaScannerConnection.OnScanCompletedListener;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class ActivityVideoPlayer extends FragmentActivity{

    private int mRestartStatus=0;
    private Context mContext=null;
    
    private boolean mApplicationTerminated=false;

    private GlobalParameters mGp=null;
    
    private Handler mUiHandler=null;
    
    private LogUtil mLog=null;
//    private CustomContextMenu mCcMenu=null;
    private CommonDialog mCommonDlg=null;

	private ArrayList<FileListItem> mFileList=new ArrayList<FileListItem>();
	private int mCurrentSelectedPos=0;
	private SurfaceView mSurfaceView=null, mThumnailView=null;;
	private SurfaceHolder mSurfaceHolder=null;
//	private Activity mActivity=null;

	private boolean mIsVideoPlaying=false, mIsVideoPausing=false;
	private boolean mIsVideoReadyToBePlayed=true;
	
	private SeekBar mSbPlayPosition=null;
	private TextView mTvPlayPosition=null;
	private TextView mTvEndPosition=null;
	private ImageButton mIbPrevFile=null;
	private ImageButton mIbStartStop=null;
	private ImageButton mIbNextFile=null;
	private ImageButton mIbDeleteFile=null;
	private ImageButton mIbShare=null;
	private TextView mTvTitle=null;
	private ImageButton mIbArchive=null;
	private ImageButton mIbCapture=null;
	private ImageButton mIbMediaPlayer=null;
//	private LinearLayout mLayoutTop=null;
//	private LinearLayout mLayoutBottom=null;

	private boolean mIsArchiveFolder=false;
	private String mVideoFolder="";
    
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
	public void onConfigurationChanged(final Configuration newConfig) {
	    // Ignore orientation change to keep activity from restarting
	    super.onConfigurationChanged(newConfig);
	    mLog.addDebugMsg(1,"I","onConfigurationChanged Entered, orientation="+newConfig.orientation);
	    Point dsz=new Point();
	    getWindowManager().getDefaultDisplay().getSize(dsz);
//	    Log.v("","x="+dsz.x+", y="+dsz.y);
	    
	    if (mIsVideoPlaying || mIsVideoPausing) {
			if (mGp.settingsVideoPlaybackKeepAspectRatio) {
			    float video_Width = mMediaPlayer.getVideoWidth();
			    float video_Height = mMediaPlayer.getVideoHeight();
			    float ratio_width = dsz.x/video_Width;
			    float ratio_height = dsz.y/video_Height;
			    float aspectratio = video_Width/video_Height;
			    android.view.ViewGroup.LayoutParams layoutParams = mSurfaceView.getLayoutParams();
			    if (ratio_width > ratio_height){
				    layoutParams.width = (int) (dsz.y * aspectratio);
				    layoutParams.height = dsz.y;
			    }else{
			    	layoutParams.width = dsz.x;
			    	layoutParams.height = (int) (dsz.x / aspectratio);
			    }
			    mSurfaceView.setLayoutParams(layoutParams);
			}
	    }
	    if (mThumnailView.isShown()) {
			if (mThumnailBitmap!=null) {
			    float video_Width = mThumnailBitmap.getWidth();
			    float video_Height = mThumnailBitmap.getHeight();
			    float ratio_width = dsz.x/video_Width;
			    float ratio_height = dsz.y/video_Height;
			    float aspectratio = video_Width/video_Height;
			    android.view.ViewGroup.LayoutParams layoutParams = mThumnailView.getLayoutParams();
			    if (ratio_width > ratio_height){
				    layoutParams.width = (int) (dsz.y * aspectratio);
				    layoutParams.height = dsz.y;
			    }else{
			    	layoutParams.width = dsz.x;
			    	layoutParams.height = (int) (dsz.x / aspectratio);
			    }
			    mThumnailView.setLayoutParams(layoutParams);
			}
	    }
	};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.video_player);
        mUiHandler=new Handler();
        mGp=(GlobalParameters) this.getApplication();
        mGp.initSettingParms(this);
        mGp.loadSettingParms(this);
        
        mContext=this.getApplicationContext();
        
        mLog=new LogUtil(mContext, "VideoPlayer", mGp);
        
        mLog.addDebugMsg(1, "I","onCreate entered");
        
//        mCcMenu = new CustomContextMenu(getResources(),getSupportFragmentManager());
        mCommonDlg=new CommonDialog(mContext, getSupportFragmentManager());

//        if (mGp.settingsDeviceOrientationPortrait) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
//        else setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        mTcPlayer=new ThreadCtrl();
        mMediaPlayer = new MediaPlayer();
        
		mSbPlayPosition=(SeekBar)findViewById(R.id.video_player_dlg_played_pos);
		mTvPlayPosition=(TextView)findViewById(R.id.video_player_dlg_played_time);
		mTvEndPosition=(TextView)findViewById(R.id.video_player_dlg_played_endpos);
		mSurfaceView=(SurfaceView)findViewById(R.id.video_player_dlg_video);
		mThumnailView=(SurfaceView)findViewById(R.id.video_player_dlg_thumnail);
		mIbPrevFile=(ImageButton) findViewById(R.id.video_player_dlg_prev);
		mIbStartStop=(ImageButton)findViewById(R.id.video_player_dlg_start_stop);
		mIbNextFile=(ImageButton)findViewById(R.id.video_player_dlg_next);
		mIbDeleteFile=(ImageButton)findViewById(R.id.video_player_dlg_delete);
		mIbShare=(ImageButton)findViewById(R.id.video_player_dlg_share);
		mIbArchive=(ImageButton)findViewById(R.id.video_player_dlg_archive);
		mIbCapture=(ImageButton)findViewById(R.id.video_player_dlg_capture);
		mIbMediaPlayer=(ImageButton)findViewById(R.id.video_player_dlg_start_media_player);
		mTvTitle=(TextView)findViewById(R.id.video_player_dlg_title);
//		mLayoutTop=(LinearLayout)findViewById(R.id.video_player_dlg_top_panel);
//		mLayoutBottom=(LinearLayout)findViewById(R.id.video_player_dlg_bottom_panel);

    };

    @Override
    public void onResume() {
    	super.onResume();
    	mLog.addDebugMsg(1, "I","onResume entered, restartStatus="+mRestartStatus);
    	if (mRestartStatus==1) {
    	} else {
    		initFileList();
			if (mRestartStatus==0) {
				
			} else if (mRestartStatus==2) {
				
			}
	        mRestartStatus=1;
	        
	        setMainViewListener();
    	}
    };
    
    @Override
    public void onPause() {
    	super.onPause();
    	mLog.addDebugMsg(1,"I","onPause entered");
    	if (mIsVideoPlaying && !mIsVideoPausing) {
    		pauseVideoPlaying();
    	}
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
    	mApplicationTerminated=true;
		if (mIsVideoPlaying || mIsVideoPausing) {
			mMediaPlayer.stop();
			mMediaPlayer.reset();
		}
    	mMediaPlayer.release();
    	mLog.flushLog();
    };

	private void initFileList() {
		Intent intent=getIntent();
		String s_fn="";
		if (intent.getExtras()!=null && intent.getExtras().containsKey("archive")) {
			mIsArchiveFolder=getIntent().getBooleanExtra("archive",false);
			mVideoFolder=getIntent().getStringExtra("fd");
			s_fn=getIntent().getStringExtra("fn");
		} else {
			mIsArchiveFolder=true;
			String fp=intent.getDataString().replace("file://", "");
			String nfn="";
			if (fp.lastIndexOf("/")>0) {
				nfn=fp.substring(fp.lastIndexOf("/")+1);
			} else {
				nfn=fp;
			}
			String nfd=fp.replace(nfn,"");
			mVideoFolder=nfd;
		}
		
    	
//    	Log.v("","archive="+mIsArchiveFolder+", fd="+mVideoFolder+", fn="+s_fn);
    	File lf=new File(mVideoFolder);
    	File[] tfl=lf.listFiles();
    	if (tfl!=null && tfl.length>0) {
    		for (int i=0;i<tfl.length;i++) {
    			FileListItem fli=new FileListItem();
    			fli.file_name=tfl[i].getName();
    			fli.file_size=MiscUtil.convertFileSize(tfl[i].length());
    			mFileList.add(fli);	
    		}
    		Collections.sort(mFileList, new Comparator<FileListItem>(){
				@Override
				public int compare(FileListItem lhs, FileListItem rhs) {
					return lhs.file_name.compareToIgnoreCase(rhs.file_name);
				}
    		});
    	}

//    	Log.v("","fp="+s_fp);
		for (int i=0;i<mFileList.size();i++) {
			if (s_fn.equals(mFileList.get(i).file_name)) {
				mCurrentSelectedPos=i;
				break;
			}
		}

	};
	
	public void setMainViewListener() {

        mSurfaceHolder=mSurfaceView.getHolder();
//        mSurfaceHolder.setFormat(PixelFormat.TRANSPARENT);
//        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback(){
			@Override
			public void surfaceCreated(SurfaceHolder holder) {
				mLog.addDebugMsg(1,"I","surfaceCreated entered, playing="+mIsVideoPlaying+", pausing="+mIsVideoPausing);
				if (mIsVideoReadyToBePlayed) {
					mIsVideoReadyToBePlayed=false;
					mIbStartStop.performClick();
				} else {
					if (mIsVideoPausing) {
						mMediaPlayer.setDisplay(mSurfaceHolder);
						mMediaPlayer.seekTo(mMediaPlayer.getCurrentPosition());
					}
				}
			};

			@Override
			public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
				mLog.addDebugMsg(1,"I","surfaceChanged entered, width="+width+", height="+height+
						", playing="+mIsVideoPlaying+", pausing="+mIsVideoPausing);
			};

			@Override
			public void surfaceDestroyed(SurfaceHolder holder) {
				mLog.addDebugMsg(1,"I","surfaceDestroyed entered, playing="+mIsVideoPlaying+", pausing="+mIsVideoPausing);
				if (mIsVideoPlaying) {
					mMediaPlayer.pause();
					mIsVideoPausing=true;
				}
			};
        });

		if ((mCurrentSelectedPos+1)<mFileList.size()) {
			mIbNextFile.setEnabled(true);
			mIbNextFile.setImageResource(R.drawable.next_file_enabled);
		} else {
			mIbNextFile.setEnabled(false);
			mIbNextFile.setImageResource(R.drawable.next_file_disabled);
		}
		if (mCurrentSelectedPos>0) {
			mIbPrevFile.setEnabled(true);
			mIbPrevFile.setImageResource(R.drawable.prev_file_enabled);
		} else {
			mIbPrevFile.setEnabled(false);
			mIbPrevFile.setImageResource(R.drawable.prev_file_disabled);
		}

		mIbDeleteFile.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				NotifyEvent ntfy=new NotifyEvent(mContext);
				ntfy.setListener(new NotifyEventListener(){
					@Override
					public void positiveResponse(Context c, Object[] o) {
						stopVideoPlayer();
						stopMediaPlayer();
						mIsVideoPlaying=false;
						mIsVideoPausing=false;
						mIbStartStop.setImageResource(R.drawable.player_play_enabled);
						mSbPlayPosition.setProgress(0);
						mSbPlayPosition.setEnabled(false);
						
						FileListItem fli=mFileList.get(mCurrentSelectedPos);
						mFileList.remove(mCurrentSelectedPos);
						File lf=new File(mVideoFolder+fli.file_name);
						lf.delete();
						deleteMediaStoreItem(mVideoFolder+fli.file_name);
//						Log.v("","size="+mFileList.size()+", pos="+mCurrentSelectedPos);
						if (mFileList.size()>0) {
							if ((mCurrentSelectedPos+1)>mFileList.size()) mCurrentSelectedPos--;
							showVideoThumnail(mCurrentSelectedPos);
							mTvEndPosition.setText("");
						} else {
							finish();
						}
						if (mGp.housekeepThumnailCache()) mGp.saveThumnailCacheList();
					}
					@Override
					public void negativeResponse(Context c, Object[] o) {
					}
				});
				mCommonDlg.showCommonDialog(true, "W", String.format(
						mContext.getString(R.string.msgs_player_delete_file_confirm),
						mFileList.get(mCurrentSelectedPos).file_name), "", ntfy);
			}
		});

		mIbShare.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
			    Intent sharingIntent = new Intent(Intent.ACTION_SEND);
			    Uri uri = Uri.parse(mVideoFolder+mFileList.get(mCurrentSelectedPos).file_name);
			     
			    sharingIntent.setType("video/mp4");
			    sharingIntent.putExtra(Intent.EXTRA_STREAM, uri);
			    startActivity(Intent.createChooser(sharingIntent,
			    		mContext.getString(R.string.msgs_main_ccmenu_share_title)));
			}
		});

		setCaptureBtnEnabled(false);
		mIbCapture.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
//				if (mIsVideoCapturedEnabled) {
//					mIsVideoCapturedEnabled=false;
//				}
				setCaptureBtnEnabled(false);
				Thread th=new Thread() {
					@Override
					public void run() {
						MediaMetadataRetriever mr=new MediaMetadataRetriever();
						mr.setDataSource(mVideoFolder+mFileList.get(mCurrentSelectedPos).file_name);
//						Log.v("","sb="+mSbPlayPosition.getProgress()+", mp="+mMediaPlayer.getCurrentPosition());
						Bitmap bm=mr.getFrameAtTime(mSbPlayPosition.getProgress()*1000);
						putPicture(bm);
						mUiHandler.post(new Runnable(){
							@Override
							public void run() {
								setCaptureBtnEnabled(true);
							}
						});
					}
				};
				th.start();
			}
		});

		mIbMediaPlayer.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
//				String dir=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()+"/";
//				File lf=new File(dir);
//				File[] list=lf.listFiles();
//				String fp="";
//				if (list!=null && list.length>0) {
//					for (int i=0;i<list.length;i++) {
//						if (list[i].getName().endsWith(".jpg")) {
//							fp=list[i].getPath();
//							break;
//						}
//					}
//				}
				Intent intent = new Intent(android.content.Intent.ACTION_VIEW);
//				if (fp.equals("")) {
//					intent.setDataAndType(Uri.parse("file://"+dir), "image/jpeg");
//				} else {
//					intent.setDataAndType(Uri.parse("file://"+fp), "image/jpeg");
//				}
				intent.setType("image/jpeg");
				startActivity(intent);
			}
		});
		
		if (mIsArchiveFolder) {
			mIbArchive.setImageResource(R.drawable.archive_disabled);
			mIbArchive.setEnabled(false);
		}
		mIbArchive.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				NotifyEvent ntfy=new NotifyEvent(mContext);
				ntfy.setListener(new NotifyEventListener(){
					@Override
					public void positiveResponse(Context c, Object[] o) {
						String fp=mGp.videoRecordDir+mFileList.get(mCurrentSelectedPos).file_name;
						String afp=mGp.videoArchiveDir+mFileList.get(mCurrentSelectedPos).file_name;
						File tlf=new File(mGp.videoArchiveDir);
						if (!tlf.exists()) tlf.mkdirs();
						
				    	File lf=new File(fp);
				    	boolean result=lf.renameTo(new File(afp));
				    	if (result) {
		        			mLog.addLogMsg("I", "File was archived. name="+mFileList.get(mCurrentSelectedPos).file_name);
					        deleteMediaStoreItem(fp);
					    	mFileList.remove(mCurrentSelectedPos);
					    	scanMediaStoreFile(afp);
					    	
							if (mFileList.size()>0) {
								if ((mCurrentSelectedPos+1)>mFileList.size()) mCurrentSelectedPos--;
								showVideoThumnail(mCurrentSelectedPos);
							} else {
								finish();
							}
				    	} else {
				    		mLog.addLogMsg("E", "File can not archived. name="+mFileList.get(mCurrentSelectedPos).file_name);
							mCommonDlg.showCommonDialog(false, "E", 
									  mContext.getString(R.string.msgs_main_ccmenu_file_archive_error), 
									  mFileList.get(mCurrentSelectedPos).file_name, null);
				    	}
					}
					@Override
					public void negativeResponse(Context c, Object[] o) {
					}
				});
				mCommonDlg.showCommonDialog(true, "W", 
						  mContext.getString(R.string.msgs_main_ccmenu_file_archive_file_confirm), 
						  mFileList.get(mCurrentSelectedPos).file_name, ntfy);
			}
		});

		mIbPrevFile.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				stopVideoPlayer();
				stopMediaPlayer();
				mSbPlayPosition.setProgress(0);
				mSbPlayPosition.setEnabled(true);
				mCurrentSelectedPos--;
				prepareVideo(mFileList.get(mCurrentSelectedPos).file_name);
				mIsVideoPlaying=true;
				mIsVideoPausing=false;
				mIbStartStop.setImageResource(R.drawable.player_pause);
				setCaptureBtnEnabled(false);
				
				setNextPrevBtnStatus();
			}
		});
		
		mIbNextFile.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				stopVideoPlayer();
				stopMediaPlayer();
				mSbPlayPosition.setProgress(0);
				mSbPlayPosition.setEnabled(true);
				mCurrentSelectedPos++;
				prepareVideo(mFileList.get(mCurrentSelectedPos).file_name);
				mIsVideoPlaying=true;
				mIsVideoPausing=false;
				mIbStartStop.setImageResource(R.drawable.player_pause);
				setCaptureBtnEnabled(false);
				
				setNextPrevBtnStatus();
			}
		});
		
		mSbPlayPosition.setProgress(0);
		mSbPlayPosition.setOnSeekBarChangeListener(new OnSeekBarChangeListener(){
			@Override
			public void onProgressChanged(SeekBar arg0, int progress, boolean byUser) {
//				Log.v("","seekTo="+progress+", max="+mSbPlayPosition.getMax()+", arg2="+arg2);
				if (byUser) {
					mMediaPlayer.seekTo(progress);
					mTvPlayPosition.setText(getTimePosition(progress));
				}
			}
			@Override
			public void onStartTrackingTouch(SeekBar arg0) {
//				Log.v("","onStartTrackingTouch");
				mMediaPlayer.setVolume(0f, 0f);
			}
			@Override
			public void onStopTrackingTouch(SeekBar arg0) {
//				Log.v("","onStopTrackingTouch");
				mMediaPlayer.setVolume(1.0f, 1.0f);
			}
		});

		mIbStartStop.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
//				Log.v("","mIsVideoPlaying="+mIsVideoPlaying+", mIsVideoPausing="+mIsVideoPausing);
				if (mIsVideoPlaying) {
					pauseVideoPlaying();
				} else {
					//Start
					mIbStartStop.setImageResource(R.drawable.player_pause);
					if (!mIsVideoPausing) {
						mSbPlayPosition.setProgress(0);
						mSbPlayPosition.setEnabled(true);
						prepareVideo(mFileList.get(mCurrentSelectedPos).file_name);
						mIsVideoPlaying=true;
						mIsVideoPausing=false;
						setCaptureBtnEnabled(false);
					} else {
						mSbPlayPosition.setEnabled(true);
//						mMediaPlayer.start();
						mIsVideoPlaying=true;
						mIsVideoPausing=false;
						mTcPlayer.setEnabled();
						startVideoThread();
						setCaptureBtnEnabled(false);
					}
				}
			}
		});
	};

	private void pauseVideoPlaying() {
		stopVideoPlayer();
		mIbStartStop.setImageResource(R.drawable.player_play_enabled);
//		mSbPlayPosition.setEnabled(false);
		mIsVideoPausing=true;
		setCaptureBtnEnabled(true);
	};
	
	private void setCaptureBtnEnabled(boolean p) {
		mIbCapture.setEnabled(p);
		if (p) mIbCapture.setImageResource(R.drawable.capture_enabled);
		else mIbCapture.setImageResource(R.drawable.capture_disabled);
	};
	
	private void putPicture(Bitmap bm) {
		String dir=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()+"/";
		String ftime=DateUtil.convDateTimeTo_YearMonthDayHourMinSec(System.currentTimeMillis())
				.replaceAll("/","-").replaceAll(":","").replaceAll(" ", "_");
		String fn="dr_pic_"+ftime+".jpg";
		final String pfp=dir+fn;
		try {
			FileOutputStream fos=new FileOutputStream(pfp);
			BufferedOutputStream bos=new BufferedOutputStream(fos,4096*256);
			bm.compress(Bitmap.CompressFormat.JPEG, 100, bos);
			bos.flush();
			bos.close();
			mUiHandler.post(new Runnable(){
				@Override
				public void run() {
					Toast toast = Toast.makeText(mContext, 
							mContext.getString(R.string.msgs_player_picture_captured)+pfp, Toast.LENGTH_SHORT);
					toast.show();
				}
			});
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		scanMediaStoreFile(dir+fn);
	};
	
	private Bitmap mThumnailBitmap=null;
	private void showVideoThumnail(int pos) {
		mThumnailView.setVisibility(SurfaceView.VISIBLE);
		if (mIsArchiveFolder) mTvTitle.setText(mContext.getString(R.string.msgs_main_folder_type_archive)+" "+mFileList.get(pos).file_name);
		else mTvTitle.setText(mContext.getString(R.string.msgs_main_folder_type_record)+" "+mFileList.get(pos).file_name);
		mSbPlayPosition.setProgress(0);
		mTvPlayPosition.setText("00:00");
		setNextPrevBtnStatus();

		mThumnailBitmap=ThumbnailUtils.createVideoThumbnail(mVideoFolder+mFileList.get(pos).file_name, 
				MediaStore.Images.Thumbnails.FULL_SCREEN_KIND);

		Canvas canvas=mThumnailView.getHolder().lockCanvas();
		if (mThumnailBitmap!=null) {
			int surfaceView_Width = mThumnailView.getWidth();
		    int surfaceView_Height = mThumnailView.getHeight();
		    float video_Width = mThumnailBitmap.getWidth();
		    float video_Height = mThumnailBitmap.getHeight();
		    float ratio_width = surfaceView_Width/video_Width;
		    float ratio_height = surfaceView_Height/video_Height;
		    float aspectratio = video_Width/video_Height;
		    android.view.ViewGroup.LayoutParams layoutParams = mThumnailView.getLayoutParams();
		    if (ratio_width > ratio_height){
			    layoutParams.width = (int) (surfaceView_Height * aspectratio);
			    layoutParams.height = surfaceView_Height;
		    }else{
		    	layoutParams.width = surfaceView_Width;
		    	layoutParams.height = (int) (surfaceView_Width / aspectratio);
		    }
		    mThumnailView.setLayoutParams(layoutParams);
		    
			Rect f_rect=new Rect(0,0,mThumnailBitmap.getWidth(),mThumnailBitmap.getHeight());
			Rect t_rect=new Rect(0,0,mThumnailView.getWidth()-1,mThumnailView.getHeight()-1);
//			Log.v("","To width="+mThumnailView.getWidth()+", height="+mThumnailView.getHeight());
//			Log.v("","From width="+f_rect.right+", height="+f_rect.bottom);
			Paint paint=new Paint();
			canvas.drawBitmap(mThumnailBitmap, f_rect, t_rect, paint);
		} else {
			canvas.drawColor(Color.BLACK);
		}
		mThumnailView.getHolder().unlockCanvasAndPost(canvas);

		mSurfaceView.setVisibility(SurfaceView.INVISIBLE);
	};
	
	private void stopVideoPlayer() {
		if (mIsVideoPlaying) {
			mMediaPlayer.pause();
			mIsVideoPlaying=false;
			mIsVideoPausing=true;
			stopVideoThread();
		} 
	};
	
	private void setNextPrevBtnStatus() {
		if ((mCurrentSelectedPos+1)<mFileList.size()) {
			mIbNextFile.setEnabled(true);
			mIbNextFile.setImageResource(R.drawable.next_file_enabled);
		} else {
			mIbNextFile.setEnabled(false);
			mIbNextFile.setImageResource(R.drawable.next_file_disabled);
		}
		if (mCurrentSelectedPos>0) {
			mIbPrevFile.setEnabled(true);
			mIbPrevFile.setImageResource(R.drawable.prev_file_enabled);
		} else {
			mIbPrevFile.setEnabled(false);
			mIbPrevFile.setImageResource(R.drawable.prev_file_disabled);
		}
	};
	
	private MediaPlayer mMediaPlayer=null;
	private ThreadCtrl mTcPlayer=null;
	private void prepareVideo(final String fp) {
		mLog.addDebugMsg(1,"I","playVideo entered, fp="+fp+", mIsVideoPlaying="+mIsVideoPlaying);
		if (mIsVideoPlaying) return;
		mTcPlayer.setEnabled();
		mSurfaceView.setVisibility(SurfaceView.VISIBLE);
		mThumnailView.setVisibility(SurfaceView.INVISIBLE);
		try {
			if (mIsArchiveFolder) mTvTitle.setText(mContext.getString(R.string.msgs_main_folder_type_archive)+" "+fp);
			else mTvTitle.setText(mContext.getString(R.string.msgs_main_folder_type_record)+" "+fp);
			  
			mMediaPlayer.setOnBufferingUpdateListener(new OnBufferingUpdateListener(){
				@Override
				public void onBufferingUpdate(MediaPlayer mp, int percent) {
					mLog.addDebugMsg(1,"I", "onBufferingUpdate percent:" + percent);
				}
			});
			mMediaPlayer.setOnCompletionListener(new OnCompletionListener(){
				@Override
				public void onCompletion(MediaPlayer mp) {
					mLog.addDebugMsg(1,"I","onCompletion called");
					mIsVideoPlaying=false;
					mIsVideoPausing=true;
					mMediaPlayer.pause();
					stopVideoThread();
					if (!mApplicationTerminated) {
						mIbStartStop.setImageResource(R.drawable.player_play_enabled);
						setCaptureBtnEnabled(true);
					}
				}
			});
			mMediaPlayer.setOnPreparedListener(new OnPreparedListener(){
				@Override
				public void onPrepared(MediaPlayer mp) {
					mLog.addDebugMsg(1,"I","onPrepared called");
					MediaMetadataRetriever mr=new MediaMetadataRetriever();
					mr.setDataSource(mVideoFolder+fp);
					String br_str=mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
					BigDecimal br=new BigDecimal("0.00");
					if (br_str!=null)  {
						BigDecimal br_a=new BigDecimal(br_str);
						BigDecimal br_b=new BigDecimal(1000*1000);
						br=br_a.divide(br_b,0,BigDecimal.ROUND_HALF_UP);
//						Log.v("","br_str="+br_str+", br_a="+br_a+", br_b="+br_b);
//						BigDecimal dfs1 = new BigDecimal(fs);
//					    BigDecimal dfs2 = new BigDecimal(1024*1024*1024);
//					    BigDecimal dfs3 = new BigDecimal("0.00");
//					    dfs3=dfs1.divide(dfs2,1, BigDecimal.ROUND_HALF_UP);
					}
					
					String wh=" "+mMediaPlayer.getVideoWidth()+" x "+mMediaPlayer.getVideoHeight()+" "+br+"MBPS";
					if (mIsArchiveFolder) mTvTitle.setText(mContext.getString(R.string.msgs_main_folder_type_archive)+" "+fp +" "+wh);
					else mTvTitle.setText(mContext.getString(R.string.msgs_main_folder_type_record)+" "+fp +" "+wh);
					
					mSbPlayPosition.setMax(mMediaPlayer.getDuration());
					mTvEndPosition.setText(getTimePosition(mMediaPlayer.getDuration()));
					if (mGp.settingsVideoPlaybackKeepAspectRatio) {
						int surfaceView_Width = mSurfaceView.getWidth();
					    int surfaceView_Height = mSurfaceView.getHeight();
					    float video_Width = mMediaPlayer.getVideoWidth();
					    float video_Height = mMediaPlayer.getVideoHeight();
					    float ratio_width = surfaceView_Width/video_Width;
					    float ratio_height = surfaceView_Height/video_Height;
					    float aspectratio = video_Width/video_Height;
					    android.view.ViewGroup.LayoutParams layoutParams = mSurfaceView.getLayoutParams();
					    if (ratio_width > ratio_height){
						    layoutParams.width = (int) (surfaceView_Height * aspectratio);
						    layoutParams.height = surfaceView_Height;
					    }else{
					    	layoutParams.width = surfaceView_Width;
					    	layoutParams.height = (int) (surfaceView_Width / aspectratio);
					    }
//					    Log.v("","lp_w="+layoutParams.width+", lp_h="+layoutParams.height+", ratio="+aspectratio);
					    mSurfaceView.setLayoutParams(layoutParams);
//					    mThumnailView.setLayoutParams(layoutParams);
					} else {
//					    mMediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
					    mMediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
					}

					mIsVideoPlaying=true;
					mIsVideoPausing=false;
					startVideoThread();
				}
			});
			mMediaPlayer.setOnVideoSizeChangedListener(new OnVideoSizeChangedListener(){
				@Override
				public void onVideoSizeChanged(MediaPlayer mp, int video_width, int video_height) {
					mLog.addDebugMsg(1,"I","onVideoSizeChanged called, width="+video_width+", height="+video_height);
				}
			});

			mIsVideoPlaying=true;
			mMediaPlayer.setDataSource(mVideoFolder+fp);
			mMediaPlayer.setDisplay(mSurfaceHolder);
			mMediaPlayer.prepareAsync();
			
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	};
	
	private String getTimePosition(int cp) {
		int mm=cp/1000/60;
		int ss=(cp-(mm*1000*60))/1000;
		return String.format("%02d",mm)+":"+String.format("%02d",ss);
	};

	private Thread mPlayerThread=null;
	
	private void stopVideoThread() {
		mTcPlayer.setDisabled();
		synchronized(mTcPlayer) {
			mTcPlayer.notify();
		}
		try {
			if (mPlayerThread!=null) mPlayerThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	};
	
	private void startVideoThread() {
		mPlayerThread=new Thread() {
			@Override
			public void run() {
				mMediaPlayer.start();
				final int interval=100;
				while (!mIsVideoPausing && mIsVideoPlaying) {
					try {
						if (!mIsVideoPausing && mIsVideoPlaying) {
							mUiHandler.post(new Runnable(){
								@Override
								public void run() {
									if (!mIsVideoPausing && mIsVideoPlaying) {
										int cp=mMediaPlayer.getCurrentPosition();
//										int cp=mSbPlayPosition.getProgress();
										mSbPlayPosition.setProgress(cp+interval);
										mTvPlayPosition.setText(getTimePosition(cp+interval));
//										Log.v("","cp="+cp);
									}
								}
							});
						}
						synchronized(mTcPlayer) {
							mTcPlayer.wait(interval);
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					if (!mTcPlayer.isEnabled()) {
						mLog.addDebugMsg(1,"I", "startVideoThread cancelled");
						break;
					} else {
//						mSbPlayPosition.setProgress(mSbPlayPosition.getMax());
					}
				}
				mLog.addDebugMsg(1,"I", "startVideoThread expired");
			}
		};
		mPlayerThread.setName("Player");
		mPlayerThread.start();		
	};

	private void stopMediaPlayer() {
		mIsVideoPlaying=false;
		mIsVideoPausing=false;
		try {
			mMediaPlayer.reset();
//			mMediaPlayer.release();
		} catch(IllegalStateException e) {
		}
	};

    private void scanMediaStoreFile(String fp) {
    	String[] paths = new String[] {fp};
    	MediaScannerConnection.scanFile(getApplicationContext(), paths, null, mOnScanCompletedListener);
    };
    
	private OnScanCompletedListener mOnScanCompletedListener=new OnScanCompletedListener(){
		@Override
		public void onScanCompleted(String path, Uri uri) {
			mLog.addDebugMsg(1,"I", "Scan completed path="+path+", uri="+uri);
		}
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


}

