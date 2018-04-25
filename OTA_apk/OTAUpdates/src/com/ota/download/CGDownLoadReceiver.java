package com.ota.download;


import java.io.File;

import com.ota.OTAUpdatesActivity;
import com.ota.R;
import com.ota.StateValue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

public class CGDownLoadReceiver extends BroadcastReceiver {
	
	private final static boolean DBG = true;
	private final static String TAG = "CGDownLoadReceiver";
	
	private final static String SHARE_FILE = "downloadinfo";
	
	private static String getFileName(String path,String name) {
		String suffix = "";
		String filemane = "";
		if(!path.endsWith("/")) {
			path += "/";
		}
		if(name.lastIndexOf(".") != -1) {
			suffix = name.substring(name.lastIndexOf("."), name.length());
			filemane = name.substring(0, name.lastIndexOf("."));
		} else {
			filemane = name;
		}
		if (DBG)
			Log.e(TAG, "filemane = " + filemane + " suffix = " + suffix + " path = " + path);
		File scfile = new File(path + name);
		if(!scfile.exists()) {
			return name;
		}
		for(int i = 1; i < 10; i++) {
			String newfilename = filemane + "-" + i + suffix;
			File file = new File(path + newfilename);
			if(!file.exists()) {
				return newfilename;
			}
		}
		return filemane + "-" + System.currentTimeMillis() + suffix;
	}
	public static void startDownLoad(Context context,String fileurl,String filepath,String filename,int filesize,String priority,boolean wifionly,boolean showprogress,boolean isautocheck) {
				
		//if the giving url euals the url-downloading the just change the visible of progress start
		SharedPreferences mSharedPreferences = context.getSharedPreferences(SHARE_FILE, Context.MODE_MULTI_PROCESS);
		String downloadingfileurl = mSharedPreferences.getString("fileurl","");
		String downloadingfilepath = mSharedPreferences.getString("filepath","");
		
		boolean downloadingwifionly = mSharedPreferences.getBoolean("wifionly", false);
		boolean downloadingshowprogress = mSharedPreferences.getBoolean("showprogress", false);
		if (filepath.equals(downloadingfilepath)&& fileurl.equals(downloadingfileurl)) {
			if(showprogress && !downloadingshowprogress) {
				SharedPreferences.Editor mEditor = mSharedPreferences.edit();
				mEditor.putBoolean("showprogress", showprogress);
				mEditor.commit();
				DownLoadService.showProgress(context, showprogress);
			}
			Log.e(TAG, "wifionly********:"+wifionly);  
		    Log.e(TAG, "downloadingwifionly*******:"+downloadingwifionly);
			if (downloadingwifionly != wifionly) {
				SharedPreferences.Editor mEditor = mSharedPreferences.edit();
				mEditor.putBoolean("wifionly", wifionly);
				mEditor.commit();
			}			
			if(wifionly) {
				if(!CGNetWorkUtils.isNetWorkWifi(context)) {
					DownLoadService.cancelDownLoad(context);
					return;
				}
			}
			if (DBG)
				Log.w(TAG,"download info is not change neednot start new download");
				DownLoadService.startDownLoadFile(context,fileurl,filepath,filename,filesize,showprogress,isautocheck);
			return;
		}
		//if the giving url euals the url-downloading the just change the visible of progress end
	    Log.e(TAG, "wifionly:"+wifionly);   
	    Log.e(TAG, "downloadingwifionly:"+downloadingwifionly);
		Intent intent = new Intent("cg.ota.startdownload");
		intent.putExtra("fileurl", fileurl);
		intent.putExtra("filepath", filepath);
		intent.putExtra("filename", getFileName(filepath, filename));
		intent.putExtra("filesize", filesize);
		intent.putExtra("wifionly", wifionly);
		intent.putExtra("showprogress", showprogress);
		intent.putExtra("priority", priority);
		intent.putExtra("isautocheck", isautocheck);
		
		context.sendBroadcast(intent);
	}
	
