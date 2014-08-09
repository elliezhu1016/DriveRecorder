package com.sentaroh.android.DriveRecorder;

import static com.sentaroh.android.DriveRecorder.Constants.*;

import java.io.File;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.sentaroh.android.DriveRecorder.Log.LogUtil;
import com.sentaroh.android.Utilities.DateUtil;
import com.sentaroh.android.Utilities.ThreadCtrl;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.RingtoneManager;
import android.media.MediaRecorder.OnErrorListener;
import android.media.MediaRecorder.OnInfoListener;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.OnScanCompletedListener;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.FrameLayout;
import android.widget.TextView;

public class RecorderService extends Service {
    private Camera mServiceCamera;
    private MediaRecorder mMediaRecorder;

    private GlobalParameters mGp=null;
    
    private LogUtil mLog=null;
    
    private Handler mUiHandler=null;

	private Context mContext=null;
	
	private SurfaceHolder mSurfaceHolder=null;
	private SurfaceView mSurfaceView=null;

    private boolean mPreviewAvailable=false;

	private WidgetService mWidget=null;
	
	private int mShowedScreenWidth=0, mSHowedScreenHeight=0;
	
	private SleepReceiver mSleepReceiver=null;
	
	private boolean mCameraInitCompleted=false;
	private String mToggleBtnEnabled="1";
	
	private WakeLock mWakeLock=null;
	
//    private String mVideoFilePath="";
    
    @Override
    public void onCreate() {
        super.onCreate();
    	mGp=(GlobalParameters) this.getApplication();
    	mGp.loadSettingParms(this);
    	mLog=new LogUtil(this,"Recorder",mGp);
    	mLog.addDebugMsg(1,"I", "onCreate entered");
    	mUiHandler=new Handler();
    	mContext=this;
    	
    	mWakeLock=((PowerManager)getSystemService(Context.POWER_SERVICE))
    			.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK    					
    				| PowerManager.ON_AFTER_RELEASE, "DriveRecorder");

    	initNotification();

    	mSleepReceiver=new SleepReceiver();
    	
    	createCameraPreview();

    	hidePreview();

    	mWidget=new WidgetService(mContext, mGp, mLog);
    	
    	startBasicEventReceiver(mGp);
    	
//    	setSensor();
    	
    	initRingTone(mContext);
    	
