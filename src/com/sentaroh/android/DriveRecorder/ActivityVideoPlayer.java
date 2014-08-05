package com.sentaroh.android.DriveRecorder;

import java.io.File;	
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import com.sentaroh.android.DriveRecorder.Log.LogUtil;
import com.sentaroh.android.Utilities.MiscUtil;
import com.sentaroh.android.Utilities.NotifyEvent;
import com.sentaroh.android.Utilities.NotifyEvent.NotifyEventListener;
import com.sentaroh.android.Utilities.ThreadCtrl;
import com.sentaroh.android.Utilities.Dialog.CommonDialog;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnVideoSizeChangedListener;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class ActivityVideoPlayer extends FragmentActivity{

    private int mRestartStatus=0;
    private Context mContext=null;

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
	private ImageButton mIbPrevFile=null;
	private ImageButton mIbStartStop=null;
	private ImageButton mIbNextFile=null;
	private ImageButton mIbDeleteFile=null;
	private TextView mTvTitle=null;
//	private LinearLayout mLayoutTop=null;
//	private LinearLayout mLayoutBottom=null;

    
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

        if (mGp.settingsDeviceOrientationPortrait) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        else setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        mTcPlayer=new ThreadCtrl();
        mMediaPlayer = new MediaPlayer();
        
		mSbPlayPosition=(SeekBar)findViewById(R.id.video_player_dlg_played_pos);
		mTvPlayPosition=(TextView)findViewById(R.id.video_player_dlg_played_time);
		mSurfaceView=(SurfaceView)findViewById(R.id.video_player_dlg_video);
		mThumnailView=(SurfaceView)findViewById(R.id.video_player_dlg_thumnail);
		mIbPrevFile=(ImageButton) findViewById(R.id.video_player_dlg_prev);
		mIbStartStop=(ImageButton)findViewById(R.id.video_player_dlg_start_stop);
		mIbNextFile=(ImageButton)findViewById(R.id.video_player_dlg_next);
		mIbDeleteFile=(ImageButton)findViewById(R.id.video_player_dlg_delete);
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
		if (mIsVideoPlaying || mIsVideoPausing) {
			mMediaPlayer.stop();
			mMediaPlayer.reset();
		}
    	mMediaPlayer.release();
    	mLog.flushLog();
    };

	private void initFileList() {
    	File lf=new File(mGp.videoFileDir);
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

    	String s_fp=getIntent().getStringExtra("fp");
//    	Log.v("","fp="+s_fp);
		for (int i=0;i<mFileList.size();i++) {
			if (s_fp.equals(mFileList.get(i).file_name)) {
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
				mLog.addDebugMsg(1,"I","surfaceCreated entered");
				if (mIsVideoReadyToBePlayed) {
					mIsVideoReadyToBePlayed=false;
					mIbStartStop.performClick();
//					mUiHandler.postDelayed(new Runnable(){
//						@Override
//						public void run() {
//						}
//					}, 500);
				}
			}

			@Override
			public void surfaceChanged(SurfaceHolder holder, int format,
					int width, int height) {
				mLog.addDebugMsg(1,"I","surfaceChanged entered, width="+width+", height="+height);
			}

			@Override
			public void surfaceDestroyed(SurfaceHolder holder) {
				mLog.addDebugMsg(1,"I","surfaceDestroyed entered");
				if (mIsVideoPlaying) {
					mMediaPlayer.stop();
					mIsVideoPausing=true;
				}
			}
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
						File lf=new File(mGp.videoFileDir+fli.file_name);
						lf.delete();
						
//						Log.v("","size="+mFileList.size()+", pos="+mCurrentSelectedPos);
						if (mFileList.size()>0) {
							if ((mCurrentSelectedPos+1)>mFileList.size()) mCurrentSelectedPos--;
							showVideoThumnail(mCurrentSelectedPos);
						} else {
							finish();
						}
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

		mIbPrevFile.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				stopVideoPlayer();
				stopMediaPlayer();
				mSbPlayPosition.setProgress(0);
				mSbPlayPosition.setEnabled(true);
				mCurrentSelectedPos--;
				playVideo(mFileList.get(mCurrentSelectedPos).file_name);
				mIsVideoPlaying=true;
				mIsVideoPausing=false;
				mIbStartStop.setImageResource(R.drawable.player_pause);
				
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
				playVideo(mFileList.get(mCurrentSelectedPos).file_name);
				mIsVideoPlaying=true;
				mIsVideoPausing=false;
				mIbStartStop.setImageResource(R.drawable.player_pause);
				
				setNextPrevBtnStatus();
				
			}
		});
		
		mSbPlayPosition.setProgress(0);
		mSbPlayPosition.setOnSeekBarChangeListener(new OnSeekBarChangeListener(){
			@Override
			public void onProgressChanged(SeekBar arg0, int progress, boolean arg2) {
				if (mIsVideoPlaying) {
//					Log.v("","seekTo="+progress+", max="+mSbPlayPosition.getMax()+", arg2="+arg2);
					if (arg2) mMediaPlayer.seekTo(progress);
				} 
			}
			@Override
			public void onStartTrackingTouch(SeekBar arg0) {
//				Log.v("","onStartTrackingTouch");
			}
			@Override
			public void onStopTrackingTouch(SeekBar arg0) {
//				Log.v("","onStopTrackingTouch");
			}
		});

		mIbStartStop.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				Log.v("","mIsVideoPlaying="+mIsVideoPlaying+", mIsVideoPausing="+mIsVideoPausing);
				if (mIsVideoPlaying) {
					stopVideoPlayer();
					mIbStartStop.setImageResource(R.drawable.player_play_enabled);
					mSbPlayPosition.setEnabled(false);
					mIsVideoPausing=true;
				} else {
					//Start
					mIbStartStop.setImageResource(R.drawable.player_pause);
					if (!mIsVideoPausing) {
						mSbPlayPosition.setProgress(0);
						mSbPlayPosition.setEnabled(true);
						playVideo(mFileList.get(mCurrentSelectedPos).file_name);
						mIsVideoPlaying=true;
						mIsVideoPausing=false;
					} else {
						mSbPlayPosition.setEnabled(true);
//						mMediaPlayer.start();
						mIsVideoPlaying=true;
						mIsVideoPausing=false;
						mTcPlayer.setEnabled();
						startVideo();
					}
				}
			}
		});
	};

	@SuppressWarnings("unused")
	private void showVideoThumnail(int pos) {
		mThumnailView.setVisibility(SurfaceView.VISIBLE);
		mTvTitle.setText(mFileList.get(pos).file_name);
		mSbPlayPosition.setProgress(0);
		mTvPlayPosition.setText("00:00");
		setNextPrevBtnStatus();

		Bitmap bm=ThumbnailUtils.createVideoThumbnail(mGp.videoFileDir+mFileList.get(pos).file_name, 
				MediaStore.Images.Thumbnails.FULL_SCREEN_KIND);
		
		int surfaceView_Width = mThumnailView.getWidth();
	    int surfaceView_Height = mThumnailView.getHeight();
	    float video_Width = bm.getWidth();
	    float video_Height = bm.getHeight();
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

		Canvas canvas=mThumnailView.getHolder().lockCanvas();
		if (bm!=null) {
			Rect f_rect=new Rect(0,0,bm.getWidth(),bm.getHeight());
			Rect t_rect=new Rect(0,0,mThumnailView.getWidth()-1,mThumnailView.getHeight()-1);
//			Log.v("","To width="+mThumnailView.getWidth()+", height="+mThumnailView.getHeight());
//			Log.v("","From width="+f_rect.right+", height="+f_rect.bottom);
			Paint paint=new Paint();
			canvas.drawBitmap(bm, f_rect, t_rect, paint);
		} else {
			canvas.drawColor(Color.BLACK);
		}
		mThumnailView.getHolder().unlockCanvasAndPost(canvas);

		mSurfaceView.setVisibility(SurfaceView.INVISIBLE);
	};
	
	private void stopVideoPlayer() {
		if (mIsVideoPlaying) {
			stopVideo();
			mTcPlayer.setDisabled();
			synchronized(mTcPlayer) {
				mTcPlayer.notify();
			}
			try {
				if (mPlayerThread!=null) mPlayerThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			mIsVideoPausing=false;
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
	private void playVideo(String fp) {
		mLog.addDebugMsg(1,"I","playVideo entered, fp="+fp+", mIsVideoPlaying="+mIsVideoPlaying);
		if (mIsVideoPlaying) return;
		mTcPlayer.setEnabled();
		mSurfaceView.setVisibility(SurfaceView.VISIBLE);
		mThumnailView.setVisibility(SurfaceView.INVISIBLE);
//		mUiHandler.postDelayed(new Runnable(){
//			@Override
//			public void run() {
//				mLayoutTop.setVisibility(LinearLayout.VISIBLE);
//				mLayoutBottom.setVisibility(LinearLayout.VISIBLE);
//			}
//		}, 2000);
		try {
			mTvTitle.setText(fp);
			  
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
//					mTcPlayer.setDisabled();
//					synchronized(mTcPlayer) {
//						mTcPlayer.notify();
//					}
//					try {
//						mPlayerThread.join();
//					} catch (InterruptedException e) {
//						e.printStackTrace();
//					}
					mIsVideoPlaying=false;
					mIsVideoPausing=false;
					stopMediaPlayer();
					mIbStartStop.setImageResource(R.drawable.player_play_enabled);
					mSbPlayPosition.setEnabled(false);
				}
			});
			mMediaPlayer.setOnPreparedListener(new OnPreparedListener(){
				@Override
				public void onPrepared(MediaPlayer mp) {
					mLog.addDebugMsg(1,"I","onPrepared called");
					mSbPlayPosition.setMax(mMediaPlayer.getDuration());
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
					startVideo();
				}
			});
			mMediaPlayer.setOnVideoSizeChangedListener(new OnVideoSizeChangedListener(){
				@Override
				public void onVideoSizeChanged(MediaPlayer mp, int video_width, int video_height) {
					mLog.addDebugMsg(1,"I","onVideoSizeChanged called, width="+video_width+", height="+video_height);
				}
			});

			mIsVideoPlaying=true;
			mMediaPlayer.setDataSource(mGp.videoFileDir+fp);
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

	private Thread mPlayerThread=null;
	private void startVideo() {
		mPlayerThread=new Thread() {
			@Override
			public void run() {
				mMediaPlayer.start();
				while (mIsVideoPlaying) {
					try {
						if (!mIsVideoPausing) {
							mUiHandler.post(new Runnable(){
								@Override
								public void run() {
									if (mIsVideoPlaying) {
										try {
											int cp=mMediaPlayer.getCurrentPosition();
											mSbPlayPosition.setProgress(cp);
											int mm=cp/1000/60;
											int ss=(cp-(mm*1000*60))/1000;
											mTvPlayPosition.setText(String.format("%02d",mm)+":"+String.format("%02d",ss));
										} catch(IllegalStateException e) {
										}
									}
								}
							});
						}
						synchronized(mTcPlayer) {
							mTcPlayer.wait(100);
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					if (!mTcPlayer.isEnabled()) {
						mLog.addDebugMsg(1,"I", "startVideo cancelled");
//						mMediaPlayer.stop();
//						mPlayer.reset();
						break;
					} else {
						mSbPlayPosition.setProgress(mSbPlayPosition.getMax());
					}
				}
//				mMediaPlayer.stop();
//				mUiHandler.post(new Runnable(){
//					@Override
//					public void run() {
//						mSbPlayPosition.setProgress(0);
//						mSbPlayPosition.setEnabled(false);
//					}
//				});
				mLog.addDebugMsg(1,"I", "startVideo expired");
			}
		};
		mPlayerThread.setName("Player");
		mPlayerThread.start();		
	};

	private void stopVideo() {
//		stopMediaPlayer();
		mMediaPlayer.pause();
		mIsVideoPlaying=false;
		mIsVideoPausing=true;
	};
	
	private void stopMediaPlayer() {
		mIsVideoPlaying=false;
		mIsVideoPausing=false;
		try {
			mMediaPlayer.reset();
//			mMediaPlayer.release();
		} catch(IllegalStateException e) {
		}
	}

}

