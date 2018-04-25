package com.ota;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import com.ota.download.CGDownLoadReceiver;
import com.ota.download.CGNetWorkUtils;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.WebAddress;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.RecoverySystem;
import android.os.StatFs;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.widget.Toast;
import android.provider.Downloads;
import android.provider.Telephony;
// import java.lang.String;
import android.telephony.TelephonyManager;

import android.app.DownloadManager;

public class OTAUpdatesActivity extends Activity 
{	
	private SharedPreferences sharedPref;
	private SharedPreferences.Editor editor;
	private UpdatesInfo updatesInfo;
	private SharedPreferences m_version;
	private ProgressDialog checkProgressDialog;
	private SharedPreferences is_sharedPref; 
	private SharedPreferences.Editor is_editor;
	private int battLevel;
	private final static String SHARE_FILE = "downloadinfo";
	
	private PendingIntent pendingIntent;
	private AlarmManager downloadLaterAlram;
	private NotificationManager notificationManager = null; 
	private Notification notification = null; 
	private static final int NOTIFICATION_ID = 0x1001;
	//cg modify 2012-10-11
    private static final int BYTE=1024;
    private NetworkChangeReceiver  mReceiver;
    public boolean  isSetCMNET = false;
	public boolean  m_is_waiting_for_network = false;
    public static final String PREFERRED_APN_URI =
            "content://telephony/carriers/preferapn";
    public static final String PREFERRED_APN_URI_SIM1=
            "content://telephony_sim1/carriers/preferapn";
    public static final String APN_ID = "apn_id";
    public static final String APN_ID_SIM1 = "apn_id_sim1";
	private static final Uri PREFERAPN_URI = Uri.parse(PREFERRED_APN_URI);
	private static final Uri PREFERAPN_URI_SIM1= Uri.parse(PREFERRED_APN_URI_SIM1);
    private int mSubId;
    private String mSelectedKey;

