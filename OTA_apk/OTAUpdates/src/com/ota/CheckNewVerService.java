package com.ota;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;
import android.telephony.TelephonyManager;
// cg add
import android.os.SystemProperties;

public class CheckNewVerService extends Service {
    private boolean isAutoCheck;
    private boolean running;
    // private Message message;
    private UpdatesInfo updatesInfo;
    private final static String SHARE_FILE = "downloadinfo";
    private int battLevel;
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isAutoCheck = intent.getBooleanExtra(StateValue.ISAUTOCHECK, false);
        running = false;
        updatesInfo = new UpdatesInfo();
        
        if (isNetConnect()) {
            // 开启一个新的线程查询
            new Thread(new updateRunnable()).start();
            running = true;
        } else {
            Message message = new Message();
            message.what = StateValue.CHECK_NO_NET;
            updateHandler.sendMessage(message);
        }
        
        return super.onStartCommand(intent, flags, startId);
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onCreate() {
        registerReceiver(mBatteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        super.onCreate();
    }
    
    class updateRunnable implements Runnable {
        public void run() {
            Message message = new Message();
            String httpUrl = packageCheckUrl();
            
            HttpGet httpGet = new HttpGet(httpUrl);
            HttpParams httpParams = new BasicHttpParams();
            HttpConnectionParams.setConnectionTimeout(httpParams, 30000);
            HttpConnectionParams.setSoTimeout(httpParams, 30000);
            HttpClient httpCheckClient = new DefaultHttpClient(httpParams);
            
            String httpData = "";
            HttpResponse httpResponse = null;
            for (int i = 0; i <= 3; i++) {
                try {
                    httpResponse = httpCheckClient.execute(httpGet);
                    if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                        httpData = EntityUtils.toString(httpResponse.getEntity());
                        if (running) {
                            parseCheckResData(httpData);
                            if (updatesInfo.result == 0) {
                                message.what = StateValue.CHECK_NEED_UP;
                            } else if (updatesInfo.result == 1) {
                                message.what = StateValue.CHECK_NO_NEED_UP;
                            } else {
                                message.what = StateValue.CHECK_ERR_DATA;
                            }
                            message.obj = updatesInfo;
                            // resetApnaftgetdata(getApplicationContext());
                        }
                        
                    } else {
                        message.what = StateValue.CHECK_ERR_DATA;
                        Log.d("OTAUpdatesActivity", "HttpStatus = " + httpResponse.getStatusLine().getStatusCode());
                        // resetApnaftgetdata(getApplicationContext());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    message.what = StateValue.CHECK_SP_ERR;
                    // resetApnaftgetdata(getApplicationContext());
                }
                
                if (message.what == StateValue.CHECK_NEED_UP || message.what == StateValue.CHECK_NO_NEED_UP) {
                    break;
                }
                Log.d("OTAUpdatesActivity", "i" + i);
            }
            httpCheckClient.getConnectionManager().shutdown();
            if (running) {
                updateHandler.sendMessage(message);
            }
        }
    }
    
    /*
     * 消息处理
     */
    private Handler updateHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            
            if (isAutoCheck) {
                // check battery and t card start
                long free = getSDCardFreeMemory();
                Log.d("OTAUpdatesActivity", "battLevel:" + battLevel);
                int ishouldbeclose = 0;
                do {
                    if (free < 0) {
                        Toast.makeText(getApplicationContext(), R.string.no_card, Toast.LENGTH_LONG).show();
                        ishouldbeclose = 1;
                        break;
                    } else if (free < updatesInfo.fileSize) {
                        Toast.makeText(getApplicationContext(), R.string.no_room, Toast.LENGTH_LONG).show();
                        ishouldbeclose = 1;
                        break;
                    }
                    if (battLevel < 20) {
                        Toast.makeText(getApplicationContext(), R.string.low_battery, Toast.LENGTH_LONG).show();
                        ishouldbeclose = 1;
                        break;
                    }
                } while (false);
                if (ishouldbeclose == 1) {
                    msg.what = StateValue.CHECK_Mandatory;
                    return;
                }
                // check battery and t card end
                
                if (msg.what == StateValue.CHECK_NEED_UP) {
                    SharedPreferences is_sharedPref = getApplicationContext().getSharedPreferences("is_download", Context.MODE_PRIVATE);
                    boolean isdownloading = is_sharedPref.getBoolean("isdownloading", false);
                    if (isdownloading) {
                        stopService();
                        Log.d("OTAUpdatesActivity", "isdownloading return");
                        return;
                    }
                    
                    Intent intent = new Intent(CheckNewVerService.this, OTAUpdatesActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra(StateValue.STARTSTATE, StateValue.DOWNLOAD_START);
                    intent.putExtra(StateValue.RESULT, updatesInfo.result);
                    intent.putExtra(StateValue.SRC_VER, updatesInfo.src_version);
                    intent.putExtra(StateValue.DST_VER, updatesInfo.dst_version);
                    intent.putExtra(StateValue.DSTCRIPTION, updatesInfo.description);
                    intent.putExtra(StateValue.DOWNLOADURL, updatesInfo.downloadUrl);
                    intent.putExtra(StateValue.MD5, updatesInfo.md5);
                    intent.putExtra(StateValue.PRIORITY, updatesInfo.priority);
                    intent.putExtra(StateValue.SESSIONID, updatesInfo.sessionId);
                    startActivity(intent);
                } else {
                    Log.d("OTAUpdatesActivity", "isAutoCheck msg = " + msg.what);
                    // resetApnaftgetdata(getApplicationContext());
                }
            } else {
                Intent broadIntent = new Intent(StateValue.CHECKBROADACTION);
                broadIntent.putExtra("msg", msg.what);
                if (msg.what == StateValue.CHECK_NEED_UP) {
                    broadIntent.putExtra(StateValue.RESULT, updatesInfo.result);
                    broadIntent.putExtra(StateValue.SRC_VER, updatesInfo.src_version);
                    broadIntent.putExtra(StateValue.DST_VER, updatesInfo.dst_version);
                    broadIntent.putExtra(StateValue.DSTCRIPTION, updatesInfo.description);
                    broadIntent.putExtra(StateValue.DOWNLOADURL, updatesInfo.downloadUrl);
                    broadIntent.putExtra(StateValue.MD5, updatesInfo.md5);
                    broadIntent.putExtra(StateValue.PRIORITY, updatesInfo.priority);
                    broadIntent.putExtra(StateValue.SESSIONID, updatesInfo.sessionId);
                }
                sendBroadcast(broadIntent);
            }
            stopService();
        }
    };
    
