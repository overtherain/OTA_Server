package com.ota.download;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.http.AndroidHttpClient;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.StatFs;
import android.util.Log;
import android.widget.Toast;

public class DownLoadService extends Service {

	private final static String TAG = "DownLoadService";
	private final static boolean DBG = true;
	
	private final static long SIZEAVAILABLE = 1024L * 1024L;

	private final static String FILE_PATH = "filepath";
	private final static String FILE_NAME = "name";
	private final static String FILE_URL = "url";
	private final static String COMMOND = "commond";
	private final static String FILE_SIZE = "filesize";
	private final static String FILE_PROGRESS = "showprogress";
	private final static String FILE_ISAUTOCHECK = "isautocheck";

	private final static int COMMOND_DOWNLOAD = 0;
	private final static int COMMOND_CANCEL = 1;
	private final static int COMMOND_SHOWPROGRESS = 2;

	public static final int SHOWPROGRESS = 2;
	public static final int CANCELNOTIFICATION = 3;
	
	private SharedPreferences sharedPref;
	private SharedPreferences.Editor editor;
     	public static enum DownLoadError {
		DOWNLOADERROR_NOSPACEAVAILABLE,
		DOWNLOADERROR_RESPANCEERROR 
	}

	int curprogress = -1;
	int notifyID = 1;

	long starttime = 0;
	
	boolean showprogress = false;

