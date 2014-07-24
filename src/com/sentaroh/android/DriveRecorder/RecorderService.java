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
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnErrorListener;
import android.media.MediaRecorder.OnInfoListener;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.OnScanCompletedListener;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

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
	
	private boolean mDisableAutoStart=true;
	
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
    	
    	File lf=new File(mGp.videoFileDir);
    	lf.mkdirs();
    	
    	initNotification();

    	mSleepReceiver=new SleepReceiver();

    	createCameraPreview();

    	hidePreview();

    	mWidget=new WidgetService(mContext, mGp, mLog);
    	
    	startBasicEventReceiver(mGp);
    	
    	setSensor();
    	
    	mUiHandler.postDelayed(new Runnable(){
			@Override
			public void run() {
				mDisableAutoStart=false;				
			}
    	}, 1000);
    	
		defaultUEH = Thread.currentThread().getUncaughtExceptionHandler();
        Thread.currentThread().setUncaughtExceptionHandler(unCaughtExceptionHandler);

    };
    
	private boolean mToggleBtnEnabled=true;
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		String action="";
		if (intent!=null && intent.getAction()!=null) action=intent.getAction();
		mLog.addDebugMsg(1,"I","onStartCommand entered, action="+action);
		if (action.startsWith(WIDGET_RECORDER_PREFIX)) {
			mWidget.processWidgetIntent(intent, action);
		} else if (action.equals(TOGGLE_RECORDER_INTENT)) {
			if (mToggleBtnEnabled) {
				mWidget.setIconStartStop();
				mToggleBtnEnabled=false;
				if (mGp.isRecording) {
					stopRecorderThread();
				} else {
					startRecorderThread();
				}
			}
		} else if ((mGp.screenIsLocked && action.equals("android.media.VOLUME_CHANGED_ACTION"))) {
			if (!mGp.isRecording) {
				if (mToggleBtnEnabled) {
					mWidget.setIconStartStop();
					mToggleBtnEnabled=false;
					startRecorderThread();
				}
			}
		}
		return START_STICKY;
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
        unsetSensor();
        closeNotification();
        stopBasicEventReceiver(mGp);
        mLog.flushLog();
        removeCameraPreview();
    };
    
	private ThreadCtrl mTcRecorder=null;
	private void startRecorderThread() {
		mTcRecorder=new ThreadCtrl();
    	showNotification();
//		prepareCameraInstant();
		Thread th=new Thread() {
			public void run() {
				mLog.addDebugMsg(1,"I", "startRecorderThread start");
				mUiHandler.post(new Runnable(){
					@Override
					public void run() {
						startRecorder();
						mUiHandler.postDelayed(new Runnable(){
							@Override
							public void run() {
								mToggleBtnEnabled=true;
							}
						},1000);
					}
				});
				while(mTcRecorder.isEnabled()) {
					for(int i=0;i<(mGp.settingsRecordingDuration*600);i++) {
						if (!mTcRecorder.isEnabled()) {
							break;
						}
						SystemClock.sleep(100);
					}
					mUiHandler.postDelayed(new Runnable(){
						@Override
						public void run() {
							stopRecorder();
							if (mTcRecorder.isEnabled()) {
								mToggleBtnEnabled=false;
								startRecorder();
								mUiHandler.postDelayed(new Runnable(){
									@Override
									public void run() {
										mToggleBtnEnabled=true;
									}
								},1000);
							} else {
								mTcRecorder=null;
								mUiHandler.postDelayed(new Runnable(){
									@Override
									public void run() {
										hidePreview();
//								        if (!isActivityStarted()) stopSelf(); 
										mUiHandler.post(new Runnable(){
											@Override
											public void run() {
										    	removeCameraPreview();
										    	createCameraPreview();
											}
										});
									}
								},100);
							}
						}
					},100);
				}
		    	closeNotification();
			}
		};
		th.setName("Recorder");
		th.start();
	};

	private void stopRecorderThread() {
		mLog.addDebugMsg(1,"I", "stopRecorderThread entered, mTcRecorder="+mTcRecorder);
		if (mTcRecorder!=null) {
			mTcRecorder.setDisabled();
			SystemClock.sleep(100);
//			mSurfaceView.setVisibility(SurfaceView.GONE);
			mToggleBtnEnabled=true;
		}
	};

	private String getVideoFilePath() {
		String fp="";
		String dt=DateUtil.convDateTimeTo_YearMonthDayHourMinSec(System.currentTimeMillis());
		String dt_date=dt.substring(0,10).replaceAll("/", "-");
		String dt_time=dt.substring(11,19).replaceAll(":", "");
		fp=mGp.videoFileDir+mGp.videoFileNamePrefix+dt_date+"_"+dt_time+".mp4";
		mLog.addDebugMsg(1,"I", "New video file path="+fp);
		return fp;
	};

	static private void houseKeepVideoFiles(GlobalParameters mGp, LogUtil mLog) {
		File lf=new File(mGp.videoFileDir);
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
					mLog.addDebugMsg(1,"I", "Video file deleted :"+mGp.videoFileDir+list.get(i));
					lf=new File(mGp.videoFileDir+list.get(i));
					lf.delete();
					list.remove(i);
				}
			}
		}
	};
	
	private String mCurrRecordedFilePath="";
	
	private boolean prepareCameraInstant() {
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
    	
        int preview_height=0,preview_width=0;
        for(int i=0;i<mSupportedPreviewSizeList.size();i++) {
        	if (screen_width>mSupportedPreviewSizeList.get(i).width && screen_height>mSupportedPreviewSizeList.get(i).height) {
        		if (preview_width<mSupportedPreviewSizeList.get(i).width || preview_height<mSupportedPreviewSizeList.get(i).height) {
        			preview_width=mSupportedPreviewSizeList.get(i).width; 
        			preview_height=mSupportedPreviewSizeList.get(i).height; 
        		}
        	}
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

        boolean result=false;
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

	private boolean prepareRecorder() {
        try {
            mMediaRecorder = new MediaRecorder();
            mMediaRecorder.setCamera(mServiceCamera);
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            
            CamcorderProfile profile = null;
            int br_ratio=1;
            if (mGp.settingsRecordVideoSize==RECORD_VIDEO_QUALITY_LOW) {
            	profile=CamcorderProfile.get(0,CamcorderProfile.QUALITY_LOW);
            	br_ratio=1;
            } else if (mGp.settingsRecordVideoSize==RECORD_VIDEO_QUALITY_HIGH) {
            	profile=CamcorderProfile.get(0,CamcorderProfile.QUALITY_HIGH);
            	br_ratio=4;
            }
            
            profile.fileFormat = MediaRecorder.OutputFormat.MPEG_4;
            profile.videoBitRate=mGp.settingsVideoBitRate*br_ratio;

            mMediaRecorder.setProfile(profile);
            
            mLog.addDebugMsg(1,"I","Selected video size width="+profile.videoFrameWidth+", height="+profile.videoFrameHeight+
            		", frame rate="+profile.videoFrameRate+", video bit rate="+profile.videoBitRate);
            
//            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
//            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
//            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
            mCurrRecordedFilePath=getVideoFilePath();
            mMediaRecorder.setOutputFile(mCurrRecordedFilePath);
//            mMediaRecorder.setVideoFrameRate(30);
//            mMediaRecorder.setVideoEncodingBitRate(mGp.settingsVideoBitRate);
            
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
            }
            else mMediaRecorder.setOrientationHint(0);
            
            mMediaRecorder.prepare();
            mMediaRecorder.start();
            
            return true;
        } catch (IOException e) {
        	mLog.addDebugMsg(1,"E", e.getMessage());
            e.printStackTrace();
            return false;
        }
	};
	
	private void startRecorder(){
        try {
        	mLog.addDebugMsg(1,"I", "recorderStart entered");
        	
            houseKeepVideoFiles(mGp, mLog);
            
            if (prepareCameraInstant()) {
                if (prepareRecorder()) {
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
    
//    private void resetCameraInstant() {
//        mServiceCamera.release();
//        mServiceCamera = null;
//    };

    public void stopRecorder() {
    	mLog.addDebugMsg(1,"I", "recorderStop entered");
        try {
            mServiceCamera.reconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }
//        mServiceCamera.lock();
        
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        mMediaRecorder.release();
        
        mServiceCamera.lock();
        mServiceCamera.stopPreview();
        mServiceCamera.release();
        mServiceCamera = null;
        
        if (!mCurrRecordedFilePath.equals("")) {
        	String[] paths = new String[] {mCurrRecordedFilePath};
        	MediaScannerConnection.scanFile(getApplicationContext(), paths, null, mOnScanCompletedListener);
        }
        
        mGp.isRecording = false;
        mWidget.updateIcon(mGp.isRecording);
        setNotificatioIcon(mGp.isRecording);
//        hidePreview();
        try {
        	if (mActCallback!=null) mActCallback.notifyRecordingStopped();
		} catch (RemoteException e) {
			e.printStackTrace();
		}
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

        if (Build.VERSION.SDK_INT>=14) {
            mLog.addDebugMsg(1,"I","MaxZoom="+p.getMaxZoom()+
            		", Zoom="+p.getZoom()+
            		", VideoStabilizationSupported="+p.isVideoStabilizationSupported()+
            		", ZoomSupported="+p.isZoomSupported());
        } else {
            mLog.addDebugMsg(1,"I","MaxZoom="+p.getMaxZoom()+
            		", Zoom="+p.getZoom()+
            		", ZoomSupported="+p.isZoomSupported());
        }

//        camera.unlock();
//
        mSupportedVideoSizeList=p.getSupportedVideoSizes();
        mLog.addDebugMsg(1,"I","Available Video size :");
        for(int i=0;i<mSupportedVideoSizeList.size();i++) {
        	mLog.addDebugMsg(1,"I","   "+i+"  width="+
        			mSupportedVideoSizeList.get(i).width+", height="+mSupportedVideoSizeList.get(i).height);
        }
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
	        	startRecorderThread();
	        }
        };
		@Override
        final public void aidlStopRecorderThread() throws RemoteException {
			mLog.addDebugMsg(1,"I", "aidlStopRecorderThread entered, isRecording="+mGp.isRecording);
			if (mGp.isRecording) {
		        stopRecorderThread();
			}
        };
		@Override
        final public void aidlStopService() throws RemoteException {
			mLog.addDebugMsg(1,"I", "aidlStopService entered, isRecording="+mGp.isRecording);
			if (mGp.isRecording) {
				stopRecorderThread();
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
        LayoutParams lp=(LayoutParams) mSurfaceView.getLayoutParams();
        lp.height=mSHowedScreenHeight;
        lp.width=mShowedScreenWidth;
        wm.updateViewLayout(mSurfaceView, lp);
        lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
    };
    
    private void hidePreview() {
    	mLog.addDebugMsg(1,"I", "hidePreview entered");
    	mPreviewAvailable=false;
    	if (mSurfaceView!=null) {
           	WindowManager wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
            LayoutParams lp=(LayoutParams) mSurfaceView.getLayoutParams();
            lp.height=14;
            lp.width=14;
            lp.gravity = Gravity.LEFT | Gravity.BOTTOM;
            wm.updateViewLayout(mSurfaceView, lp);
    	}
    };
    
	final static private float toPixel(Resources res, int dip) {
		float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, res.getDisplayMetrics());
		return px;
	};

	private void createCameraPreview() {
    	WindowManager windowManager = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
    	mSurfaceView = new SurfaceView(this);
    	mSurfaceHolder=mSurfaceView.getHolder();
        LayoutParams layoutParams = new WindowManager.LayoutParams(
        		20,20,
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE+
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.CENTER | Gravity.BOTTOM;
        windowManager.addView(mSurfaceView, layoutParams);
        mSurfaceView.setZOrderOnTop(true);
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
        mLog.addDebugMsg(1,"I", "SurfaceView created");
    };

	private void removeCameraPreview() {
		if (mSurfaceView!=null) {
	    	WindowManager windowManager = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
	        windowManager.removeView(mSurfaceView);
	        mSurfaceView=null;
	        mLog.addDebugMsg(1,"I", "SurfaceView removed");
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
 	};
 	
	private void closeNotification() {
		NotificationManager nm=(NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancelAll();
        stopForeground(true);
        mNotification=null;
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
 	private void setSensor() {
 		final SensorManager sm = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
 		mSensorAccerometer=getAccerometerSensor();
		sm.registerListener(mAccerometerSensorReceiver, 
				mSensorAccerometer, SensorManager.SENSOR_DELAY_UI);

 	};
 	
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

			checkAutoStart();

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

//    private long mLastShockDetectTime=0, mFirstShockDetectTime=0;
    private void checkAutoStart() {
    	if (mGp.settingsAutoRecordingStartLevel>0 && !mGp.isRecording && !mDisableAutoStart) {
//    		float tl=0;
//    		if (mGp.settingsAutoRecordingStartLevel==1) tl=8f;
//    		else if (mGp.settingsAutoRecordingStartLevel==2) tl=10f;
//    		else if (mGp.settingsAutoRecordingStartLevel==3) tl=15f;
//    		else if (mGp.settingsAutoRecordingStartLevel==4) tl=18f;
//    		else if (mGp.settingsAutoRecordingStartLevel==5) tl=20f;
//    		if (mCurrAccerometerX>tl || mCurrAccerometerY>tl || mCurrAccerometerZ>tl) {
//    			if (mFirstShockDetectTime==0) {
//    				mFirstShockDetectTime=System.currentTimeMillis();
//    			} else {
//    				long span_diff=System.currentTimeMillis()-mFirstShockDetectTime;
//    				if (span_diff>5000) {//Re-Init
//    					mFirstShockDetectTime=System.currentTimeMillis();
//    					mLastShockDetectTime=0;
//    				} else {
//    					if (mLastShockDetectTime==0) {
//    						mLastShockDetectTime=System.currentTimeMillis();
//    					} else {
//    						
//    					}
//    				}
//    			}
//    			startRecorderThread();
//    		}
    	}
    }
    
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