    /*
     * 网络是否可用
     */
    private boolean isNetConnect() {
        ConnectivityManager connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (connectivity != null) {
            NetworkInfo info = connectivity.getActiveNetworkInfo();
            if (info != null && info.isConnected() && (info.getState() == NetworkInfo.State.CONNECTED)) {
                return true;
            }
        }
        
        return false;
    }
    
    /*
     * 请求url地址
     * http://www.51coolpad.com:80/updsvr/ota/checkupdate?hw=xxx&hwv=xxx&swv=xxxx
     * &serialno=xxxxx
     */
    private String packageCheckUrl() {
        String url = "http://121.43.183.196:8081/updsvr/ota";
        
        // 服务器地址
        Context con;
        try {
            con = createPackageContext("com.spreadtrum.android.eng", Context.CONTEXT_IGNORE_SECURITY);
            SharedPreferences sharedPreferences = con.getSharedPreferences("ota_updates_test_prefs", Context.MODE_MULTI_PROCESS);
            boolean isChecked = sharedPreferences.getBoolean("ota_updates_test", false);
            Log.i("pan", "isChecked:" + isChecked);
            if (isChecked) {
                url = "http://121.43.183.196:8081/updsvr/ota/test";
            } else {
                url = "http://121.43.183.196:8081/updsvr/ota";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // hw: 手机型号
        {
            String strdevice = SystemProperties.get("ro.cg.product.device", "");
            // url += "checkupdate?hw=" + Build.DEVICE;
            url += "checkupdate?hw=" + strdevice;
        }
        
        // hwv:硬件版本
        // String hwv = "1.1.0";
        // cg modify,2012-9-29
        String hwv = "P1";
        try {
            con = createPackageContext("com.android.settings", Context.CONTEXT_IGNORE_SECURITY);
            SharedPreferences sharedPreferences = con.getSharedPreferences("hardware_prefs", Context.MODE_WORLD_READABLE
                    + Context.MODE_WORLD_WRITEABLE);
            // hwv = sharedPreferences.getString("hardware_number", "1.1.0");
            hwv = sharedPreferences.getString("hardware_number", "P1");
        } catch (Exception e) {
            e.printStackTrace();
        }
        url += "&hwv=" + hwv;
        
        // swv: 软件版本
        // url += "&swv=" + Build.DISPLAY;
        String str_sw_ver = SystemProperties.get("ro.product.cg_version", "");
        url += "&swv=" + str_sw_ver;
        
        // 手机串号
        TelephonyManager telephonemanage = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        url += "&serialno=" + telephonemanage.getDeviceId();
        
        url = url.replace(' ', '.');
        
        url = url.replace("+", "%2B");
        
        Log.d("OTAUpdatesActivity", url);
        
        return url;
    }
    
    /*
     * 解析查询结果
     */
    private void parseCheckResData(String resData) {
        Log.d("OTAUpdatesActivity", resData);
        
        String FLAG = "OTA_NAME";
        int checkResult = -1;
        String objName, fileName, filePath, downloadUrl, description, srcVersion, dstVersion, priority, md5, sessionid = "";
        
        // get json data
        JSONObject jsonObject = new JSONObject(resData);
        
        objName = jsonObject.optString(FLAG);
        checkResult = jsonObject.optInt(StateValue.RESULT);
        fileName = jsonObject.optString(StateValue.FILENAME);
        filePath = jsonObject.optString(StateValue.FILEPATH);
        downloadUrl = jsonObject.optString(StateValue.DOWNLOADURL);
        description = jsonObject.optString(StateValue.DSTCRIPTION);
        srcVersion = jsonObject.optString(StateValue.SRC_VER);
        dstVersion = jsonObject.optString(StateValue.DST_VER);
        priority = jsonObject.optString(StateValue.PRIORITY);
        md5 = jsonObject.optString(StateValue.MD5);
        sessionid = jsonObject.optString(StateValue.SESSIONID);
        
        // 标记
        if (!objName.equals("blephone_ota")) {
            updatesInfo.result = -1;
            return;
        }
        
        // 结果
        updatesInfo.result = checkResult;
        if(checkResult < 0 || checkResult >= 1){
            return;
        }
        
        // 老版本
        if(srcVersion != "" || srcVersion != null){
            updatesInfo.src_version = srcVersion;
        }else{
            updatesInfo.result = -1;
            return;
        }
        
        // 新版本
        if(dstVersion != "" || dstVersion != null){
            updatesInfo.dst_version = dstVersion;
        }else{
            updatesInfo.result = -1;
            return;
        }
        
        // 描述
        if(description != "" || description != null){
            updatesInfo.description = description;
        }else{
            updatesInfo.result = -1;
            return;
        }
        
        // 下载地址
        if(downloadUrl != "" || downloadUrl != null){
            updatesInfo.downloadUrl = downloadUrl;
        }else{
            updatesInfo.result = -1;
            return;
        }
        
        // 优先级
        if(priority != "" || priority != null){
            updatesInfo.priority = priority;
        }else{
            updatesInfo.result = -1;
            return;
        }
        
        // 会话ID
        if(sessionid != "" || sessionid != null){
            updatesInfo.sessionId = sessionid;
        }else{
            updatesInfo.result = -1;
            return;
        }
        
        // MD5
        if(md5 != "" || md5 != null){
            updatesInfo.md5 = md5;
        }else{
            updatesInfo.result = -1;
            return;
        }
    }
    
    public void stopService() {
        super.stopSelf();
        running = false;
    }
    
    @Override
    public void onDestroy() {
        Log.i("pan", "onDestroy");
        unregisterReceiver(mBatteryInfoReceiver);
    }
    
    private void resetApnaftgetdata(Context context) {
        String name = "CMWAP";
        Uri carr_uri = null;
        int carr_id = TelephonyManager.getDefaultDataPhoneId(context);
        if (carr_id == 0) {
            carr_uri = Uri.parse("content://telephony/carriers");
        } else {
            carr_uri = Uri.parse("content://telephony_sim2/carriers");
        }
        Cursor cursor = getContentResolver().query(carr_uri, new String[] { "_id" }, "name=? and current=?", new String[] { name, "1" },
                null);
        String id = "";
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            id = cursor.getString(cursor.getColumnIndex("_id"));
            Log.e("OTAUpdatesActivity", "Settings onOptionsItemSelected  id = " + id);
        }
        SharedPreferences sharedPref = context.getSharedPreferences(StateValue.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor meditor = context.getSharedPreferences(StateValue.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE).edit();
        boolean needreset = sharedPref.getString("needresetapn", "0").equals("1") ? true : false;
        Log.i("pan", "needreset" + needreset + "");
        meditor.putString("needresetapn", "0");
        meditor.commit();
        
        if (needreset) {
            Log.i("pan", "******************" + id);
            setSelectedApnKey(context, id);
        }
    }
    
    private void setSelectedApnKey(Context context, String key) {
        ContentResolver resolver = context.getContentResolver();
        
        ContentValues values = new ContentValues();
        // Log.e("peter", "Settings   setSelectedApnKey  apn = "+apn);
        Log.d("OTAUpdatesActivity", "setSelectedApnKey=" + key);
        int carr_id = TelephonyManager.getDefaultDataPhoneId(this);
        Log.i("pan", "carr_id=" + carr_id);
        if (carr_id == 0) {
            values.put("apn_id", key);
            resolver.update(Uri.parse("content://telephony/carriers/preferapn"), values, null, null);
        } else {
            values.put("apn_id_sim2", key);
            resolver.update(Uri.parse("content://telephony_sim2/carriers/preferapn"), values, null, null);
        }
    }
    
    public long getSDCardFreeMemory() {
        long sdCardInfo = -1;
        
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            File sdcardDir = Environment.getExternalStorageDirectory();
            StatFs sf = new StatFs(sdcardDir.getPath());
            long bSize = sf.getBlockSize();
            long availBlocks = sf.getAvailableBlocks();
            
            sdCardInfo = bSize * availBlocks;
        }
        
        return sdCardInfo;
    }
    
    private BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                int level = intent.getIntExtra("level", 0);
                int scale = intent.getIntExtra("scale", 100);
                battLevel = level * 100 / scale;
            }
        }
    };
}