    	myUncaughtExceptionHandler.init();

    };
    
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		String action="";
		if (intent!=null && intent.getAction()!=null) action=intent.getAction();
		mLog.addDebugMsg(1,"I","onStartCommand entered, action="+action);
		if (action.startsWith(WIDGET_RECORDER_PREFIX)) {
			mWidget.processWidgetIntent(intent, action);
		} else if (action.equals(Intent.ACTION_USER_PRESENT)) {
		} else if (action.equals(TOGGLE_RECORDER_INTENT)) {
			if (isToggleBtnEnabled()) {
				synchronized(mToggleBtnEnabled) {
					mWidget.setIconStartStop();
					setToggleBtnEnabled(false);
					if (mGp.isRecording) {
						stopIntervalRecorder();
					} else {
						startIntervalRecorder();
					}
				}
			}
		} else if ((mGp.screenIsLocked && action.equals("android.media.VOLUME_CHANGED_ACTION"))) {
			synchronized(mIgnoreVolumeChangedAction) {
				if (isToggleBtnEnabled() && mIgnoreVolumeChangedAction.equals("0")) {
					synchronized(mToggleBtnEnabled) {
						mWidget.setIconStartStop();
						setToggleBtnEnabled(false);
						if (mGp.isRecording) {
							playBackRingtone();
							stopIntervalRecorder();
						} else {
							startIntervalRecorder();
						}
					}
				} else {
					mLog.addDebugMsg(1,"I","onStartCommand ignored" +
							", toggle="+isToggleBtnEnabled()+
							", ignore="+mIgnoreVolumeChangedAction+
							",  action="+action);
				}
			}
		}
		return START_STICKY;
	};
    
	private void setToggleBtnEnabled(boolean p) {
		synchronized(mToggleBtnEnabled) {
			if (p) mToggleBtnEnabled="1";
			else mToggleBtnEnabled="0";
		}
	};
	
	private boolean isToggleBtnEnabled() {
		synchronized(mToggleBtnEnabled) {
			if (mToggleBtnEnabled.equals("1")) return true;
			else return false;
		}
	};
	
	private String mIgnoreVolumeChangedAction="0";
	private void playBackRingtone() {
		if (mStopRecorderRtUri!=null) {
			Thread th=new Thread() {
				@Override
				public void run() {
					synchronized(mIgnoreVolumeChangedAction) {
						mIgnoreVolumeChangedAction="1";
						SystemClock.sleep(1000);
						AudioManager am=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
						int c_n_v=am.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
						int c_m_v=am.getStreamVolume(AudioManager.STREAM_MUSIC);
						int m_n_v=am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
						int m_m_v=am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
						am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, m_n_v, 0);
						am.setStreamVolume(AudioManager.STREAM_MUSIC, m_m_v, 0);
						MediaPlayer player = MediaPlayer.create(mContext, mStopRecorderRtUri);
						if (player!=null) {
							int duration=player.getDuration();
							player.setVolume(1, 1);
							player.start();
							SystemClock.sleep(duration+10);
							player.stop();
							player.release();
						}
						am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, c_n_v, 0);
						am.setStreamVolume(AudioManager.STREAM_MUSIC, c_m_v, 0);
						mUiHandler.postDelayed(new Runnable(){
							@Override
							public void run() {
								synchronized(mIgnoreVolumeChangedAction) {
									mIgnoreVolumeChangedAction="0";
								}
							}
						}, 100);
					}
				}
			};
			th.start();
		} else {
			mLog.addDebugMsg(1,"I", "Ringtone was can not playback.");
		}
	};
	
    @Override
    public IBinder onBind(Intent intent) {
    	mLog.addDebugMsg(1,"I", "onBind entered");
    	if (intent.getAction().equals("Connection")) return mSvcRecorderClient;
    	return null;
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        mLog.addDebugMsg(1,"I", "onDestroy entered");
//        unsetSensor();
        
        closeNotification();
        stopBasicEventReceiver(mGp);
        mLog.flushLog();
        removeCameraPreview();
    };

    private Uri mStopRecorderRtUri=null;
    
    final private void initRingTone(Context c) {
        RingtoneManager rm = new RingtoneManager(c);
        rm.setType(RingtoneManager.TYPE_NOTIFICATION);
        Cursor cursor = rm.getCursor();
        int idx=0;
        while (cursor.moveToNext()) {
        	String ringtone_name=cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
        	mStopRecorderRtUri=rm.getRingtoneUri(idx);
//        	int index_no=idx;
        	idx++;
        	mLog.addDebugMsg(1,"I", "stopRecorderRingtone is "+ringtone_name);
        	break;
        }
        cursor.close();
	};

    
	private ThreadCtrl mTcRecorder=null;
	private void startIntervalRecorder() {
		mTcRecorder=new ThreadCtrl();
    	showNotification();
    	prepareCamera();
		Thread th=new Thread() {
			public void run() {
				mLog.addDebugMsg(1,"I", "startIntervalRecorder started");
				mUiHandler.post(new Runnable(){
					@Override
					public void run() {
						startVideoRecorder();
						mUiHandler.postDelayed(new Runnable(){
							@Override
							public void run() {
								setToggleBtnEnabled(true);
							}
						},1000);
					}
				});
				while(mTcRecorder.isEnabled()) {
					synchronized(mTcRecorder) {
						mLog.addDebugMsg(1,"I", "startIntervalRecorder wait for timer expired");
						try {
							mTcRecorder.wait(mGp.settingsRecordingDuration*60*1000+900);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						mLog.addDebugMsg(1,"I", "startIntervalRecorder timer expired"); 
					}
					stopVideoRecorder();
					setToggleBtnEnabled(false);
					if (mTcRecorder.isEnabled()) {
						startVideoRecorder();
						mUiHandler.post(new Runnable(){
							@Override
							public void run() {
								mUiHandler.postDelayed(new Runnable(){
									@Override
									public void run() {
										setToggleBtnEnabled(true);
									}
								},1000);
							}
						});
					} else {//Cancel
						mLog.addDebugMsg(1,"I", "Recorder thread disable was detected");
						mUiHandler.post(new Runnable(){
							@Override
							public void run() {
								destroyCamera();
								mUiHandler.postDelayed(new Runnable(){
									@Override
									public void run() {
										hidePreview();
										mUiHandler.post(new Runnable(){
											@Override
											public void run() {
										    	removeCameraPreview();
										    	createCameraPreview();
												setToggleBtnEnabled(true);
											}
										});
									}
								},100);
							}
						});
					}
				}
		    	closeNotification();
		        try {
		        	if (mActCallback!=null) mActCallback.notifyRecordingStopped();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				mTcRecorder=null;
				mLog.addDebugMsg(1,"I", "startIntervalRecorder ended");
			}
		};
		th.setName("IntervalRecorder");
		th.start();
	};

	private void stopIntervalRecorder() {
		mLog.addDebugMsg(1,"I", "stopIntervalRecorder entered, mTcRecorder="+mTcRecorder);
		if (mTcRecorder!=null) {
			mTcRecorder.setDisabled();
			synchronized(mTcRecorder) {
				mTcRecorder.notify();
			}
//			SystemClock.sleep(100);
//			mSurfaceView.setVisibility(CustomSurfaceView.GONE);
		}
	};

	private String getVideoFilePath() {
    	File lf=new File(mGp.videoRecordDir);
    	if (!lf.exists()) lf.mkdirs();
		String dt=DateUtil.convDateTimeTo_YearMonthDayHourMinSec(System.currentTimeMillis());
		String dt_date=dt.substring(0,10).replaceAll("/", "-");
		String dt_time=dt.substring(11,19).replaceAll(":", "");
		mGp.currentRecordedFileName=mGp.videoFileNamePrefix+dt_date+"_"+dt_time+".mp4";
		mLog.addDebugMsg(1,"I", "New video file name="+mGp.currentRecordedFileName);
		return mGp.currentRecordedFileName;
	};

	static private void houseKeepVideoFiles(GlobalParameters mGp, LogUtil mLog) {
		File lf=new File(mGp.videoRecordDir);
		String[] fl=lf.list();
		if (fl!=null) {
			ArrayList<String>list=new ArrayList<String>();
			for(int i=0;i<fl.length;i++) list.add(fl[i]);
			Collections.sort(list, new Comparator<String>(){
				@Override
				public int compare(String lhs, String rhs) {
					return lhs.compareToIgnoreCase(rhs);
				}
			});
			if (list.size()>mGp.settingsMaxVideoKeepGeneration) {
				for (int i=list.size()-mGp.settingsMaxVideoKeepGeneration-1;i>=0;i--) {
					mLog.addDebugMsg(1,"I", "Video file deleted :"+mGp.videoRecordDir+list.get(i));
					lf=new File(mGp.videoRecordDir+list.get(i));
					lf.delete();
					list.remove(i);
				}
			}
		}
	};
	
	private void prepareCamera() {
        mServiceCamera = Camera.open(0);
    	if (!mCameraInitCompleted) {
    		initCameraParms(mServiceCamera);
    		mCameraInitCompleted=true;
    	}

        Camera.Parameters params = mServiceCamera.getParameters();
        mServiceCamera.setParameters(params);
        Camera.Parameters p = mServiceCamera.getParameters();
        p.setFocusMode(mFocusMode);

        p.setFlashMode(mFlashMode);

    	WindowManager wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
    	int h_m=(int)toPixel(mContext.getResources(), 85);
    	
    	Display display = wm.getDefaultDisplay();
    	Point size = new Point();
    	display.getSize(size);
    	int screen_height=size.y-h_m;
    	int screen_width=size.x;
    	mLog.addDebugMsg(1,"I","Device screen size : width = " + screen_width + ", height = " + screen_height);
    	
//        int preview_height=0,preview_width=0;
        int preview_height=144,preview_width=176;
        for(int i=0;i<mSupportedPreviewSizeList.size();i++) {
        	if (screen_width>mSupportedPreviewSizeList.get(i).width && screen_height>mSupportedPreviewSizeList.get(i).height) {
        		if (preview_width<mSupportedPreviewSizeList.get(i).width || preview_height<mSupportedPreviewSizeList.get(i).height) {
        			preview_width=mSupportedPreviewSizeList.get(i).width; 
        			preview_height=mSupportedPreviewSizeList.get(i).height; 
        		}
        	}
//        	if (preview_width>mSupportedPreviewSizeList.get(i).width) {
//    			preview_width=mSupportedPreviewSizeList.get(i).width; 
//    			preview_height=mSupportedPreviewSizeList.get(i).height; 
//        	}
        }
        mLog.addDebugMsg(1,"I","Selected Preview video size : width = " + preview_width + " height = " + preview_height);
        
        p.setPreviewSize(preview_width, preview_height);
        p.setPreviewFormat(ImageFormat.NV21);
        mServiceCamera.setParameters(p);

        mServiceCamera.setErrorCallback(new ErrorCallback(){
			@Override
			public void onError(int error, Camera camera) {
				mLog.addDebugMsg(1,"E", "Camera error="+error);
			}
        });

        int lp_height = 0, lp_width = 0;
        float scale_factor_height=0, scale_factor_width=0, result_scale_factor=0;
        if (mGp.settingsDeviceOrientationPortrait) {
            lp_height = screen_height;
            lp_width = screen_width;
        } else {
            scale_factor_height = (float)screen_height / (float)preview_height;
            scale_factor_width = (float)screen_width / (float)preview_width;
            // Select smaller factor, because the surface cannot be set to the size larger than display metrics.
            if (scale_factor_height < scale_factor_width) {
                result_scale_factor = scale_factor_height;
            } else {
                result_scale_factor = scale_factor_width;
            }
            lp_height = (int)((float)preview_height * result_scale_factor);
            lp_width = (int)((float)preview_width * result_scale_factor);
        }
        mShowedScreenWidth=lp_width;
        mSHowedScreenHeight=lp_height;
        if (mPreviewAvailable) showPreview();
        else hidePreview();

        if (mGp.settingsDeviceOrientationPortrait) mServiceCamera.setDisplayOrientation(90);
        else mServiceCamera.setDisplayOrientation(0);

//        if (mFocusMode.equals(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) ||
//        		mFocusMode.equals(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
//            mServiceCamera.setAutoFocusMoveCallback(new AutoFocusMoveCallback(){
//    			@Override
//    			public void onAutoFocusMoving(boolean start, Camera camera) {
//    				mLog.addDebugMsg(1,"I", "onAutoFocusMoving entered, start="+start);
//    			}
//            });
//        }
        
//        byte[] cb=new byte[3110400];
//        mServiceCamera.addCallbackBuffer(cb);
//        mServiceCamera.setPreviewCallbackWithBuffer(new PreviewCallback(){
//			@Override
//			public void onPreviewFrame(byte[] data, Camera camera) {
//				mLog.addDebugMsg(1,"I", "onPreviewFrame entered, length="+data.length);
//				camera.addCallbackBuffer(data);
//			}
//        });
	};

	private void destroyCamera() {
        mServiceCamera.lock();
        mServiceCamera.release();
        mServiceCamera=null;
	};
	
	private boolean startCamera() {
        boolean result=false;
        mServiceCamera.lock();
        try {
        	mServiceCamera.setPreviewDisplay(mSurfaceHolder);
            mServiceCamera.startPreview();
            result=true;
        }
        catch (IOException e) {
        	mLog.addDebugMsg(1,"E", e.getMessage());
            e.printStackTrace();
        }

        mServiceCamera.unlock();
        
		return result;
	};
	
	private boolean prepareMediaRecorder() {
        try {
            mMediaRecorder = new MediaRecorder();
            mMediaRecorder.setCamera(mServiceCamera);
            if (mGp.settingsRecordSound) mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
//            else mMediaRecorder.setAudioSource(MediaRecorder.AudioSource..MIC);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            
            CamcorderProfile profile = null;
            int br_ratio=1;
            if (mGp.settingsRecordVideoQuality.equals(RECORD_VIDEO_QUALITY_LOW)) {
            	profile=CamcorderProfile.get(0,CamcorderProfile.QUALITY_LOW);
            	br_ratio=1;
            } else if (mGp.settingsRecordVideoQuality.equals(RECORD_VIDEO_QUALITY_MEDIUM)) {
            	profile=CamcorderProfile.get(0,CamcorderProfile.QUALITY_LOW);
            	br_ratio=2;
            } else if (mGp.settingsRecordVideoQuality.equals(RECORD_VIDEO_QUALITY_HIGH)) {
            	profile=CamcorderProfile.get(0,CamcorderProfile.QUALITY_HIGH);
            	br_ratio=4;
            }
            mLog.addDebugMsg(1,"I","Profile video frame rate="+profile.videoFrameRate+
            		", video bit rate="+profile.videoBitRate+
            		", audio bit rate=="+profile.audioBitRate+", sample rate="+profile.audioSampleRate);
            
            profile.fileFormat = MediaRecorder.OutputFormat.MPEG_4;
            profile.videoBitRate=mGp.settingsVideoBitRate*br_ratio;
            
//            mMediaRecorder.setProfile(profile);
            
            mLog.addDebugMsg(1,"I","Selected video size width="+profile.videoFrameWidth+", height="+profile.videoFrameHeight+
            		", frame rate="+profile.videoFrameRate+", video bit rate="+profile.videoBitRate+
            		", audio bit rate=="+profile.audioBitRate+", sample rate="+profile.audioSampleRate);
            
            mMediaRecorder.setOutputFormat(profile.fileFormat);
            if (mGp.settingsRecordSound) {
            	mMediaRecorder.setAudioEncodingBitRate(profile.audioBitRate);
            	mMediaRecorder.setAudioSamplingRate(profile.audioSampleRate);
            	mMediaRecorder.setAudioEncoder(profile.audioCodec);
            }
            mMediaRecorder.setVideoEncoder(profile.videoCodec);
            getVideoFilePath();
            mMediaRecorder.setOutputFile(mGp.videoRecordDir+mGp.currentRecordedFileName);
//            mMediaRecorder.setVideoFrameRate(profile.videoFrameRate);
            mMediaRecorder.setVideoEncodingBitRate(profile.videoBitRate);
            
            mMediaRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
            final String t_msg="Video width="+profile.videoFrameWidth+", height="+profile.videoFrameHeight+
            		", bit rate="+(profile.videoBitRate/1024)+"KBPS";
            mUiHandler.post(new Runnable(){
				@Override
				public void run() {
		            mCameraPreviewTopText.setText(t_msg);
				}
            });
            
            mMediaRecorder.setOnInfoListener(new OnInfoListener(){
    			@Override
    			public void onInfo(MediaRecorder mr, int what, int extra) {
    				mLog.addDebugMsg(1, "I", "onInfoListener entered, what="+what+", extra="+extra);
    			}
            });
            
            mMediaRecorder.setOnErrorListener(new OnErrorListener(){
    			@Override
    			public void onError(MediaRecorder mr, int what, int extra) {
    				mLog.addDebugMsg(1, "I", "onErrorListener entered, what="+what+", extra="+extra);
    			}
            });
            
            mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());

            if (mGp.settingsDeviceOrientationPortrait) {
            	if (Camera.getNumberOfCameras()==2) mMediaRecorder.setOrientationHint(90);
            	else mMediaRecorder.setOrientationHint(270);
            } else {
            	if (Camera.getNumberOfCameras()==2) mMediaRecorder.setOrientationHint(0);
            	else mMediaRecorder.setOrientationHint(0);
            }
            
            mMediaRecorder.prepare();
            mMediaRecorder.start();
            
            return true;
        } catch (IOException e) {
        	mLog.addDebugMsg(1,"E", e.getMessage());
            e.printStackTrace();
            return false;
        }
	};
	
	private void startVideoRecorder(){
		try {
        	mLog.addDebugMsg(1,"I", "startVideoRecorder entered");
        	
            houseKeepVideoFiles(mGp, mLog);
            
            if (startCamera()) {
                if (prepareMediaRecorder()) {
//                	mCameraPreviewTopText.setVisibility(TextView.VISIBLE);
                	
                    mGp.isRecording = true;
                    
                    mWidget.updateIcon(mGp.isRecording);
                    setNotificatioIcon(mGp.isRecording);

                    try {
            			if (mActCallback!=null) mActCallback.notifyRecordingStarted();
            		} catch (RemoteException e) {
            			e.printStackTrace();
            		}
                } else {// Prepare recorder error
                	mLog.addDebugMsg(1,"I", "Prepare recorder error");
                }
            } else {//Prepare camera error
            	mLog.addDebugMsg(1,"I", "Prepare camera error");
            }
        } catch (IllegalStateException e) {
        	mLog.addDebugMsg(1,"E", e.getMessage());
            e.printStackTrace();
        }
    };
    
    public void stopVideoRecorder() {
    	mLog.addDebugMsg(1,"I", "stopVideoRecorder entered");
        try {
            mServiceCamera.reconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        try {
        	mMediaRecorder.stop();
        } catch(RuntimeException e) {
        	mLog.addDebugMsg(1, "E", "MediaRecorder stop() was failed.");
        	printStackTrace(e.toString(), e.getStackTrace(), e.getCause());
        } finally {
            mMediaRecorder.reset();
            mMediaRecorder.release();
        }
        
        mServiceCamera.lock();
        mServiceCamera.stopPreview();
        mServiceCamera.unlock();
        
        if (!mGp.currentRecordedFileName.equals("")) {
        	final String sfp=mGp.videoRecordDir+mGp.currentRecordedFileName;
        	Thread th=new Thread() {
        		@Override
        		public void run() {
                	String[] paths = new String[] {sfp};
                	MediaScannerConnection.scanFile(getApplicationContext(), paths, null, mOnScanCompletedListener);
        		}
        	};
        	th.setPriority(Thread.MIN_PRIORITY);
        	th.start();
        	mGp.currentRecordedFileName="";
        }
        
        mGp.isRecording = false;
        mWidget.updateIcon(mGp.isRecording);
        setNotificatioIcon(mGp.isRecording);
//        mCameraPreviewTopText.setVisibility(TextView.INVISIBLE);
    };

    private List<String> mSupportedFocusModeList=null;
    private String mFocusMode="", mFlashMode="";
    private List<String> mSupportedFlashModeList=null;
    private List<Size> mSupportedPreviewSizeList=null;
    private List<Size> mSupportedVideoSizeList=null;
    
    private void initCameraParms(Camera camera) {
    	
        Camera.Parameters params = camera.getParameters();
        camera.setParameters(params);
        Camera.Parameters p = camera.getParameters();
        mSupportedFocusModeList=p.getSupportedFocusModes();
        mFocusMode=mSupportedFocusModeList.get(0);
        boolean f_c_v=false, f_c_p=false, f_auto=false;
        if (mSupportedFocusModeList.size()>0) {
            mLog.addDebugMsg(1,"I","Supported focus mode :");
            for(int i=0;i<mSupportedFocusModeList.size();i++) {
            	mLog.addDebugMsg(1,"I","   "+i+"="+mSupportedFocusModeList.get(i));
            	if (mSupportedFocusModeList.get(i).equals(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            		f_c_v=true;
            	} else if (mSupportedFocusModeList.get(i).equals(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            		f_c_p=true;
            		break;
            	} else if (mSupportedFocusModeList.get(i).equals(Parameters.FOCUS_MODE_AUTO)) {
            		f_auto=true;
            		break;
            	}
            }
            if (f_c_v) mFocusMode=Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
            else if (f_c_p) mFocusMode=Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;
            else if (f_auto) mFocusMode=Parameters.FOCUS_MODE_AUTO;
        }
        mLog.addDebugMsg(1,"I","Focus mode is="+mFocusMode);

        mSupportedFlashModeList=p.getSupportedFlashModes();
        mFlashMode="";
        if (mSupportedFlashModeList!=null) {
        	mLog.addDebugMsg(1,"I","Supported flash mode :");
            for(int i=0;i<mSupportedFlashModeList.size();i++) {
            	mLog.addDebugMsg(1,"I","   "+i+"="+mSupportedFlashModeList.get(i));
            	if (mSupportedFlashModeList.get(i).equals(Parameters.FLASH_MODE_OFF)) 
            		mFlashMode=mSupportedFlashModeList.get(i);
            }
        }
        mLog.addDebugMsg(1,"I","Flash mode is="+mFlashMode);

        mSupportedPreviewSizeList = p.getSupportedPreviewSizes();
        mLog.addDebugMsg(1,"I","Available preview size :");
        for(int i=0;i<mSupportedPreviewSizeList.size();i++) {
        	mLog.addDebugMsg(1,"I","   "+i+"  width="+
        			mSupportedPreviewSizeList.get(i).width+", height="+mSupportedPreviewSizeList.get(i).height);
        }

        mLog.addDebugMsg(1,"I","MaxZoom="+p.getMaxZoom()+
        		", Zoom="+p.getZoom()+
        		", VideoStabilizationSupported="+p.isVideoStabilizationSupported()+
        		", ZoomSupported="+p.isZoomSupported());

//        camera.unlock();
//
        mSupportedVideoSizeList=p.getSupportedVideoSizes();
        mLog.addDebugMsg(1,"I","Available Video size :");
        for(int i=0;i<mSupportedVideoSizeList.size();i++) {
        	mLog.addDebugMsg(1,"I","   "+i+"  width="+
        			mSupportedVideoSizeList.get(i).width+", height="+mSupportedVideoSizeList.get(i).height);
        }
        
        mLog.addDebugMsg(1,"I","Available quality profile:");
        mLog.addDebugMsg(1,"I","  QUALITY_HIGH="+CamcorderProfile.hasProfile(0, CamcorderProfile.QUALITY_HIGH));
        mLog.addDebugMsg(1,"I","  QUALITY_LOW="+CamcorderProfile.hasProfile(0, CamcorderProfile.QUALITY_LOW));
        mLog.addDebugMsg(1,"I","  QUALITY_480P="+CamcorderProfile.hasProfile(0, CamcorderProfile.QUALITY_480P));
        mLog.addDebugMsg(1,"I","  QUALITY_720P="+CamcorderProfile.hasProfile(0, CamcorderProfile.QUALITY_720P));
        mLog.addDebugMsg(1,"I","  QUALITY_1080P="+CamcorderProfile.hasProfile(0, CamcorderProfile.QUALITY_1080P));
        mLog.addDebugMsg(1,"I","  QUALITY_CIF="+CamcorderProfile.hasProfile(0, CamcorderProfile.QUALITY_CIF));
        mLog.addDebugMsg(1,"I","  QUALITY_QCIF="+CamcorderProfile.hasProfile(0, CamcorderProfile.QUALITY_QCIF));
        mLog.addDebugMsg(1,"I","  QUALITY_QVGA="+CamcorderProfile.hasProfile(0, CamcorderProfile.QUALITY_QVGA));
        mLog.addDebugMsg(1,"I","  QUALITY_TIME_LAPSE_HIGH="+CamcorderProfile.hasProfile(0, CamcorderProfile.QUALITY_TIME_LAPSE_HIGH));
        mLog.addDebugMsg(1,"I","  QUALITY_TIME_LAPSE_LOW="+CamcorderProfile.hasProfile(0, CamcorderProfile.QUALITY_TIME_LAPSE_LOW));
        mLog.addDebugMsg(1,"I","  QUALITY_TIME_LAPSE_480P="+CamcorderProfile.hasProfile(0, CamcorderProfile.QUALITY_TIME_LAPSE_480P));
        mLog.addDebugMsg(1,"I","  QUALITY_TIME_LAPSE_720P="+CamcorderProfile.hasProfile(0, CamcorderProfile.QUALITY_TIME_LAPSE_720P));
        mLog.addDebugMsg(1,"I","  QUALITY_TIME_LAPSE_1080P="+CamcorderProfile.hasProfile(0, CamcorderProfile.QUALITY_TIME_LAPSE_1080P));
        mLog.addDebugMsg(1,"I","  QUALITY_TIME_LAPSE_CIF="+CamcorderProfile.hasProfile(0, CamcorderProfile.QUALITY_TIME_LAPSE_CIF));
        mLog.addDebugMsg(1,"I","  QUALITY_TIME_LAPSE_QCIF="+CamcorderProfile.hasProfile(0, CamcorderProfile.QUALITY_TIME_LAPSE_QCIF));
        mLog.addDebugMsg(1,"I","  QUALITY_TIME_LAPSE_QVGA="+CamcorderProfile.hasProfile(0, CamcorderProfile.QUALITY_TIME_LAPSE_QVGA));

    };
	
	private OnScanCompletedListener mOnScanCompletedListener=new OnScanCompletedListener(){
		@Override
		public void onScanCompleted(String path, Uri uri) {
			mLog.addDebugMsg(1,"I", "Scan completed path="+path+", uri="+uri);
		}
	};

	private IRecorderCallback mActCallback=null;
    
    final private IRecorderClient.Stub mSvcRecorderClient = 
			new IRecorderClient.Stub() {
    	@Override
		final public void setCallBack(final IRecorderCallback callback)
				throws RemoteException {
    		mActCallback=callback;
		};
		@Override
		final public void removeCallBack(IRecorderCallback callback)
				throws RemoteException {
			mActCallback=null;
		};
		@Override
        final public void aidlStartRecorderThread() throws RemoteException {
			mLog.addDebugMsg(1,"I", "aidlStartRecorderThread entered, isRecording="+mGp.isRecording);
	        if (!mGp.isRecording) {
	        	startIntervalRecorder();
	        }
        };
		@Override
        final public void aidlStopRecorderThread() throws RemoteException {
			mLog.addDebugMsg(1,"I", "aidlStopRecorderThread entered, isRecording="+mGp.isRecording);
			if (mGp.isRecording) {
		        stopIntervalRecorder();
			}
        };
		@Override
        final public void aidlStopService() throws RemoteException {
			mLog.addDebugMsg(1,"I", "aidlStopService entered, isRecording="+mGp.isRecording);
			if (mGp.isRecording) {
				stopIntervalRecorder();
			}
	        stopSelf();
        };
        
        @Override
        final public boolean aidlIsRecording() throws RemoteException {
        	mLog.addDebugMsg(1,"I", "aidlIsRecording result="+mGp.isRecording);
        	return mGp.isRecording;
        };

        @Override
        final public void aidlShowPreview() throws RemoteException {
        	showPreview();
        };

        @Override
        final public void aidlHidePreview() throws RemoteException {
        	hidePreview();
        };

        @Override
        final public void aidlSetActivityStarted(boolean started) throws RemoteException {
        	setActivityStarted(started);
        };

    };
    
    private boolean mActivityStarted=false;
    
    @SuppressWarnings("unused")
	private boolean isActivityStarted() {
    	return mActivityStarted;
    }
    
    private void setActivityStarted(boolean started) {
    	mActivityStarted=started;
    };

    private void showPreview() {
    	mLog.addDebugMsg(1,"I", "showPreview entered");
    	mPreviewAvailable=true;
       	WindowManager wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
//       	mCameraPreviewTopText.setVisibility(TextView.VISIBLE);
        LayoutParams lp=(LayoutParams) mCameraPreviewFrame.getLayoutParams();
        lp.height=mSHowedScreenHeight;
        lp.width=mShowedScreenWidth;
        lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        wm.updateViewLayout(mCameraPreviewFrame, lp);
    };
    
    private void hidePreview() {
    	mLog.addDebugMsg(1,"I", "hidePreview entered");
    	mPreviewAvailable=false;
    	if (mCameraPreviewFrame!=null) {
//    		mCameraPreviewTopText.setVisibility(TextView.INVISIBLE);
           	WindowManager wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
            LayoutParams lp=(LayoutParams) mCameraPreviewFrame.getLayoutParams();
            lp.height=14;
            lp.width=14;
            lp.gravity = Gravity.LEFT | Gravity.BOTTOM;
            wm.updateViewLayout(mCameraPreviewFrame, lp);
    	}
    };

	final static private float toPixel(Resources res, int dip) {
		float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, res.getDisplayMetrics());
		return px;
	};

	private FrameLayout mCameraPreviewFrame=null;
	private TextView mCameraPreviewTopText=null;
	private void createCameraPreview() {
    	WindowManager windowManager = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
    	mCameraPreviewFrame=new FrameLayout(this);
    	mCameraPreviewTopText=new TextView(this);
    	mSurfaceView = new SurfaceView(this);
    	mSurfaceHolder=mSurfaceView.getHolder();
        LayoutParams lp_frame = new WindowManager.LayoutParams(
        		20,20,
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE+
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        lp_frame.gravity = Gravity.CENTER | Gravity.BOTTOM;
        LayoutParams lp_c_view = new LayoutParams(
        		LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT);
        lp_c_view.gravity = Gravity.CENTER | Gravity.TOP;
        windowManager.addView(mCameraPreviewFrame, lp_frame);
        mCameraPreviewFrame.addView(mSurfaceView, lp_c_view);
        mCameraPreviewFrame.addView(mCameraPreviewTopText, lp_c_view);
//        mSurfaceView.setZOrderOnTop(true);
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback(){
			@Override
			public void surfaceCreated(SurfaceHolder holder) {
				mLog.addDebugMsg(1,"I","surfaceCreated entered");
			}

			@Override
			public void surfaceChanged(SurfaceHolder holder, int format,
					int width, int height) {
				mLog.addDebugMsg(1,"I","surfaceChanged entered");
			}

			@Override
			public void surfaceDestroyed(SurfaceHolder holder) {
				mLog.addDebugMsg(1,"I","surfaceDestroyed entered");
			}
        });
        mLog.addDebugMsg(1,"I", "CustomSurfaceView created");
    };

	private void removeCameraPreview() {
		if (mSurfaceView!=null) {
	    	WindowManager windowManager = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
	        windowManager.removeView(mCameraPreviewFrame);
	        mSurfaceView=null;
	        mLog.addDebugMsg(1,"I", "Camera preview was removed");
		}
    };

    private Notification mNotification=null;
	private Notification.Builder mNotificationBuilder=null;
	
	private void initNotification() {
		closeNotification();

		Intent in=new Intent(mContext, ActivityMain.class);
		in.setAction(Intent.ACTION_MAIN);
		in.addCategory(Intent.CATEGORY_LAUNCHER);

		PendingIntent pi=PendingIntent.getActivity(mContext, 0, in, PendingIntent.FLAG_UPDATE_CURRENT);
		
		mNotificationBuilder=new Notification.Builder(this)
	        .setContentTitle("DriveRecorder")
	        .setContentText("Stopped")
	        .setSmallIcon(R.drawable.ic_48_recorder_started)
//	        .setTicker("DriveRecorder")
	        .setContentIntent(pi);
	};
	
	@SuppressLint("NewApi")
	private void showNotification() {
	    mNotification = mNotificationBuilder.build();

        NotificationManager nm=(NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(R.string.app_name, mNotification);
        
        startForeground(R.string.app_name, mNotification);
        if (!mWakeLock.isHeld()) {
        	mWakeLock.acquire();
        	mLog.addDebugMsg(1,"I", "Wakelock acquired");
        } else {
        	mLog.addDebugMsg(1,"I", "Wakelock already acquired");
        }
 	};
 	
	private void closeNotification() {
		NotificationManager nm=(NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancelAll();
        stopForeground(true);
        mNotification=null;
        if (mWakeLock.isHeld()) {
        	mWakeLock.release();
        	mLog.addDebugMsg(1,"I", "Wakelock released");
        } else {
        	mLog.addDebugMsg(1,"I", "Wakelock already released");
        }
 	};

 	private void setNotificatioIcon(boolean start) {
 		if (start) {
 			mNotificationBuilder.setSmallIcon(R.drawable.ic_48_recorder_run_anim)
 			.setContentText("Started");
 		} else {
 			mNotificationBuilder.setSmallIcon(R.drawable.ic_48_recorder_started)
 			.setContentText("Stopped");
 		}
 		if (mNotification!=null) {
 	 		mNotification = mNotificationBuilder.build();
 		    NotificationManager nm=(NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
 		    nm.notify(R.string.app_name, mNotification);
 		}
 	};
 
	final private void startBasicEventReceiver(GlobalParameters gp) {
		mLog.addDebugMsg(1, "I", "startBasicEventReceiver entered");
		IntentFilter intent = null;

		intent = new IntentFilter();
		intent.addAction(Intent.ACTION_SCREEN_OFF);
		intent.addAction(Intent.ACTION_SCREEN_ON);
		intent.addAction(Intent.ACTION_USER_PRESENT);
		registerReceiver(mSleepReceiver, intent);

	};

	final private void stopBasicEventReceiver(GlobalParameters gp) {
		mLog.addDebugMsg(1, "I", "stopBasicEventReceiver entered");
		unregisterReceiver(mSleepReceiver);
	};

	static final private boolean isKeyguardEffective(Context mContext) {
        KeyguardManager keyguardMgr=
        		(KeyguardManager)mContext.getSystemService(Context.KEYGUARD_SERVICE);
    	boolean result=keyguardMgr.inKeyguardRestrictedInputMode();
    	return result;
    };

	final private class SleepReceiver extends BroadcastReceiver {
		@Override
		final public void onReceive(Context c, Intent in) {
			String action = in.getAction();
			mLog.addDebugMsg(1, "I","Sleep receiver entered,"+
					" screenIsLocked="+mGp.screenIsLocked+
					", action=", action);
			if (action.equals(Intent.ACTION_SCREEN_ON)) {
				boolean kge = isKeyguardEffective(mContext);
				if (mGp.screenIsLocked) {
					if (!kge) {// Screen unlocked
						mGp.screenIsLocked = false;
					}
				} else {
					if (kge) {// Screen locked
						mGp.screenIsLocked = true;
					}
				}
			} else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
				boolean kge = isKeyguardEffective(mContext);
				if (kge) {// Screen locked
					mGp.screenIsLocked = true;
				} else {
					mGp.screenIsLocked = false;
				}
			} else if (action.equals(Intent.ACTION_USER_PRESENT)) {
				mGp.screenIsLocked = false;
			}
		}
	};

 	private AccerometerSensorReceiver mAccerometerSensorReceiver=new AccerometerSensorReceiver();
 	private Sensor mSensorAccerometer=null;
 	@SuppressWarnings("unused")
	private void setSensor() {
 		final SensorManager sm = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
 		mSensorAccerometer=getAccerometerSensor();
		sm.registerListener(mAccerometerSensorReceiver, 
				mSensorAccerometer, SensorManager.SENSOR_DELAY_UI);

 	};
 	
 	@SuppressWarnings("unused")
	private void unsetSensor() {
 		final SensorManager sm = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
 		mSensorAccerometer=getAccerometerSensor();
		sm.unregisterListener(mAccerometerSensorReceiver);

 	};

    final public Sensor getAccerometerSensor() {
    	Sensor result=null;
	    final SensorManager sm = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensors_list = sm.getSensorList(Sensor.TYPE_ACCELEROMETER);
        for(Sensor sensor: sensors_list) {
           	mLog.addDebugMsg(1,"I", "Accerometer sensor list size="+sensors_list.size()+
           			", type="+sensor.getType()+", vendor="+sensor.getVendor()+
        			", ver="+sensor.getVersion());
        	if (sensor.getType()==Sensor.TYPE_ACCELEROMETER) {
        		mLog.addDebugMsg(1, "I", "Accerometer sensor is available, name="+sensor.getName()+", vendor="+sensor.getVendor()+", version="+sensor.getVersion());
        		result=sensor;
            }
        }
        return result;
	};

 	private float mLowpassAccerometerX=0;
 	private float mLowpassAccerometerY=0;
 	private float mLowpassAccerometerZ=0;
 	private float mCurrAccerometerX=0,mCurrAccerometerY=0,mCurrAccerometerZ=0;
 	private float mMaxAccerometerX=0,mMaxAccerometerY=0,mMaxAccerometerZ=0;
    final private class AccerometerSensorReceiver implements SensorEventListener {
    	@Override
    	final public void onAccuracyChanged(Sensor sensor, int accuracy) {
//    		Log.v("","accracy="+accuracy);
    	}
    	@Override
    	final public void onSensorChanged(SensorEvent event) {
//    		float x=event.values[0];
//    		float y=event.values[1];
//    		float z=event.values[2];
//    		Log.v("","x="+x+", y="+y+", z="+z);
    		
			mLowpassAccerometerX=mLowpassAccerometerX*0.9f+event.values[0]*0.1f;
			mLowpassAccerometerY=mLowpassAccerometerY*0.9f+event.values[1]*0.1f;
			mLowpassAccerometerZ=mLowpassAccerometerZ*0.9f+event.values[2]*0.1f;
			
			mCurrAccerometerX=event.values[0]-mLowpassAccerometerX;
			mCurrAccerometerY=event.values[1]-mLowpassAccerometerY;
			mCurrAccerometerZ=event.values[2]-mLowpassAccerometerZ;

			if (mMaxAccerometerX<mCurrAccerometerX) {
				mMaxAccerometerX=mCurrAccerometerX;
				mLog.addDebugMsg(1,"I", "updated X="+mMaxAccerometerX);
			}
			if (mMaxAccerometerY<mCurrAccerometerY) {
				mMaxAccerometerY=mCurrAccerometerY;
				mLog.addDebugMsg(1,"I", "updated Y="+mMaxAccerometerY);
			}
			if (mMaxAccerometerZ<mCurrAccerometerZ) {
				mMaxAccerometerZ=mCurrAccerometerZ;
				mLog.addDebugMsg(1,"I", "updated Z="+mMaxAccerometerZ);
			}
    	}
    };

// Default uncaught exception handler variable
	private MyUncaughtExceptionHandler myUncaughtExceptionHandler = new MyUncaughtExceptionHandler();
	class MyUncaughtExceptionHandler implements UncaughtExceptionHandler{
			private boolean mCrashing=false;
		    private UncaughtExceptionHandler defaultUEH;
			public void init() {
				defaultUEH = Thread.currentThread().getUncaughtExceptionHandler();
		        Thread.currentThread().setUncaughtExceptionHandler(myUncaughtExceptionHandler);
			}
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
            	try {
                    if (!mCrashing) {
                        mCrashing = true;
            			printStackTrace(ex.toString(), ex.getStackTrace(), ex.getCause());           			
//            			File ldir=new File(mGp.settingsLogFileDir);
//            			if (!ldir.exists()) ldir.mkdirs();
//            			
//                		File lf=new File(mGp.settingsLogFileDir+"exception.txt");
//                		try {
//                			FileWriter fw=new FileWriter(lf,true);
//        					PrintWriter pw=new PrintWriter(fw);
//        					pw.println(end_msg);
//        					pw.println(end_msg2);
//        					pw.flush();
//        					pw.close();
//        				} catch (FileNotFoundException e) {
//        					e.printStackTrace();
//        				} catch (IOException e) {
//        					e.printStackTrace();
//        				}
 
                    }
                } finally {
                    defaultUEH.uncaughtException(thread, ex);
                }
            }
    };

    private void printStackTrace(String e_msg, StackTraceElement[] st, Throwable cause) {
    	String st_msg="";
    	for (int i=0;i<st.length;i++) {
    		st_msg+="\n at "+st[i].getClassName()+"."+
    				st[i].getMethodName()+"("+st[i].getFileName()+
    				":"+st[i].getLineNumber()+")";
    	}
		String end_msg=e_msg+st_msg;
		
		String end_msg2="";
		st_msg="";
		if (cause!=null) {
			st=cause.getStackTrace();
			if (st!=null) {
            	for (int i=0;i<st.length;i++) {
            		st_msg+="\n at "+st[i].getClassName()+"."+
            				st[i].getMethodName()+"("+st[i].getFileName()+
            				":"+st[i].getLineNumber()+")";
            	}
    			end_msg2="Caused by:"+cause.toString()+st_msg;
			}
		}

		mLog.addDebugMsg(1, "E", end_msg);
		if (!end_msg2.equals("")) mLog.addDebugMsg(1, "E", end_msg2);
    };
    
}