	public static void cancelDownLoad(Context context) {
		Intent intent = new Intent("cg.ota.canceldownload");
		context.sendBroadcast(intent);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stub		
		if(intent == null) {
			return;
		}
				
		String action = intent.getAction();
		showMsg("action = " + action);
		if("android.intent.action.BOOT_COMPLETED".equals(action)) {
			onBootCompleted(context,intent);
		} else if("android.net.conn.CONNECTIVITY_CHANGE".equals(action)) {
			onConnectivityChanged(context,intent);
		} else if("cg.ota.startdownload".equals(action)) {
			startdownload(context,intent);
		} else if("cg.ota.canceldownload".equals(action)) {
			canceldownload(context,intent);
		} else if("android.intent.action.MEDIA_UNMOUNTED".equals(action)){
			/*showMsg("unmounted");
			SharedPreferences sharedPref = context.getSharedPreferences("is_download", Context.MODE_PRIVATE);
			SharedPreferences.Editor editor=sharedPref.edit();
			editor.putBoolean("isdownloading", false);
			editor.commit();
			canceldownload(context,intent);
			DownLoadService.showProgress(context, false);*/
			canceldownload(context,intent);
			DownLoadService.showProgress(context, false);
		} else if("android.intent.action.MEDIA_MOUNTED".equals(action)) {
			checkAndStartRealDownLoad(context,intent);
		} else if("cg.downloadfile.success".equals(action)) {			
			String fileName = intent.getStringExtra("name");
			String filePath = intent.getStringExtra("filepath");
			boolean isautocheck = intent.getBooleanExtra("isautocheck", false);
			String dirPath = filePath+"/"+fileName;
			showMsg("dirpath:"+dirPath);
						
			Intent startIntent = new Intent(context, OTAUpdatesActivity.class);			
			startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startIntent.putExtra(StateValue.STARTSTATE, StateValue.DOWNLOAD_OK);
			startIntent.putExtra(StateValue.FILEPATH, dirPath);
			startIntent.putExtra("isautocheck", isautocheck);	
			context.startActivity(startIntent);
			
			clearDownLoadInfo(context);
		} else if("cg.downloadfile.failed".equals(action)) {
			clearDownLoadInfo(context);
		}
	}
	
		private void clearDownLoadInfo(Context context) {
		SharedPreferences mSharedPreferences = context.getSharedPreferences(SHARE_FILE, Context.MODE_MULTI_PROCESS);
		SharedPreferences.Editor mEditor = mSharedPreferences.edit();
		mEditor.clear();
		mEditor.commit();
	}
	

	private void onBootCompleted(Context context, Intent intent) {
		checkAndStartRealDownLoad(context,intent);
	}
	
	private void onConnectivityChanged(Context context, Intent intent) {
		checkAndStartRealDownLoad(context,intent);
	}
	
	private void startdownload(Context context, Intent intent) {
		String fileurl = intent.getStringExtra("fileurl");
		String filepath = intent.getStringExtra("filepath");
		String filename = intent.getStringExtra("filename");
		int filesize = intent.getIntExtra("filesize",0);
		boolean wifionly = intent.getBooleanExtra("wifionly", false);
		boolean showprogress = intent.getBooleanExtra("showprogress", false);
		String priority = intent.getStringExtra("priority");
		boolean isautocheck = intent.getBooleanExtra("isautocheck", false);
		
		SharedPreferences mSharedPreferences = context.getSharedPreferences(SHARE_FILE, Context.MODE_MULTI_PROCESS);
		SharedPreferences.Editor mEditor = mSharedPreferences.edit();
		mEditor.putString("fileurl", fileurl);
		mEditor.putString("filepath", filepath);
		mEditor.putString("filename", filename);
		mEditor.putInt("filesize", filesize);
		mEditor.putBoolean("wifionly", wifionly);
		mEditor.putBoolean("showprogress", showprogress);
		mEditor.putString("priority", priority);
		mEditor.putBoolean("isautocheck", isautocheck);
		mEditor.commit();
		checkAndStartRealDownLoad(context,intent);
	}
	
	private void checkAndStartRealDownLoad(Context context,Intent intent) {
		SharedPreferences mSharedPreferences = context.getSharedPreferences(SHARE_FILE, Context.MODE_MULTI_PROCESS);
		String fileurl = mSharedPreferences.getString("fileurl","");
		String filepath = mSharedPreferences.getString("filepath","");
		String filename = mSharedPreferences.getString("filename","");
		int filesize = mSharedPreferences.getInt("filesize", 0);
		boolean wifionly = mSharedPreferences.getBoolean("wifionly", false);
		boolean showprogress = mSharedPreferences.getBoolean("showprogress", false);
		String priority = mSharedPreferences.getString("priority", "");
		boolean isautocheck = mSharedPreferences.getBoolean("isautocheck", false);
		
		if("".equals(filename) || "".equals(filepath) || "".equals(fileurl)){
			return;
		}
		
		if(!CGNetWorkUtils.isNetworkAvailable(context)) {
			DownLoadService.cancelDownLoad(context);
			return;
		}
		
		if(wifionly) {
			if(!CGNetWorkUtils.isNetWorkWifi(context)) {
				DownLoadService.cancelDownLoad(context);
				return;
			}
		}
		
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_UNMOUNTED.equals(state)) {
			showMsg("unmount");
			/*SharedPreferences sharedPref = context.getSharedPreferences("is_download", Context.MODE_PRIVATE);
			SharedPreferences.Editor editor=sharedPref.edit();
			editor.putBoolean("isdownloading", false);
			editor.commit();*/
			canceldownload(context,intent);
			DownLoadService.showProgress(context, false);
		}
		
		DownLoadService.startDownLoadFile(context,fileurl,filepath,filename,filesize,showprogress,isautocheck);
	}
	
	private void canceldownload(Context context, Intent intent) {
		DownLoadService.cancelDownLoad(context);
	}
    
	private void showMsg(final String msg) {
		if(DBG) Log.w(TAG, msg);
	}
}