	private DownLoadThread mDownLoadThread = null;

	Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			// TODO Auto-generated method stub
			int what = msg.what;
			switch (what) {
			case SHOWPROGRESS:
				try {
					Bundle data = msg.getData();
					if (data != null) {
						String tittle = data.getString("tittle");
						notifyProgressChange(msg.arg1, msg.arg2, tittle);
					}
				} catch (Exception e) {
					// TODO: handle exception
					if(e != null) {
						showMsg("show progress error" + e);
					}
				}
				break;
			case CANCELNOTIFICATION:
				try {
					NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
					mNotificationManager.cancel(msg.arg2);
				} catch (Exception e) {
					if(e != null) {
						showMsg("cancel notification error " + e);
					}
				}
			default:
				break;
			}
		}
	};

	private void notifyProgressChange(int progress, int notifyID, String tittle) {

		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		Notification.Builder builder = new Notification.Builder(this);
		builder.setSmallIcon(android.R.drawable.stat_sys_download);
		builder.setWhen(0);
		builder.setOngoing(true);
		builder.setContentText(tittle);
		if (progress > 0) {
			builder.setProgress(100, progress, false);
			builder.setContentInfo("" + progress + "%");
		} else {
			builder.setProgress(100, 0, false);
			builder.setContentInfo("" + progress + "%");
		}
		mNotificationManager.notify(notifyID, builder.getNotification());
		showMsg("nofify progress changed progress= " + progress + " tittle=" + tittle);
	}

	private void showPregress(int progress, String name) {
		showMsg("showprogress=" + showprogress + " curprogress=" + curprogress + " progress=" + progress);
		if(!showprogress) {
			return;
		}
		
		// progress = progress / 2 * 2;
		if (progress > curprogress) {
			curprogress = progress;
		} else {
			return;
		}
		Message msg = mHandler.obtainMessage();
		msg.what = DownLoadService.SHOWPROGRESS;
		msg.arg1 = progress;
		msg.arg2 = notifyID;
		Bundle bundle = new Bundle();
		bundle.putString("tittle", name);
		msg.setData(bundle);
		msg.sendToTarget();
	}

	private void cancelNotification() {
		Message msg = mHandler.obtainMessage();
		msg.what = DownLoadService.CANCELNOTIFICATION;
		msg.arg2 = notifyID;
		msg.sendToTarget();
		curprogress = -1;
	}
	
	public void showProgress(boolean show) {
		curprogress = -1;
		showprogress = show;
		if(!showprogress) {
			cancelNotification();
		}
	}

	public static void startDownLoadFile(Context context, String url,
			String dirpath, String filename, int filesize, boolean showprogress,boolean isautocheck) {
		Intent intent = new Intent(context, DownLoadService.class);
		intent.putExtra(COMMOND, COMMOND_DOWNLOAD);
		intent.putExtra(FILE_URL, url);
		intent.putExtra(FILE_NAME, filename);
		intent.putExtra(FILE_PATH, dirpath);
		intent.putExtra(FILE_SIZE, filesize);
		intent.putExtra(FILE_PROGRESS, showprogress);
		intent.putExtra(FILE_ISAUTOCHECK, isautocheck);
		
		context.startService(intent);
	}

	public static void cancelDownLoad(Context context) {
		Intent intent = new Intent(context, DownLoadService.class);
		intent.putExtra(COMMOND, COMMOND_CANCEL);
		context.startService(intent);
	}
	
	public static void showProgress(Context context,boolean showprogress) {
		Intent intent = new Intent(context, DownLoadService.class);
		intent.putExtra(COMMOND, COMMOND_SHOWPROGRESS);
		intent.putExtra(FILE_PROGRESS, showprogress);
		context.startService(intent);
	}
    
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// TODO Auto-generated method stub
		if (intent == null) {
			return super.onStartCommand(intent, flags, startId);
		}

		int commond = intent.getIntExtra(COMMOND, -1);
		switch (commond) {
		case COMMOND_DOWNLOAD:
             			
			long curtime = System.currentTimeMillis();
			showMsg("curtime=" + curtime);
			long tmp = curtime - starttime;
			starttime = curtime;
			if (tmp > 0 && tmp < 1 * 1000) {
				Log.e("cg", "start time dis little abort");
				return super.onStartCommand(intent, flags, startId);
			}
			
			showMsg("download commond");
			String url = intent.getStringExtra(FILE_URL);
			String filename = intent.getStringExtra(FILE_NAME);
			String dirpath = intent.getStringExtra(FILE_PATH);
			int filesize = intent.getIntExtra(FILE_SIZE, 0);
			showprogress = intent.getBooleanExtra(FILE_PROGRESS, false);
			boolean isautocheck = intent.getBooleanExtra(FILE_ISAUTOCHECK, false);
			if(!showprogress) {
				cancelNotification();
			}
			if (mDownLoadThread != null) {
				mDownLoadThread.cancelDownLoad();
			}
			mDownLoadThread = new DownLoadThread(getApplicationContext(), url,
					dirpath, filename, filesize ,isautocheck );
			mDownLoadThread.start();
						
			break;
		case COMMOND_CANCEL:
			showMsg("cancel commond");
			if (mDownLoadThread != null) {
				mDownLoadThread.cancelDownLoad();
			}
//			showMsg("false");
//			SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("is_download", Context.MODE_PRIVATE);
//			SharedPreferences.Editor editor=sharedPref.edit();
//			editor.putBoolean("isdownloading", false);
//			editor.commit();
			break;
		case COMMOND_SHOWPROGRESS:
			showprogress = intent.getBooleanExtra(FILE_PROGRESS, false);
			showProgress(showprogress);
			break;
		default:
			break;
		}
         
		return super.onStartCommand(intent, flags, startId);
	}

	class DownLoadThread extends Thread {

		Context context;
		String apkurl;
		String filename;
		String dirpath;
		int filesize;
		boolean isautocheck;

		long curfilesize = 0;
		long exparelength = -1;
		
		static final long WAPDIS = 1024L * 100L;

		int relocation = 0;

		private AndroidHttpClient httpClient = null;

		private boolean isCancelled = false;

		class RetryException extends Exception {
			private static final long serialVersionUID = 1L;
		}
		
		class WAPRetryException extends Exception {
			private static final long serialVersionUID = 1L;
		}
		
		class StopDownLoadException extends Exception {
			private static final long serialVersionUID = 1L;
		}

		public DownLoadThread(Context context, String apkurl, String dirpath,
				String filename, int filesize , boolean isautocheck) {
			this.context = context;
			this.apkurl = apkurl;
			this.filename = filename;
			this.dirpath = dirpath;
			this.filesize = filesize;
			this.isautocheck = isautocheck;
		}

		public void cancelDownLoad() {
			showMsg("cancel download");
			isCancelled = true;
			if (httpClient != null) {
				httpClient.close();
				httpClient = null;
			}
		}

		boolean isfinished = false;

		@Override
		public void run() {
			// TODO Auto-generated method stub
			
			showMsg("true"); 
			sharedPref = context.getSharedPreferences("is_download", Context.MODE_PRIVATE);
			editor=sharedPref.edit();
			editor.putBoolean("isdownloading", true);
			editor.commit();
			
			isCancelled = false;
			relocation = 0;
			isfinished = false;
			curfilesize = 0;
			exparelength = -1;
			curprogress = -1;
			try {
				for (int i = 0; i < 10 && !isfinished && !isCancelled; i++) {
					
					if(CheckRequestState.isWap(context)) {
						showMsg("wap start download try : " + i);
						try {
							int total = (int) (filesize / WAPDIS) + 2;
							for(i = 0; i < total && !isCancelled; i++) {
								showMsg("wapdownload down dis : " + i);
								try {
									downloadfile(context);
								} catch (WAPRetryException e) {
									// TODO: handle exception
								}
								if(isfinished) {
									break;
								}
							}
							showMsg("isCancelled" + isCancelled + "filesize" + filesize);
						} catch (RetryException e) {
							// TODO: handle exception
						}
					} else {
						showMsg("wifi net start download try : " + i);
						try {
							downloadfile(context);
							showMsg("isCancelled" + isCancelled + "filesize" + filesize);
						} catch (RetryException e) {
							// TODO: handle exception
						}
					}
					if(isfinished && !isCancelled) {
						//download finished
						showMsg("send broadcast success");
						Intent result = new Intent("cg.downloadfile.success");
						result.putExtra(FILE_NAME, filename);
						result.putExtra(FILE_PATH, dirpath);
						result.putExtra(FILE_ISAUTOCHECK, isautocheck);
						context.sendBroadcast(result);
						
						showMsg("false");
						sharedPref = context.getSharedPreferences("is_download", Context.MODE_PRIVATE);
						editor=sharedPref.edit();
						editor.putBoolean("isdownloading", false);
						editor.commit();
						
						showMsg("----------------download finished----------------");
						cancelNotification();
						break;
					}
				}
			} catch (Exception e) {
				// TODO: handle exception
				Log.d("cg", "" + e);
			}finally{
				
//				showMsg("false"); 
//				sharedPref = context.getSharedPreferences("is_download", Context.MODE_PRIVATE);
//				editor=sharedPref.edit();
//				editor.putBoolean("isdownloading", false);
//				editor.commit();
				
				cancelNotification();
			}
		}

		private void stopDownloadWithException() throws StopDownLoadException {
			showMsg("stopDownloadWithException");
			throw new StopDownLoadException();
		}

		private void reTryDownLoadWithException() throws RetryException {
			showMsg("reTryDownLoadWithException");
			throw new RetryException();
		}
		
		private void ContinueDownLoadWithException() throws WAPRetryException {
			showMsg("ContinueDownLoadWithException");
			throw new WAPRetryException();
		}

		private void downloadfile(Context context) throws Exception {
			showMsg("prepare download " + dirpath + " filename " + filename);
			long startPosition = -1;
			File dir = new File(dirpath);
			if (!dir.exists()) {
				dir.mkdirs();
			}
			File ap = new File(dir, filename);

			if (ap.exists()) {
				startPosition = ap.length();
			} else {
				ap.createNewFile();
				startPosition = 0;
			}
			if(startPosition == filesize && startPosition != 0) {
				isfinished = true;
				showMsg("startPosition == filesize download finished filesize=" + filesize);
				reTryDownLoadWithException();
			}
			
			long sizeAvailable = getAvailableBytesInFileSystemAtGivenRoot(dir);
			if((sizeAvailable - SIZEAVAILABLE) < (filesize - startPosition)) {
				breadcastDownloadError(DownLoadError.DOWNLOADERROR_NOSPACEAVAILABLE, 0);
				stopDownloadWithException();
			}
			
			showMsg("HttpGet --> " + apkurl);
			HttpGet getRequest = new HttpGet(apkurl);
			getRequest.addHeader("Cache-control", "no-cache");
			if (httpClient != null) {
				httpClient.close();
				httpClient = null;
			}
			httpClient = getHttpClient(context);
			if(CheckRequestState.isWap(context)) {
				if (startPosition >= 0) {
					exparelength = startPosition + WAPDIS - 1;
					if(exparelength > (filesize - 1)) {
						exparelength = filesize - 1;
					}
					getRequest.addHeader("Range", "bytes=" + startPosition + "-" + exparelength);
					showMsg("request info bytes=" + startPosition + "-" + exparelength);
				} else {
					exparelength = 0 + WAPDIS - 1;
					if(exparelength > (filesize - 1)) {
						exparelength = filesize - 1;
					}
					getRequest.addHeader("Range", "bytes=" + 0 + "-" + exparelength);
					showMsg("request info bytes=" + 0 + "-" + exparelength);
				}
			} else {
				if (startPosition >= 0) {
					getRequest.addHeader("Range", "bytes=" + startPosition + "-");
					showMsg("request info bytes=" + startPosition + "-file end");
				}
			}

			try {
				HttpResponse response = httpClient.execute(getRequest);
				int code = response.getStatusLine().getStatusCode();
				showMsg("response 1 code = " + code);

				if (startPosition >= 0) {
					if (code == HttpURLConnection.HTTP_OK) {
						showMsg("file already exits return code is wrong retry startPosition=" + startPosition + " code=" + code);
						reTryDownLoadWithException();
					}						
				}
				if (code == 301 || code == 302 || code == 303 || code == 307) {
					if (relocation >= 5) {
						showMsg("relocation > 5 stop download relocation=" + relocation);
						stopDownloadWithException();
					}
					relocation++;
					Header header = response.getFirstHeader("Location");
					if (header == null) {
						showMsg("header == null stop download");
						stopDownloadWithException();
					}
					apkurl = new URI(apkurl).resolve(new URI(header.getValue())).toString();
					showMsg("retry download relocation apkurl = " + apkurl);
					reTryDownLoadWithException();
				}

				if (code != HttpURLConnection.HTTP_OK
						&& code != HttpURLConnection.HTTP_PARTIAL) {
					showMsg("code is not prepared stop download code=" + code);
					breadcastDownloadError(DownLoadError.DOWNLOADERROR_RESPANCEERROR, code);
					stopDownloadWithException();
				}

				curfilesize = startPosition;
				excdownloadFile(ap, response);

			} finally {
				if(getRequest != null) {
					getRequest.abort();
					getRequest = null;
				}
				if (httpClient != null) {
					httpClient.close();
					httpClient = null;
				}
			}
		}
		
		private void breadcastDownloadError(DownLoadError error, int code) {
			Intent intent = new Intent("cg.downloadfile.failed");
			intent.putExtra("error", error);
			intent.putExtra("code", code);
			context.sendBroadcast(intent);
		}

		private void readHeader(HttpResponse response) {
			String headerTransferEncoding = null;
			Header header = response.getFirstHeader("Transfer-Encoding");
			if (header != null) {
				headerTransferEncoding = header.getValue();
			}
			if (headerTransferEncoding == null) {
				header = response.getFirstHeader("Content-Length");
				if (header != null) {
					String mHeaderContentLength = header.getValue();
					contentlength = Long.parseLong(mHeaderContentLength);
					showMsg("mHeaderContentLength" + mHeaderContentLength);
				}
			} else {
			}
		}

		long contentlength = 0;

		private void excdownloadFile(File apkFile, HttpResponse response)
				throws Exception {
			contentlength = 0;
			readHeader(response);
			showMsg("contentlength" + contentlength);
			if(CheckRequestState.isWap(context)) {
				if (contentlength != WAPDIS) {
					if (contentlength + curfilesize != filesize) {
						showMsg("file length is error retry download");
						showMsg("contentlength=" + contentlength + " curfilesize=" + curfilesize  + " filesize=" + filesize);
						apkFile.delete();
						reTryDownLoadWithException();
					}
				}
			} else {
				if (contentlength != 0) {
					if (contentlength + curfilesize != filesize) {
						showMsg("file length is error retry download");
						apkFile.delete();
						reTryDownLoadWithException();
					}
				}
			}

			InputStream inputStream = null;
			OutputStream outputStream = null;
			try {
				FileOutputStream fileOutputStream = null;
				fileOutputStream = new FileOutputStream(apkFile, true);
				int read;
				int readSize = 10 * 1024;
				byte[] buffer = new byte[readSize];
				if (response.getEntity() == null) {
					showMsg("response.getEntity() == null");
					stopDownloadWithException();
				}
				showMsg("read-write start");
				inputStream = response.getEntity().getContent();
				outputStream = new BufferedOutputStream(fileOutputStream);
				while ((read = inputStream.read(buffer)) != -1) {
					if (isCancelled) {
						showMsg("isCancelled=" + isCancelled);
						stopDownloadWithException();
					}
					
					long sizeAvailable = getAvailableBytesInFileSystemAtGivenRoot(apkFile.getParentFile());
					if((sizeAvailable - SIZEAVAILABLE) < (filesize - curfilesize)) {
						breadcastDownloadError(DownLoadError.DOWNLOADERROR_NOSPACEAVAILABLE, 0);
						stopDownloadWithException();
					}
					
					outputStream.write(buffer, 0, read);
					outputStream.flush();
					curfilesize += read;
					showPregress((int) (curfilesize / (filesize / 100)), filename);  
				}
				showMsg("read-write end");
				if(CheckRequestState.isWap(context)) {
					showMsg("contentlength=" + contentlength + " curfilesize=" + curfilesize + " filesize=" + filesize);
					if(curfilesize != filesize) {
						showMsg("***************************");
						ContinueDownLoadWithException();
					}
				}
				if(!isCancelled && (curfilesize >= filesize - 1) && filesize > 0 && curfilesize > 0) {
					isfinished = true;
					showMsg("file downloaded");
				}
			} finally {
				if (outputStream != null) {
					outputStream.flush();
				}

				if (inputStream != null) {
					inputStream.close();
				}
				if (outputStream != null) {
					outputStream.close();
				}
				if (httpClient != null) {
					httpClient.close();
					httpClient = null;
				}
			}
		}
		
		private long getAvailableBytesInFileSystemAtGivenRoot(File root) {
	        StatFs stat = new StatFs(root.getPath());
	        long availableBlocks = (long) stat.getAvailableBlocks() - 4;
	        long size = stat.getBlockSize() * availableBlocks;
	        if (DBG) {
	            Log.i(TAG, "available space (in bytes) in filesystem rooted at: " +
	                    root.getPath() + " is: " + size);
	        }
	        return size;
	    }

		private AndroidHttpClient getHttpClient(Context context) {
			AndroidHttpClient httpClient = AndroidHttpClient.newInstance(
					"Android client", context);
			HttpParams params = httpClient.getParams();
			HttpConnectionParams.setStaleCheckingEnabled(params, false);
			HttpConnectionParams.setSocketBufferSize(params, 8192);
			HttpClientParams.setRedirecting(params, false);
			HttpHost proxy = CheckRequestState.checkHttpHost(context);
			if (proxy != null) {
				params.setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
			}
			return httpClient;
		}

	}

	private void showMsg(String msg) {
		if (DBG)Log.e(TAG, msg);
	}

}