	Handler mHandler = new Handler();
	public boolean  m_is_waiting_for_getdata = false;
	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);

		//初始化
		init();
        
		//下载状态
		int startState = getIntent().getIntExtra(StateValue.STARTSTATE, 0);
		
	    Log.d("OTAUpdatesActivity", "startState :" + startState + ", result :" + updatesInfo.result);
	    
	    is_sharedPref= getApplicationContext().getSharedPreferences("is_download", Context.MODE_MULTI_PROCESS);
	    is_editor=is_sharedPref.edit();
		boolean isdownloading = is_sharedPref.getBoolean("isdownloading", false);
	    Log.d("OTAUpdatesActivity", "isdownloading:" + isdownloading);

		if(startState == 0)
		{
			int istatus = 0;
//			istatus = condition();
			//是否在下载中
	    		Log.d("OTAUpdatesActivity", "updatesInfo.result = " + updatesInfo.result);
			/*if(
				(updatesInfo.result == 2) &&
				istatus > 0 &&
				(200 != istatus)
			  )*/
			if (isdownloading)
			{
				long free = getSDCardFreeMemory();
				if(free < 0)
				{
				    Toast.makeText(getApplicationContext(), R.string.no_card, Toast.LENGTH_LONG).show();
				    finish();
				    return ;
				}

				 if("Optional".equals(updatesInfo.priority))
				{
					if (!isNetConnect()) 
					{
						Toast.makeText(getWindow().getContext(), R.string.no_net,Toast.LENGTH_LONG).show();
						finish();
					}
					else
					{
					    Toast.makeText(this, R.string.downloading,Toast.LENGTH_LONG).show();
					    finish();
					}
				}
				 else if("Mandatory".equals(updatesInfo.priority))
				{

					if (!isNetConnect()) 
					{
						Toast.makeText(getWindow().getContext(), R.string.no_net,Toast.LENGTH_LONG).show();
						finish();
					}
					else 
					{
						if(!CGNetWorkUtils.isNetWorkWifi(this))
						{
							SharedPreferences mSharedPreferences = getApplication().getSharedPreferences(SHARE_FILE, Context.MODE_MULTI_PROCESS);
							SharedPreferences.Editor mEditor = mSharedPreferences.edit();
							boolean downloadingwifionly = mSharedPreferences.getBoolean("wifionly", false);
							Log.i("OTAUpdatesActivity", "downloadingwifionly:"+downloadingwifionly); 
							if(downloadingwifionly)
							{
								checkForUpdates();
							}
							else
							{
								 Toast.makeText(this, R.string.downloading,Toast.LENGTH_LONG).show();
							     finish();
							}

						}
						else{
							 Toast.makeText(this, R.string.downloading,Toast.LENGTH_LONG).show();
						     finish();
						}
					}
				}
			}
			else
			{
				updatesLater(false);
				//更新包是否存在
	    		Log.d("OTAUpdatesActivity", "updatesInfo.filePath = " + updatesInfo.filePath);
				if(isFileExists(updatesInfo.filePath))
				{
					//cg modify 2012-10-5 start
					if (updatesInfo.fileSize==new File(updatesInfo.filePath).length())
					{
						//提示更新
						updatesDialog();
					}
					else
					{
						Log.d("OTAUpdatesActivity", "onCreate() file size err updatesInfo.filePath = " + updatesInfo.filePath);
						new File(updatesInfo.filePath).delete();
						updatesInfo.filePath="";
//						resetApn();
						finish();
					}
					//cg modify 2012-10-5 end
				}
				else
				{
					//setApnCMNET();
					Log.i("OTAUpdatesActivity", "check");
//					检查是否有更新
					checkForUpdates();
				}
			}        	
		}
		else
		{
				switch(startState)
				{
				case StateValue.DOWNLOAD_START: //提示下载
					//cg modify 2012-10-11
					getData();
				   //cg modify 2012-12-19 start
					if(isFileExists(updatesInfo.filePath))
					{
						if (updatesInfo.fileSize==new File(updatesInfo.filePath).length())
						{
						      updatesDialog();
						}
						else
						{
							new File(updatesInfo.filePath).delete();
							updatesInfo.filePath="";
//							resetApn();
							finish();
						}
					}
					else
					{
						if ("Optional".equals(updatesInfo.priority))
						{
							downloadDialog();
						}
						else if ("Mandatory".equals(updatesInfo.priority))
						{
							Log.d("OTAUpdatesActivity", "battLevel*****:"+battLevel);  
							String fileName = updatesInfo.downloadUrl.substring(updatesInfo.downloadUrl.lastIndexOf('/')+1);
							String filePath = Environment.getExternalStorageDirectory().getPath() + "/download";
							CGDownLoadReceiver.startDownLoad(getApplicationContext(), updatesInfo.downloadUrl, filePath,fileName,updatesInfo.fileSize,updatesInfo.priority,true,true,true);
							finish();
						}
					}
					//cg modify 2012-12-19 end
					break;

				case StateValue.DOWNLOAD_OK: //下载完成 
//					resetApn();
					updatesInfo.filePath = getIntent().getStringExtra(StateValue.FILEPATH);
					Log.i("OTAUpdatesActivity", "filePath:"+new File(updatesInfo.filePath).length());
					Log.i("OTAUpdatesActivity","updatesInfo.fileSize:"+updatesInfo.fileSize);
					//cg modify,2012-9-25
					if (updatesInfo.fileSize==new File(updatesInfo.filePath).length())
					{
					    editor.putString(StateValue.FILEPATH, updatesInfo.filePath);
        				editor.commit();

					    cg_set_update_state(0);

					    updatesDialog();
					    break;
					}
					else
					{
						Toast.makeText(this, R.string.download_err, Toast.LENGTH_LONG).show();
//						cg_dl_delete();
						new File(updatesInfo.filePath).delete();
					}

				case StateValue.DOWNLOAD_FAIL: //下载失败
//					resetApn();
					cg_set_update_state(1);
					Toast.makeText(this, R.string.download_err, Toast.LENGTH_LONG).show();
					 new File(updatesInfo.filePath).delete();
					 is_editor.putBoolean("isdownloading", false);
					 editor.commit();
					// cg_dl_delete();
					finish();
					break;

				case StateValue.DOWNLOAD_DEL: //取消下载
//					resetApn();
					cg_set_update_state(1);
					Toast.makeText(this, R.string.cancle, Toast.LENGTH_LONG).show();
					finish();
					break;
		            
				default:
//					resetApn();
					cg_set_update_state(1);
					finish();
					break;
				}
		}
	}	

	private int cg_set_update_state(int istate)
	{
		Log.d("OTAUpdatesActivity", "cg_set_update_state() old = " + updatesInfo.result + ", new = " + istate);
		updatesInfo.result = istate;
		return 0;
	}

	private int cg_save_udpate_state()
	{
		Log.d("OTAUpdatesActivity", "cg_save_udpate_state() updatesInfo.result = " + updatesInfo.result);
		editor.putInt(StateValue.RESULT, updatesInfo.result);
		editor.commit();
		return 0;
	}

	private int condition()
	{
		int iret = -1;
		int StatusColumnId = 0;
		int istatus = 0;
		Cursor cursor = null;
		do
		{
			if (updatesInfo.db_url.equals(""))
			{
				iret = 0;
				break;
			}
			Log.d("OTAUpdatesActivity","query() updatesInfo.db_url = " + updatesInfo.db_url);
			cursor = getContentResolver().query(Uri.parse(updatesInfo.db_url),
				null,null,null,null);

			if (null == cursor)
			{
				iret = -10;
				break;
			}

			try {			
				cursor.moveToFirst();
				
				int index = cursor.getColumnIndex(Downloads.Impl.COLUMN_URI);
				if (index < 0)
				{
					iret = -15;
					break;
				}
				String path=cursor.getString(index);

				

				if(!path.equals(updatesInfo.downloadUrl))
				{
					iret = -20;
					break;
				}

				Log.d("OTAUpdatesActivity", "updatesInfo.downloadUrl = " + updatesInfo.downloadUrl);
				iret = 0;
				StatusColumnId = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS);
	    		Log.d("OTAUpdatesActivity", "StatusColumnId = " + StatusColumnId);
				if (0 != StatusColumnId)
				{
					istatus = cursor.getInt(StatusColumnId);
					iret = istatus;
				}			
				
			}
			catch(Exception e)
			{
				iret = -30;
				break;
			}

		}while(false);

		if (null != cursor)
		{
			cursor.close();
			cursor = null;
		}
	    	Log.d("OTAUpdatesActivity", "condition() = " + iret);
		return iret;
	}
	//cg modify ,2012-9-25
	private void cg_dl_delete()
	{
		ContentResolver cr = this.getContentResolver();
	    String data=getIntent().getStringExtra(StateValue.DATA);
	    Uri uri = Uri.parse(data); 
	    cr.delete(uri, null, null); 
	    new File(updatesInfo.filePath).delete();
	}
	
    /*
     * 初始化数据
     */
    private void init()
    {
        sharedPref = getSharedPreferences(StateValue.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        editor = getSharedPreferences(StateValue.SHARED_PREFERENCES_NAME,Context.MODE_PRIVATE).edit();
        
        updatesInfo = new UpdatesInfo();        
        cg_set_update_state(sharedPref.getInt(StateValue.RESULT, 1));
        updatesInfo.src_version = sharedPref.getString(StateValue.SRC_VER, "0");
        updatesInfo.dst_version = sharedPref.getString(StateValue.DST_VER, "0");
        updatesInfo.description = sharedPref.getString(StateValue.DSTCRIPTION, "");
        updatesInfo.downloadUrl = sharedPref.getString(StateValue.DOWNLOADURL, "");
        updatesInfo.fileSize = sharedPref.getInt(StateValue.SIZE, 0);
        updatesInfo.priority = sharedPref.getString(StateValue.PRIORITY, "");
        updatesInfo.sessionId = sharedPref.getString(StateValue.SESSIONID, "");
        updatesInfo.filePath = sharedPref.getString(StateValue.FILEPATH, "");
        updatesInfo.waitTimes = sharedPref.getInt(StateValue.WAITTIMES, 0);		
		updatesInfo.db_url = sharedPref.getString(StateValue.DB_URL, "");
		Log.d("OTAUpdatesActivity", "init() datesInfo.db_url = " + updatesInfo.db_url);
		
        pendingIntent = PendingIntent.getActivity(this,0,new Intent(this, OTAUpdatesActivity.class),0);
        downloadLaterAlram = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        
        notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE); 
        notification = new Notification(); 

        //注册广播接收器
        registerReceiver(mBatteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        registerReceiver(mCheckResultReceiver, new IntentFilter(StateValue.CHECKBROADACTION));
		
		mReceiver = new  NetworkChangeReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mReceiver, intentFilter);
    }
    
    /*
     * 检查是否有更新
     */
    private void checkForUpdates()
    {
        cg_set_update_state(1);
        //cg modify 2012-10-13
//        updatesInfo.filePath = "";
        updatesInfo.waitTimes = 0;
        editor.putString(StateValue.FILEPATH, updatesInfo.filePath);
        editor.putInt(StateValue.WAITTIMES, updatesInfo.waitTimes);
        editor.commit();
		    	
        if(!isNetConnect())
        {
			//网络不可用
			Toast.makeText(getWindow().getContext(), R.string.no_net, Toast.LENGTH_LONG).show();
			finish();
        }
        else
        {
        	//弹出等待框
        	setProgressDialog(true);
        	//启动service去检查
//        	setApnCmnetBefore(); 
//        	if(!m_is_waiting_for_getdata) {
        		Intent updateIntent =new Intent(this, CheckNewVerService.class);
        		updateIntent.putExtra(StateValue.ISAUTOCHECK, false); 
        		startService(updateIntent);
//        	}
        }
    }

    /*
     * 网络是否可用
     */
    private boolean isNetConnect()
    {
        ConnectivityManager connectivity = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE); 
        
        if (connectivity != null) 
        { 
            NetworkInfo info = connectivity.getActiveNetworkInfo(); 
            if (info != null && info.isConnected() && (info.getState() == NetworkInfo.State.CONNECTED)) 
            {
            	return true; 
            } 
        }
        
    	return false;
    }

    /*
     * 查询等待框
     */
    private void setProgressDialog(boolean show)
    {
    	if(show)
    	{
    		checkProgressDialog = new ProgressDialog(this);
    		checkProgressDialog.setMessage(getString(R.string.wait));
    		checkProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
    		//cg pcx modify for dialog disappear,2013-5-13 start
    		checkProgressDialog.setCanceledOnTouchOutside(false);
    		//cg pcx modify for dialog disappear,2013-5-13 end
    		checkProgressDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                    	checkProgressDialog.dismiss();
                        finish();
                    	return true;
                    }
                    return false;
                }
            });	
    		checkProgressDialog.show();
    	}
    	else
		{
    		if(checkProgressDialog != null)
    		{
        		checkProgressDialog.dismiss();
    		}
		}
    }

    /*
     * 接收查询结果信息
     */
    private BroadcastReceiver mCheckResultReceiver = new BroadcastReceiver() 
    {
        @Override
        public void onReceive(Context context, Intent intent) 
        {
        	if (StateValue.CHECKBROADACTION.equals(intent.getAction())) 
        	{
        		setProgressDialog(false);
        		int msg = intent.getIntExtra("msg", StateValue.CHECK_NO_NEED_UP);
        		Log.i("pan", "msg:"+msg);
        		if(msg == StateValue.CHECK_NEED_UP)
        		{
					cg_set_update_state(intent.getIntExtra(StateValue.RESULT, 1));
            		updatesInfo.src_version = intent.getStringExtra(StateValue.SRC_VER);
            		updatesInfo.dst_version = intent.getStringExtra(StateValue.DST_VER);
            		updatesInfo.description = intent.getStringExtra(StateValue.DSTCRIPTION);
            		updatesInfo.downloadUrl = intent.getStringExtra(StateValue.DOWNLOADURL);
            		updatesInfo.fileSize = intent.getIntExtra(StateValue.SIZE, 0);
            		updatesInfo.priority = intent.getStringExtra(StateValue.PRIORITY);
            		updatesInfo.sessionId = intent.getStringExtra(StateValue.SESSIONID);
            		
                    cg_save_udpate_state();
                    editor.putString(StateValue.SRC_VER, updatesInfo.src_version);
                    editor.putString(StateValue.DST_VER, updatesInfo.dst_version);
                    editor.putString(StateValue.DSTCRIPTION, updatesInfo.description);
                    editor.putString(StateValue.DOWNLOADURL, updatesInfo.downloadUrl);
                    editor.putInt(StateValue.SIZE, updatesInfo.fileSize);
                    editor.putString(StateValue.PRIORITY, updatesInfo.priority);
                    editor.putString(StateValue.SESSIONID, updatesInfo.sessionId);
                    editor.commit();
        			
                    //提醒用户下载
                    downloadDialog();
        		}
        		else
        		{
            		switch(msg)
            		{
            		case StateValue.CHECK_NO_NET:
            			//网络不可用
        	            Toast.makeText(getWindow().getContext(), R.string.no_net, Toast.LENGTH_LONG).show();
        	            finish();
            			break;

            		case StateValue.CHECK_NO_NEED_UP:
                        //已经最新
                        Toast.makeText(getWindow().getContext(), R.string.latest, Toast.LENGTH_LONG).show();
                        finish();
            			break;

            		case StateValue.CHECK_ERR_DATA:
                        //服务器返回数据错误
        	            Toast.makeText(getWindow().getContext(), R.string.err_data, Toast.LENGTH_LONG).show();
        	            finish();
            			break;

            		default: //case StateValue.CHECK_SP_ERR:
                    	//服务器暂时不可用
                        Toast.makeText(getWindow().getContext(), R.string.check_error, Toast.LENGTH_LONG).show();
                        finish();
            			break;
            		}
            		
            		resetApnaftgetdata();
        		}
        	}
        }
    }; 
    	
    /*
     * 提醒用户下载更新包
     */
    private void downloadDialog()
    {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);    
        
        alert.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) 
            {
            	//cg modify pcx 2012-10-30
                if (keyCode == KeyEvent.KEYCODE_BACK&&event.getAction()==KeyEvent.ACTION_UP)
                {
                	cg_set_update_state(1);
                	dialog.dismiss();
                	finish();
                	return true;
                }
                return false;
            }
        });        
        
        String msg = String.format(getString(R.string.download_msg), 
        		updatesInfo.dst_version,
        		//cg modify 2012-10-11
//      		updatesInfo.fileSize,
        		updatesInfo.fileSize/BYTE,
        		updatesInfo.description
        		);
        	
        alert.setTitle(R.string.download_title)
        	.setMessage(msg)
        	.setPositiveButton(R.string.download, new DialogInterface.OnClickListener() 
        	{ 
        		public void onClick(DialogInterface dialog, int which) 
        		{
        			//开始下载
				/// cg  modify ~!!!!!!!!!!!!!!!!!!
        		// check battery and t card start
					long free = getSDCardFreeMemory();
					int ishouldbeclose = 0;
					do
					{
						if(free < 0)
						{
						    Toast.makeText(getWindow().getContext(), R.string.no_card, Toast.LENGTH_LONG).show();
						    ishouldbeclose = 1;
						    break;
						}
						else if(free < updatesInfo.fileSize)
						{
						    Toast.makeText(getWindow().getContext(), R.string.no_room, Toast.LENGTH_LONG).show();
						    ishouldbeclose = 1;
						    break;
						}
						if (battLevel < 20) {
							Toast.makeText(getWindow().getContext(), R.string.low_battery,Toast.LENGTH_LONG).show();
						    ishouldbeclose = 1;
						    break;
						}
					}while(false);
					if (ishouldbeclose == 1)
					{
							dialog.dismiss();
							// cg modify 2012-10-13
//							resetApn();
							finish();
							return;
					}
					// check battery and t card end
        			
				if (true)
				{					
//					if (setApnCMNET() < 0)
//					{
//						   dialog.dismiss(); 
//						   //cg modify 2012-10-13
//						   Toast.makeText(getWindow().getContext(), R.string.no_net, Toast.LENGTH_LONG).show();
//						   finish();
//					}
					String fileName = updatesInfo.downloadUrl.substring(updatesInfo.downloadUrl.lastIndexOf('/')+1);
					String filePath = Environment.getExternalStorageDirectory().getPath() + "/download";
					CGDownLoadReceiver.startDownLoad(getApplicationContext(), updatesInfo.downloadUrl, filePath,fileName,updatesInfo.fileSize,updatesInfo.priority,false,true,false);
					Toast.makeText(getWindow().getContext(),R.string.wait, Toast.LENGTH_LONG).show();
		        	finish();
				}
				else
				{
        				startDownload();
				}
         	    } 
             })
             .setNegativeButton(R.string.cancle,new DialogInterface.OnClickListener()
             { 
                 public void onClick(DialogInterface dialog, int which) 
                 { 
                 	dialog.dismiss(); 
                 	//cg modify 2012-10-13
                 	Toast.makeText(getWindow().getContext(), R.string.download_cancle, Toast.LENGTH_LONG).show();
//					resetApn();
                 	finish();
                 }
             }
        ); 
        // cg pcx modify for dialog disappear,2013-5-13 start
       AlertDialog adialog=alert.create();
       adialog.setCanceledOnTouchOutside(false);
       adialog.show();
       // cg pcx modify for dialog disappear,2013-5-13 end
    }

    /*
     * 开始下载
     */
    private void startDownload()
    {		
        //cg pcx modify for low_battery prompt end
 		String fileName = updatesInfo.downloadUrl.substring(updatesInfo.downloadUrl.lastIndexOf('/')+1);
 		String mimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(updatesInfo.downloadUrl));
		try {
			File file = new File(Environment.getExternalStorageDirectory().getPath() + "/download");
			if (!file.exists()) {
				file.mkdir();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		// cg pcx modify for Ota download package request error prompt mantis:0034445 start
		String  fileNameHint=new java.io.File(Environment.getExternalStorageDirectory().getPath() + "/download/"+fileName).toURI().toString();
		// cg pcx modify for Ota download package request error prompt mantis:0034445 end

		// cg_clean_down_process(fileName);
 		WebAddress webAddress;
 		try {
 			webAddress = new WebAddress(updatesInfo.downloadUrl);
 			webAddress.mPath = encodePath(webAddress.mPath);

 			ContentValues values = new ContentValues();
 			values.put(Downloads.Impl.COLUMN_URI, webAddress.toString());
 			values.put(Downloads.Impl.COLUMN_NOTIFICATION_PACKAGE,getPackageName());
 			values.put(Downloads.Impl.COLUMN_NOTIFICATION_CLASS,DownloadReceiver.class.getCanonicalName());
 			values.put(Downloads.Impl.COLUMN_VISIBILITY,Downloads.Impl.VISIBILITY_VISIBLE);
 			values.put(Downloads.Impl.COLUMN_MIME_TYPE, mimetype);
 			values.put(Downloads.Impl.COLUMN_FILE_NAME_HINT, fileNameHint);
 			values.put(Downloads.Impl.COLUMN_DESCRIPTION, webAddress.mHost);
 			//cg pcx modify for Ota download package request error prompt mantis:0034445 start
			values.put(Downloads.Impl.COLUMN_TITLE, fileName);
			values.put(Downloads.Impl.COLUMN_IS_VISIBLE_IN_DOWNLOADS_UI, 1);
			values.put(Downloads.Impl.COLUMN_DESTINATION,Downloads.Impl.DESTINATION_FILE_URI);
			//cg pcx modify for Ota download package request error prompt mantis:0034445 end
 			final Uri contentUri = getContentResolver().insert(Downloads.Impl.CONTENT_URI, values);
			
			editor.putString(StateValue.DB_URL, contentUri.toString());
			Log.d("OTAUpdatesActivity", "contentUri.toString() = " + contentUri.toString());
			editor.putBoolean("cgisdownloading", true);
        	editor.commit();

    		cg_set_update_state(2);
            Toast.makeText(getWindow().getContext(), R.string.wait, Toast.LENGTH_LONG).show();
 		}
 		catch (Exception e) 
 		{
        	cg_set_update_state(1);
            Toast.makeText(getWindow().getContext(), R.string.err_req, Toast.LENGTH_LONG).show();
 		}
 		
        finish();
    }

    /*
     * 修正编码
     */
    private static String encodePath(String path) {
        char[] chars = path.toCharArray();

        boolean needed = false;
        for (char c : chars) {
            if (c == '[' || c == ']') {
                needed = true;
                break;
            }
        }
        if (needed == false) {
            return path;
        }

        StringBuilder sb = new StringBuilder("");
        for (char c : chars) {
            if (c == '[' || c == ']') {
                sb.append('%');
                sb.append(Integer.toHexString(c));
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }
	
	/*
	 * 文件是否存在
	 */
	private boolean isFileExists(String filePath)
	{		
		File downloadFile = new File(filePath); 

		if(downloadFile != null && downloadFile.exists())
		{
			return true;			
		}

		return false;
	}
    
    /*
     * 获取T卡剩余空间
     */
    public long getSDCardFreeMemory() 
    {  
    	long sdCardInfo = -1;  

    	String state = Environment.getExternalStorageState();  
    	if (Environment.MEDIA_MOUNTED.equals(state)) 
    	{  
    		File sdcardDir = Environment.getExternalStorageDirectory();  
    		StatFs sf = new StatFs(sdcardDir.getPath());  
    		long bSize = sf.getBlockSize();  
    		long availBlocks = sf.getAvailableBlocks();  
      
    		sdCardInfo = bSize * availBlocks;
        }
    	
    	return sdCardInfo;  
    }

    /*
     * 提示升级
     */
    private void updatesDialog()
    {
        AlertDialog.Builder alert = new AlertDialog.Builder(this); 
        alert.setOnKeyListener(new DialogInterface.OnKeyListener() 
        {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) 
            {
            	//cg modify pcx 2012-10-30
                if (keyCode == KeyEvent.KEYCODE_BACK&&event.getAction()==KeyEvent.ACTION_UP)
                {
                	dialog.dismiss();
        			finish();
                	//不响应返回键
                    return true;
                }
                return false;
            }
        });
        
        String msg = String.format(getString(R.string.download_msg), 
        		updatesInfo.dst_version,  
        		//cg modify 2012-10-11
//        		updatesInfo.fileSize,
        		updatesInfo.fileSize/BYTE,
        		updatesInfo.description
        		);
        
        alert.setTitle(R.string.updates_title)
        	.setMessage(msg)
        	.setPositiveButton(R.string.updates, new DialogInterface.OnClickListener() { 
        		public void onClick(DialogInterface dialog, int which) 
        		{
        			//立即升级
        			updates();
//				resetApnaftgetdata();
        		    finish();
         	    } 
             });
        

        	alert.setNegativeButton(R.string.later,new DialogInterface.OnClickListener(){ 
                public void onClick(DialogInterface dialog, int which) { 
        			//一小时后提醒升级
        			updatesLater(false);
//				resetApnaftgetdata();
        			dialog.dismiss(); 
        			finish();
                } 
            }); 
                
        	 // cg pcx modify for dialog disappear,2013-5-13 start
            AlertDialog adialog=alert.create();
            adialog.setCanceledOnTouchOutside(false);
            adialog.show();
            // cg pcx modify for dialog disappear,2013-5-13 end
    }
        
    /*
     *1小时后提醒升级
     */
    private void updatesLater(boolean start)
    {
    	if(start)
    	{
    		updatesInfo.waitTimes++;
            editor.putInt(StateValue.WAITTIMES, updatesInfo.waitTimes);
			editor.commit();
         	cg_set_update_state(0);
         	
			downloadLaterAlram.set(AlarmManager.RTC,System.currentTimeMillis()+60*60*1000,pendingIntent);
			
			notification.icon = R.drawable.icon; 
			notification.setLatestEventInfo(this,getString(R.string.updates),"",pendingIntent); 
		    notificationManager.notify(NOTIFICATION_ID,notification); 
    	}
    	else
    	{
         	cg_set_update_state(1);
    		if(downloadLaterAlram != null)
    		{
    			downloadLaterAlram.cancel(pendingIntent);
    		}
    		if(notificationManager != null)
    		{
    			notificationManager.cancel(NOTIFICATION_ID);
    		}
    	}    	
    }
        
    /*
     * 升级
     */
    private void updates()
    {
		cg_set_update_state(1);
		updatesInfo.waitTimes = 0;
        cg_save_udpate_state();
        editor.putInt(StateValue.WAITTIMES, updatesInfo.waitTimes);
		editor.commit();

		//更新包是否存在
		if(!isFileExists(updatesInfo.filePath))
		{
            Toast.makeText(getWindow().getContext(), R.string.no_file, Toast.LENGTH_LONG).show();
    	    return;
		}
		
    	//电量太少
		if(battLevel < 20)
		{
            Toast.makeText(getWindow().getContext(), R.string.low_battery, Toast.LENGTH_LONG).show();
    	    return;
		}

    	Intent broadIntent = new Intent("android.intent.action.OTARECOVERY");
		broadIntent.putExtra("filePath", updatesInfo.filePath);

//		ContentResolver cr = this.getContentResolver();
//		Uri uri = Uri.parse(updatesInfo.db_url);
//		cr.delete(uri, null, null);

        updatesInfo.filePath = ""; 
        editor.putString(StateValue.FILEPATH, updatesInfo.filePath);
        editor.commit();
      
        
    	sendBroadcast(broadIntent);
    }

    /*
     * 接收电量信息
     */
    private BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver() 
    {
        @Override
        public void onReceive(Context context, Intent intent) 
        {
        	String action = intent.getAction();
        	if (Intent.ACTION_BATTERY_CHANGED.equals(action)) 
        	{
        		int level = intent.getIntExtra("level", 0);
        		int scale = intent.getIntExtra("scale", 100);
        		battLevel = level * 100 / scale;
        	}
        }
    };  
    
	/*
     * 退出
     */
	@Override
	protected void onDestroy() 
    {
		super.onDestroy();
		Log.e("OTAUpdatesActivity", "onDestroy B");

        cg_save_udpate_state();
        editor.commit();
        
        unregisterReceiver(mBatteryInfoReceiver);
        unregisterReceiver(mCheckResultReceiver);
		try{
				unregisterReceiver(mReceiver);
		}
		catch(IllegalArgumentException e)
		{
		}
        
        setProgressDialog(false);
		Log.e("OTAUpdatesActivity", "onDestroy E");
    }
	//cg modify 2012-10-11 
	private void getData()
	{
		cg_set_update_state(getIntent().getIntExtra(StateValue.RESULT, 1));
		updatesInfo.src_version = getIntent().getStringExtra(StateValue.SRC_VER);
		updatesInfo.dst_version =getIntent().getStringExtra(StateValue.DST_VER);
		updatesInfo.description = getIntent().getStringExtra(StateValue.DSTCRIPTION);
		updatesInfo.downloadUrl =getIntent().getStringExtra(StateValue.DOWNLOADURL);
		updatesInfo.fileSize = getIntent().getIntExtra(StateValue.SIZE, 0);
		updatesInfo.priority = getIntent().getStringExtra(StateValue.PRIORITY);
		updatesInfo.sessionId = getIntent().getStringExtra(StateValue.SESSIONID);
		
        cg_save_udpate_state();
        editor.putString(StateValue.SRC_VER, updatesInfo.src_version);
        editor.putString(StateValue.DST_VER, updatesInfo.dst_version);
        editor.putString(StateValue.DSTCRIPTION, updatesInfo.description);
        editor.putString(StateValue.DOWNLOADURL, updatesInfo.downloadUrl);
        editor.putInt(StateValue.SIZE, updatesInfo.fileSize);
        editor.putString(StateValue.PRIORITY, updatesInfo.priority);
        editor.putString(StateValue.SESSIONID, updatesInfo.sessionId);
        editor.commit();
	}
	//cg modify 2012-11-9 start 
	@Override
	protected void onResume()
	{
		super.onResume();
	}
	
		/*
	 * Wifi是否连接
	 */
	public boolean isConnectedToWifi(NetworkInfo mNetworkInfo) 
	{
		Log.d("OTAUpdatesActivity", "isConnectedToWifi = " + mNetworkInfo.getType());
		return (mNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI);
	}

	/*
	 * apn切换
	 */
	private int setApnCMNET()
	{
		int iret = 0;
		Cursor cursor = null;
		String name = "CMNET";
		ConnectivityManager conManager = 
				(ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);

		do
		{
			if(conManager == null)	
			{
				iret = -10;
				break;
			}

			NetworkInfo ni = conManager.getActiveNetworkInfo(); 
			if (ni == null)
			{
				iret = -20;
				break;
			}
			int carr_id = TelephonyManager.getDefaultDataPhoneId(this);
			if(carr_id == 0) {
				cursor = getContentResolver().query(Uri.parse("content://telephony/carriers"),
						new String[]{"_id"}, "name=? and current=?", new String[]{name,"1"}, null);
			} else {
				cursor = getContentResolver().query(Uri.parse("content://telephony_sim2/carriers"),
						new String[]{"_id"}, "name=? and current=?", new String[]{name,"1"}, null);
			}
			
			String apmpre_sim1 = "";
			String apnpre_sim2 = "";
			
			Cursor cursorsim1 = getContentResolver().query(Uri.parse("content://telephony/carriers/preferapn"), new String[] { "_id" },
	             null, null, android.provider.Telephony.Carriers.DEFAULT_SORT_ORDER);
			if(cursorsim1 != null) {
	            if (cursorsim1.getCount() > 0) {
	            	cursorsim1.moveToFirst();
	            	apmpre_sim1 = cursorsim1.getString(cursorsim1.getColumnIndex("_id"));
	            	Log.i("OTAUpdatesActivity", "apmpre_sim1="+apmpre_sim1);
	            }
	            cursorsim1.close();
	        }
			Cursor cursorsim2 = getContentResolver().query(Uri.parse("content://telephony_sim2/carriers/preferapn"), new String[] { "_id" },
	             null, null, android.provider.Telephony.Carriers.DEFAULT_SORT_ORDER);
			if(cursorsim2 != null) {
	            if (cursorsim2.getCount() > 0) {
	            	cursorsim2.moveToFirst();
	            	apnpre_sim2 = cursorsim2.getString(cursorsim2.getColumnIndex("_id"));
	            	Log.i("OTAUpdatesActivity", "apnpre_sim2="+apnpre_sim2);
	            }
	            cursorsim2.close();
	        }

			if (cursor == null)
			{
				iret = -50;
				break;
			}
			String id = "";
			
			if (cursor.getCount() > 0)
			{
				cursor.moveToFirst();			
				id = cursor.getString(cursor.getColumnIndex("_id"));		
				Log.e("OTAUpdatesActivity", "Settings	onOptionsItemSelected  id = "+id);
			}
			
			if (isConnectedToWifi(ni))
			{
				if (!ni.isConnected())
				{
					iret = -30;
					break;
				}
				if (cursor.getCount() > 0)
				{
					setSelectedApnKey(id);
					isSetCMNET = true ;
				
					editor.putString(StateValue.IS_CMWAP, "1");
					editor.commit();
				}
				startDownload();
				break;
			}

			if (!ni.isConnected())
			{
				iret = -35;
				break;
			}

			{
				String apn = ni.getExtraInfo();
				Log.e("OTAUpdatesActivity", "Settings   onOptionsItemSelected  apn = "+apn);
				if ((apn == null) || apn.equals(""))
				{
					iret = -40;
					break;
				}				
				
				if("cmnet".equals(apn))
				{
					startDownload();
//					setApnCmnet(this, apmpre_sim1, apnpre_sim2);
					//cg lock apn
//					ContentResolver resolver = getContentResolver();
//					ContentValues values = new ContentValues();
//					values.put("CGISLOCKAPN", "enable");
//					resolver.update(Uri.parse("content://telephony/carriers/preferapn"), values, null, null);
					//cg end
					break;
				}

				if (apn.equals("cmwap"))
				{
					setSelectedApnKey(id);
					isSetCMNET = true ;
					m_is_waiting_for_network = true;
					
					editor.putString(StateValue.IS_CMWAP, "1");
					editor.commit();
//					setApnCmnet(this, apmpre_sim1, apnpre_sim2);
					//cg lock apn
//					ContentResolver resolver = getContentResolver();
//					ContentValues values = new ContentValues();
//					values.put("CGISLOCKAPN", "enable");
//					resolver.update(Uri.parse("content://telephony/carriers/preferapn"), values, null, null);
					//cg end
					mHandler.removeCallbacks(resetApnRunnable);
		                        mHandler.postDelayed(resetApnRunnable, 20 * 1000);
					break;
				}
				startDownload();
				break;
			}
			
		}while(false);

		if (cursor != null)
		{
			cursor.close();
			cursor  = null;
		}
		
		Log.d("OTAUpdatesActivity", "setApnCMNET()= " + iret); 
		return iret;		
	}
	
	private void setApnCmnet(Context context, String sim1, String sim2) {
		Log.i("OTAUpdatesActivity", "setApnCmnet sim1="+sim1 + " sim2=" + sim2);
		String name = "CMNET";
		Cursor cursorsim1 = getContentResolver().query(Uri.parse("content://telephony/carriers"),
					new String[]{"_id"}, "name=? and current=?", new String[]{name,"1"}, null);
		if (cursorsim1.getCount() > 0)
		{
			cursorsim1.moveToFirst();			
			String preid1 = cursorsim1.getString(cursorsim1.getColumnIndex("_id"));
			Log.i("OTAUpdatesActivity", "preid1="+preid1);
			if(!sim1.equals(preid1)) {
				editor.putString("simoneneedreset", "1");
				editor.putString("simonepre", sim1);
				editor.commit();
				ContentResolver resolver = getContentResolver();
		        ContentValues values = new ContentValues();
		        values.put(APN_ID, preid1);
				resolver.update(Uri.parse("content://telephony/carriers/preferapn"), values, null, null);
			}
		}
		Cursor cursorsim2 = getContentResolver().query(Uri.parse("content://telephony_sim2/carriers"),
					new String[]{"_id"}, "name=? and current=?", new String[]{name,"1"}, null);
		if (cursorsim2.getCount() > 0)
		{
			cursorsim2.moveToFirst();			
			String preid2 = cursorsim2.getString(cursorsim2.getColumnIndex("_id"));
			Log.i("OTAUpdatesActivity", "preid2="+preid2);
			if(!sim2.equals(preid2)) {
				editor.putString("simtwoneedreset", "1");
				editor.putString("simtwopre", sim2);
				editor.commit();
				ContentResolver resolver = getContentResolver();
		        ContentValues values = new ContentValues();
		        values.put("apn_id_sim2", preid2);
				resolver.update(Uri.parse("content://telephony_sim2/carriers/preferapn"), values, null, null);
			}
		}
		
	}
   
	private void setSelectedApnKey(String key)
	 {
        mSelectedKey = key;
        ContentResolver resolver = getContentResolver();

        ContentValues values = new ContentValues();
//        Log.e("peter", "Settings   setSelectedApnKey  apn = "+apn);
        Log.d("OTAUpdatesActivity","setSelectedApnKey="+key+"  mSubId="+mSubId);
        int carr_id = TelephonyManager.getDefaultDataPhoneId(this);
        Log.i("pan", "carr_id="+carr_id);
		if(carr_id == 0) {
			values.put(APN_ID, mSelectedKey);
			resolver.update(Uri.parse("content://telephony/carriers/preferapn"), values, null, null);
		} else {
			values.put("apn_id_sim2", mSelectedKey);
			resolver.update(Uri.parse("content://telephony_sim2/carriers/preferapn"), values, null, null);
		}

    }

	Runnable resetApnRunnable = new Runnable() {
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
			OTAUpdatesActivity.this.finish();
//			resetApn();
			Toast.makeText(getWindow().getContext(), R.string.download_cancle, Toast.LENGTH_LONG).show();
		}
	};
	
	public class NetworkChangeReceiver extends BroadcastReceiver
	 { 
        public void onReceive(Context context, Intent intent)
		 { 
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) 
			{ 
            	
        		ConnectivityManager conManager = 
     				   (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo info = conManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE); 
                String apn = info.getExtraInfo(); 
                Log.e("OTAUpdatesActivity", "Settings  onReceive  apn = "+apn);
                Log.i("pan", "set apn:"+apn);
				if (info.isConnected() && ("cmnet".equals(apn))) 
				{ 
					
					Log.i("pan", "m_is_waiting_for_getdata"+m_is_waiting_for_getdata);
	            	if(m_is_waiting_for_getdata) {
	            		Intent updateIntent =new Intent(context, CheckNewVerService.class);
	            		updateIntent.putExtra(StateValue.ISAUTOCHECK, false); 
	            		startService(updateIntent);
	            		m_is_waiting_for_getdata = false;
	            		return;
	            	}
	            	
					mHandler.removeCallbacks(resetApnRunnable);
					//start download    调用下载的方法。
					Log.i("OTAUpdatesActivity", isSetCMNET+"isSetCMNET");
					if(isSetCMNET && m_is_waiting_for_network)
					{
						m_is_waiting_for_network = false;
						startDownload();
					}

					/* 
					* apn change message is sent out more than once during a second, but it 
					* only happens once practically. 
					*/ 
				}
            } 
        } 
    } 
    
	private void resetApn()
	{
//cg unlock apn
		ContentResolver resolver = getContentResolver();
		ContentValues values = new ContentValues();
		values.put("CGISLOCKAPN", "disable");
		resolver.update(Uri.parse("content://telephony/carriers/preferapn"), values, null, null);
//cg end
		String name = "CMWAP";
		Cursor cursor = getContentResolver().query(Uri.parse("content://telephony/carriers"),
				new String[]{"_id"}, "name=? and current=?", new String[]{name,"1"}, null);
		String id = "";
		if(cursor !=null && cursor.getCount() >0)
		{
            cursor.moveToFirst();
            id = cursor.getString(cursor.getColumnIndex("_id"));
            Log.e("OTAUpdatesActivity", "Settings onOptionsItemSelected  id = "+id);
        }
		Log.i("OTAUpdatesActivity", "is"+isSetCMNET);
		isSetCMNET = sharedPref.getString(StateValue.IS_CMWAP, "0").equals("1") ? true : false;
		
		editor.putString(StateValue.IS_CMWAP, "0");
		editor.commit();
			
		if(isSetCMNET)
		{
			setSelectedApnKey(id);
			isSetCMNET = false ;
		}
		
//		resetApnaftgetdata();
		/*editor.putString("simoneneedreset", "1");
		editor.putString("simonepre", sim1);*/
		boolean simoneneedreset = sharedPref.getString("simoneneedreset", "0").equals("1") ? true : false;
		Log.i("OTAUpdatesActivity", "simoneneedreset="+simoneneedreset);
		if(simoneneedreset) {
			Cursor simonecursor = getContentResolver().query(Uri.parse("content://telephony/carriers"),
				new String[]{"_id"}, "name=? and current=?", new String[]{name,"1"}, null);
			String simonepre = "";
			if(simonecursor !=null && simonecursor.getCount() >0)
			{
            			simonecursor.moveToFirst();
            			simonepre = simonecursor.getString(simonecursor.getColumnIndex("_id"));
        		}
			Log.i("OTAUpdatesActivity", "simonepre="+simonepre);
	        	ContentValues valuessimone = new ContentValues();
	        	valuessimone.put(APN_ID, simonepre);
			resolver.update(Uri.parse("content://telephony/carriers/preferapn"), valuessimone, null, null);
			editor.putString("simoneneedreset", "0");
			editor.putString("simonepre", "0");
			editor.commit();
		}
		
		boolean simtwoneedreset = sharedPref.getString("simtwoneedreset", "0").equals("1") ? true : false;
		Log.i("OTAUpdatesActivity", "simtwoneedreset="+simtwoneedreset);
		if(simtwoneedreset) {
			Cursor simtwocursor = getContentResolver().query(Uri.parse("content://telephony_sim2/carriers"),
				new String[]{"_id"}, "name=? and current=?", new String[]{name,"1"}, null);
			String simtwopre = "";
			if(simtwocursor !=null && simtwocursor.getCount() >0)
			{
            			simtwocursor.moveToFirst();
            			simtwopre = simtwocursor.getString(simtwocursor.getColumnIndex("_id"));
        		}
			Log.i("OTAUpdatesActivity", "simtwopre="+simtwopre);
			ContentValues valuessimtwo = new ContentValues();
			valuessimtwo.put("apn_id_sim2", simtwopre);
			resolver.update(Uri.parse("content://telephony_sim2/carriers/preferapn"), valuessimtwo, null, null);
			editor.putString("simtwoneedreset", "0");
			editor.putString("simtwopre", "0");
			editor.commit();
		}
	}
	//cg modify 2012-11-9 end 
	
	//cg modify 2012-11-19 start
	protected void onPause() {
		//setProgressDialog(false);
		Log.i("OTAUpdatesActivity", "onPause");
		super.onPause();
	}
  //cg modify for 2012-11-19 end
	
	private void setApnCmnetBefore(){
		int iret = 0;
		Cursor cursor = null;
		String name = "CMNET";
		ConnectivityManager conManager = 
				(ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
		do
		{
			if(conManager == null)	
			{
				break;
			}
			NetworkInfo ni = conManager.getActiveNetworkInfo(); 
			Log.i("pan", "ni="+ni);
			if (ni == null)
			{
				break;
			}
			
			int carr_id = TelephonyManager.getDefaultDataPhoneId(this);
			Log.i("pan", "carr_id="+carr_id);
			if(carr_id == 0) {
				cursor = getContentResolver().query(Uri.parse("content://telephony/carriers"),
						new String[]{"_id"}, "name=? and current=?", new String[]{name,"1"}, null);
			} else {
				cursor = getContentResolver().query(Uri.parse("content://telephony_sim2/carriers"),
						new String[]{"_id"}, "name=? and current=?", new String[]{name,"1"}, null);
			}
			if (cursor == null)
			{
				break;
			}
			String id = "";
			Log.i("pan", "cursor.getCount()="+cursor.getCount());
			if (cursor.getCount() > 0)
			{
				cursor.moveToFirst();			
				id = cursor.getString(cursor.getColumnIndex("_id"));		
				Log.e("OTAUpdatesActivity", "Settings	onOptionsItemSelected  id = "+id);
			}
			Log.i("pan", "isConnectedToWifi="+isConnectedToWifi(ni));
			if (isConnectedToWifi(ni))
			{
				if (!ni.isConnected())
				{
					break;
				}
				if (cursor.getCount() > 0)
				{
					Log.i("pan", "cursor.getCount()="+cursor.getCount());
				}else{
					break;
				}
				break;
			}

			if (!ni.isConnected())
			{
				break;
			}

			{
				String apn = ni.getExtraInfo();
				Log.e("OTAUpdatesActivity", "Settings   onOptionsItemSelected  apn = "+apn);
				Log.i("pan", "apn="+apn);
				if ((apn == null) || apn.equals(""))
				{
					break;
				}				
				
				if("cmnet".equals(apn))
				{
					break;
				}

				if (apn.equals("cmwap"))
				{
					setSelectedApnKey(id);
					editor.putString("needresetapn", "1");
					editor.commit();
					m_is_waiting_for_getdata = true;
					Log.e("OTAUpdatesActivity", "set apn for get data cmwap -> cmnet");
					Log.i("pan", "set apn for get data cmwap -> cmnet");
					break;
				}
				break;
			}
			
		}while(false);

		if (cursor != null)
		{
			cursor.close();
			cursor  = null;
		}
	}
	
	private void resetApnaftgetdata()
	{
		String name = "CMWAP";
		Uri carr_uri = null;
		int carr_id = TelephonyManager.getDefaultDataPhoneId(this);
		if(carr_id == 0) {
			carr_uri = Uri.parse("content://telephony/carriers");
		} else {
			carr_uri = Uri.parse("content://telephony_sim2/carriers");
		}
		Log.i("pan", "carr_id="+carr_id);
		Cursor cursor = getContentResolver().query(carr_uri,
				new String[]{"_id"}, "name=? and current=?", new String[]{name,"1"}, null);
		String id = "";
		if(cursor !=null && cursor.getCount() >0)
		{
            cursor.moveToFirst();
            id = cursor.getString(cursor.getColumnIndex("_id"));
            Log.e("OTAUpdatesActivity", "Settings onOptionsItemSelected  id = "+id);
        }
		boolean needreset = sharedPref.getString("needresetapn", "0").equals("1") ? true : false;
		Log.i("pan", "needreset"+needreset+"");
		editor.putString("needresetapn", "0");
		editor.commit();
			
		if(needreset)
		{
			Log.i("pan", "******************"+id);
			setSelectedApnKey(id);
		}
	}

}


