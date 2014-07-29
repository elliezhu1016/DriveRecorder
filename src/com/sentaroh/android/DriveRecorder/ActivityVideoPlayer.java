package com.sentaroh.android.DriveRecorder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import com.sentaroh.android.DriveRecorder.Log.LogUtil;
import com.sentaroh.android.Utilities.MiscUtil;
import com.sentaroh.android.Utilities.ThreadCtrl;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnVideoSizeChangedListener;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
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
//    private CommonDialog mCommonDlg=null;

	private ArrayList<FileListItem> mFileList=new ArrayList<FileListItem>();
	private int mCurrentSelectedPos=0;
	private SurfaceView mSurfaceView=null;
	private SurfaceHolder mSurfaceHolder=null;
//	private Activity mActivity=null;

	private boolean mIsVideoPlaying=false, mIsVideoPausing=false;
	private boolean mIsVideoReadyToBePlayed=true;

    
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
        setContentView(R.layout.video_player);
        mUiHandler=new Handler();
        mGp=(GlobalParameters) this.getApplication();
        mGp.initSettingParms(this);
        mGp.loadSettingParms(this);
        
        mContext=this.getApplicationContext();
        
        mLog=new LogUtil(mContext, "VideoPlayer", mGp);
        
        mLog.addDebugMsg(1, "I","onCreate entered");
        
//        mCcMenu = new CustomContextMenu(getResources(),getSupportFragmentManager());
//        mCommonDlg=new CommonDialog(mContext, getSupportFragmentManager());

        if (mGp.settingsDeviceOrientationPortrait) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        else setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        mTcPlayer=new ThreadCtrl();
        mMediaPlayer = new MediaPlayer();
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
    	mLog.flushLog();
    	stopMediaPlayer();
    	mMediaPlayer.release();
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
    	Log.v("","fp="+s_fp);
		for (int i=0;i<mFileList.size();i++) {
			if (s_fp.equals(mFileList.get(i).file_name)) {
				mCurrentSelectedPos=i;
				break;
			}
		}

	};
	
	@SuppressWarnings("unused")
	public void setMainViewListener() {

		final SeekBar sb_play_pos=(SeekBar)findViewById(R.id.video_player_dlg_played_pos);
		final TextView tv_play_time=(TextView)findViewById(R.id.video_player_dlg_played_time);
		mSurfaceView=(SurfaceView)findViewById(R.id.video_player_dlg_video);
		final ImageButton ib_prev=(ImageButton) findViewById(R.id.video_player_dlg_prev);
		final ImageButton ib_start_stop=(ImageButton)findViewById(R.id.video_player_dlg_start_stop);
		final ImageButton ib_next=(ImageButton)findViewById(R.id.video_player_dlg_next);
//		sb_play_pos.bringToFront();
//		tv_play_time.bringToFront();
//		ib_prev.bringToFront();
//		ib_next.bringToFront();
//		ib_start_stop.bringToFront();
		
        mSurfaceView.setZOrderOnTop(true);
        mSurfaceHolder=mSurfaceView.getHolder();
        mSurfaceHolder.setFormat(PixelFormat.TRANSPARENT);
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback(){
			@Override
			public void surfaceCreated(SurfaceHolder holder) {
				mLog.addDebugMsg(1,"I","surfaceCreated entered");
				if (mIsVideoReadyToBePlayed) {
					mIsVideoReadyToBePlayed=false;
					ib_start_stop.performClick();
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
			ib_next.setEnabled(true);
			ib_next.setImageResource(R.drawable.next_file_enabled);
		} else {
			ib_next.setEnabled(false);
			ib_next.setImageResource(R.drawable.next_file_disabled);
		}
		if (mCurrentSelectedPos>0) {
			ib_prev.setEnabled(true);
			ib_prev.setImageResource(R.drawable.prev_file_enabled);
		} else {
			ib_prev.setEnabled(false);
			ib_prev.setImageResource(R.drawable.prev_file_disabled);
		}

		ib_prev.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				if (mIsVideoPlaying) {
					stopVideo();
					mTcPlayer.setDisabled();
					synchronized(mTcPlayer) {
						mTcPlayer.notify();
					}
					try {
						mPlayerThread.join();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
//					stopMediaPlayer();
					mIsVideoPausing=false;
//					sb_play_pos.setEnabled(false);
				} 
				stopMediaPlayer();
				sb_play_pos.setProgress(0);
				sb_play_pos.setEnabled(true);
				mCurrentSelectedPos--;
				playVideo(mFileList.get(mCurrentSelectedPos).file_name);
				mIsVideoPlaying=true;
				mIsVideoPausing=false;
				ib_start_stop.setImageResource(R.drawable.player_stop);
				
				if ((mCurrentSelectedPos+1)<mFileList.size()) {
					ib_next.setEnabled(true);
					ib_next.setImageResource(R.drawable.next_file_enabled);
				} else {
					ib_next.setEnabled(false);
					ib_next.setImageResource(R.drawable.next_file_disabled);
				}
				if (mCurrentSelectedPos>0) {
					ib_prev.setEnabled(true);
					ib_prev.setImageResource(R.drawable.prev_file_enabled);
				} else {
					ib_prev.setEnabled(false);
					ib_prev.setImageResource(R.drawable.prev_file_disabled);
				}

			}
		});
		
		ib_next.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				if (mIsVideoPlaying) {
					stopVideo();
//					stopMediaPlayer();
					mTcPlayer.setDisabled();
					synchronized(mTcPlayer) {
						mTcPlayer.notify();
					}
					try {
						mPlayerThread.join();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					mIsVideoPausing=false;
//					sb_play_pos.setEnabled(false);
				} 
				stopMediaPlayer();
				sb_play_pos.setProgress(0);
				sb_play_pos.setEnabled(true);
				mCurrentSelectedPos++;
				playVideo(mFileList.get(mCurrentSelectedPos).file_name);
				mIsVideoPlaying=true;
				mIsVideoPausing=false;
				ib_start_stop.setImageResource(R.drawable.player_stop);
				
				if ((mCurrentSelectedPos+1)<mFileList.size()) {
					ib_next.setEnabled(true);
					ib_next.setImageResource(R.drawable.next_file_enabled);
				} else {
					ib_next.setEnabled(false);
					ib_next.setImageResource(R.drawable.next_file_disabled);
				}
				if (mCurrentSelectedPos>0) {
					ib_prev.setEnabled(true);
					ib_prev.setImageResource(R.drawable.prev_file_enabled);
				} else {
					ib_prev.setEnabled(false);
					ib_prev.setImageResource(R.drawable.prev_file_disabled);
				}

			}
		});
		
		sb_play_pos.setProgress(0);
		sb_play_pos.setOnSeekBarChangeListener(new OnSeekBarChangeListener(){
			@Override
			public void onProgressChanged(SeekBar arg0, int progress, boolean arg2) {
				if (mIsVideoPlaying) {
					if (mSongPlayedPosIsTouched) {
						mSongPlayedPosIsTouched=false;
						int n_pos=(mMediaPlayer.getDuration()*progress)/100;
						mMediaPlayer.seekTo(n_pos);
					}
				} 
			}
			@Override
			public void onStartTrackingTouch(SeekBar arg0) {
//				Log.v("","onStartTrackingTouch");
				mSongPlayedPosIsTouched=true;
			}
			@Override
			public void onStopTrackingTouch(SeekBar arg0) {
//				Log.v("","onStopTrackingTouch");
				mSongPlayedPosIsTouched=false;
			}
		});

		ib_start_stop.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				if (mIsVideoPlaying) {
					stopVideo();
					mTcPlayer.setDisabled();
					synchronized(mTcPlayer) {
						mTcPlayer.notify();
					}
					try {
						mPlayerThread.join();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					mIsVideoPausing=true;
					ib_start_stop.setImageResource(R.drawable.player_play_enabled);
					sb_play_pos.setEnabled(false);
				} else {
					//Start
					ib_start_stop.setImageResource(R.drawable.player_stop);
					if (!mIsVideoPausing) {
						sb_play_pos.setProgress(0);
						sb_play_pos.setEnabled(true);
						playVideo(mFileList.get(mCurrentSelectedPos).file_name);
						mIsVideoPlaying=true;
						mIsVideoPausing=false;
					} else {
						sb_play_pos.setEnabled(true);
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
	
	private MediaPlayer mMediaPlayer=null;
	private ThreadCtrl mTcPlayer=null;
	private boolean mSongPlayedPosIsTouched=false;
	private void playVideo(String fp) {
		mLog.addDebugMsg(1,"I","playVideo entered, fp="+fp+", mIsVideoPlaying="+mIsVideoPlaying);
		mTcPlayer.setEnabled();
		final ImageButton ib_start_stop=(ImageButton)findViewById(R.id.video_player_dlg_start_stop);
		if (mIsVideoPlaying) return;
		  try {
			  setTitle(mContext.getString(R.string.app_name)+"("+fp+")");
			  
			  mIsVideoPlaying=true;
			  mMediaPlayer.setDataSource(mGp.videoFileDir+fp);
			  mMediaPlayer.setDisplay(mSurfaceHolder);
			  mMediaPlayer.prepare();
		      
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
		      mSurfaceView.setLayoutParams(layoutParams);
			  
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
//						mTcPlayer.setDisabled();
//						synchronized(mTcPlayer) {
//							mTcPlayer.notify();
//						}
//						try {
//							mPlayerThread.join();
//						} catch (InterruptedException e) {
//							e.printStackTrace();
//						}
						mIsVideoPlaying=false;
						mIsVideoPausing=false;
						stopMediaPlayer();
						ib_start_stop.setImageResource(R.drawable.player_play_enabled);
					}
			  });
			  mMediaPlayer.setOnPreparedListener(new OnPreparedListener(){
					@Override
					public void onPrepared(MediaPlayer mp) {
						mLog.addDebugMsg(1,"I","onPrepared called");
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
		final SeekBar sb_play_pos=(SeekBar)findViewById(R.id.video_player_dlg_played_pos);
		final TextView tv_play_time=(TextView)findViewById(R.id.video_player_dlg_played_time);
		mPlayerThread=new Thread() {
			@Override
			public void run() {
				final float duration=mMediaPlayer.getDuration();
				mMediaPlayer.start();
				while (mIsVideoPlaying) {
					try {
						if (!mIsVideoPausing) {
							mUiHandler.post(new Runnable(){
								@Override
								public void run() {
									if (!mSongPlayedPosIsTouched && mIsVideoPlaying) {
										try {
											int cp=mMediaPlayer.getCurrentPosition();
											float fcp=cp;
											int prog=(int)((fcp/duration)*100);
											sb_play_pos.setProgress(prog);
											int mm=cp/1000/60;
											int ss=(cp-(mm*1000*60))/1000;
											tv_play_time.setText(String.format("%02d",mm)+":"+String.format("%02d",ss));
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
						sb_play_pos.setProgress(100);
					}
				}
//				mMediaPlayer.stop();
//				mUiHandler.post(new Runnable(){
//					@Override
//					public void run() {
//						sb_play_pos.setProgress(0);
//						sb_play_pos.setEnabled(false);
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
