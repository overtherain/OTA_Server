package com.ota;

import java.io.File;

import com.ota.OTAUpdatesActivity.NetworkChangeReceiver;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;

import android.app.AlarmManager;
import android.app.PendingIntent;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.SystemProperties;
import android.telephony.TelephonyManager;

public class AutoUpdatesReceiver  extends BroadcastReceiver
{

	private SharedPreferences m_checksetting = null;
	private Editor m_editor = null;
	
	// cg check version
	private SharedPreferences m_version=null;
	private Editor editor=null;
	private SharedPreferences sharedPref;
	private UpdatesInfo updatesInfo;
    // end cg check version
	
	NetworkChangeReceiver mReceiver = null;
    
	public final String AUTOCHECK = "android.intent.action.AUTOCHECK";

    @Override
    public void onReceive(Context context, Intent intent) 
    {
	    Log.d("OTAUpdatesActivity","AutoUpdatesReceiver,I receive the Auto-Updates message, intent.getAction() =" + intent.getAction());
		if(intent.getAction().equals("android.intent.action.TIME_SET")) {
			long curtime = java.lang.System.currentTimeMillis();
			SharedPreferences mSharedPreferences = context.getSharedPreferences("autocheckset", Context.MODE_PRIVATE);
			long starttime = mSharedPreferences.getLong("starttime", 0);
			Log.d("OTAUpdatesActivity", "TIME_SET starttime=" + android.text.format.DateFormat.format("yy-MM-dd hh:mm:ss", starttime) + " curtime=" + android.text.format.DateFormat.format("yy-MM-dd hh:mm:ss", curtime));
			if(curtime < starttime) {
				SharedPreferences.Editor cgeditor = mSharedPreferences.edit();
				cgeditor.putLong("starttime", curtime);
				cgeditor.commit();
				Log.d("OTAUpdatesActivity", "TIME_SET store curtime=" + curtime);
				Intent mintent = new Intent(AUTOCHECK);
				PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, mintent, PendingIntent.FLAG_UPDATE_CURRENT);
				AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
				boolean checkset = mSharedPreferences.getBoolean("checkset", true);
				if(checkset) {
					am.setRepeating(AlarmManager.RTC, System.currentTimeMillis()+3*24*60*60000, 3*24*60*60000, pendingIntent);
				} else {
					am.cancel(pendingIntent);
				}
			} else if(curtime - starttime > 3*24*60*60000) {
				SharedPreferences.Editor cgeditor = mSharedPreferences.edit();
				cgeditor.putLong("starttime", curtime);
				cgeditor.commit();
				Log.d("OTAUpdatesActivity", "TIME_SET curtime=" + android.text.format.DateFormat.format("yy-MM-dd hh:mm:ss", curtime));
			}
			return;
		}
		if(intent.getAction().equals("android.cg.intent.action.AUTOCHECKCHANGED")) {
			SharedPreferences mSharedPreferences = context.getSharedPreferences("autocheckset", Context.MODE_PRIVATE);
			boolean checkset = intent.getBooleanExtra("cgotaauto", true);
			SharedPreferences.Editor cgeditor = mSharedPreferences.edit();
			cgeditor.putBoolean("checkset", checkset);
			cgeditor.commit();
			return;
		}

		if(intent.getAction().equals("android.net.conn.CONNECTIVITY_CHANGE")) {
			if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) 
			{ 
				SharedPreferences sharedPref = context.getSharedPreferences(StateValue.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
           			boolean needlistennetchange = sharedPref.getString("needlistennetchange", "0").equals("1") ? true : false;
				if(!needlistennetchange) {
					return;
				}
       				ConnectivityManager conManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
               			NetworkInfo info = conManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE); 
               			String apn = info.getExtraInfo(); 
               			Log.e("OTAUpdatesActivity", "AutoUpdatesReceiver Settings  onReceive  apn = "+apn);
               			Log.i("pan", "AutoUpdatesReceiver set apn:"+apn);
				if (info.isConnected() && ("cmnet".equals(apn))) 
				{ 
					Intent updateIntent = new Intent(context, CheckNewVerService.class);
					updateIntent.putExtra(StateValue.ISAUTOCHECK, true); 
					context.startService(updateIntent);
					SharedPreferences.Editor meditor = context.getSharedPreferences(StateValue.SHARED_PREFERENCES_NAME,Context.MODE_PRIVATE).edit();
					meditor.putString("needlistennetchange", "0");
					meditor.commit();
				}
			} 
			return;
		}

	    m_checksetting =context.getSharedPreferences("auto_update_setting", 0);
	    String str_is_auto = m_checksetting.getString("is_auto", null);
	    m_editor = m_checksetting.edit();

	    if (null == str_is_auto)
	    {
		    m_editor.putString("is_auto", "1");
		    m_editor.commit();

		    Intent mintent = new Intent(AUTOCHECK);
		    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, mintent, PendingIntent.FLAG_UPDATE_CURRENT);
		    AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
		    am.setRepeating(AlarmManager.RTC, System.currentTimeMillis()+3*24*60*60000, 3*24*60*60000, pendingIntent);//15缁夋帡鎸撴禒銉ユ倵閸氼垰濮�
			long starttime = java.lang.System.currentTimeMillis();
			SharedPreferences mSharedPreferences = context.getSharedPreferences("autocheckset", Context.MODE_PRIVATE);
			SharedPreferences.Editor cgeditor = mSharedPreferences.edit();
			cgeditor.putLong("starttime", starttime);
			cgeditor.commit();
			Log.d("OTAUpdatesActivity", "null == starttime=" + android.text.format.DateFormat.format("yy-MM-dd hh:mm:ss", starttime));
			Log.e("OTAUpdatesActivity", "null == str_is_auto store starttime= "+starttime);
	    }

	    if (intent.getAction().equals("android.intent.action.AUTOCHECK"))
	    {
			long starttime = java.lang.System.currentTimeMillis();
			SharedPreferences mSharedPreferences = context.getSharedPreferences("autocheckset", Context.MODE_PRIVATE);
			SharedPreferences.Editor cgeditor = mSharedPreferences.edit();
			cgeditor.putLong("starttime", starttime);
			cgeditor.commit();
			Log.d("OTAUpdatesActivity", "AUTOCHECK starttime=" + android.text.format.DateFormat.format("yy-MM-dd hh:mm:ss", starttime));
//	    	if(!setApnCmnetBefore(context)) {
	    		Intent updateIntent =new Intent(context, CheckNewVerService.class);
	    		updateIntent.putExtra(StateValue.ISAUTOCHECK, true); 
	    		context.startService(updateIntent);
//	    	}
	    }
	    else if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED"))
	    {
		    //cg modify 2012-11-5 start
		    m_version=context.getSharedPreferences("version", 0);
		    editor=m_version.edit();

		    sharedPref = context.getSharedPreferences(StateValue.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		    updatesInfo = new UpdatesInfo();
		    updatesInfo.dst_version = sharedPref.getString(StateValue.DST_VER, "0");

		    String str_prop_ver = SystemProperties.get("ro.product.cg_version", "");
            Log.i("pan", "str:"+str_prop_ver);
            Log.i("pan", "updatesInfo.dst_version:"+updatesInfo.dst_version);
            Log.i("pan", "updatesInfo.src_version:"+updatesInfo.src_version);
		    String cg_version=m_version.getString("cg_version",str_prop_ver);
		    Log.i("pan", "cg_version:"+cg_version);
		    int iret = 0;
		    do
		    {
			    if(cg_version.equals(str_prop_ver))
			    {
				    editor.putString("cg_version", str_prop_ver);
				    editor.commit();
				    iret = -10;				
				    break;
			    }

			    if(!str_prop_ver.equals(updatesInfo.dst_version))
			    {
				    iret = -20;
				    break;	
			    }
					Toast.makeText(context, R.string.updates_success, Toast.LENGTH_LONG).show();
			    editor.putString("cg_version", str_prop_ver);
			    editor.commit();
			    break;

		    }while(false);
		    Log.d("OTAUpdatesActivity", "AutoUpdatesReceiver::onReceive() iret = " + iret);
			{
				SharedPreferences mSharedPreferences = context.getSharedPreferences("autocheckset", Context.MODE_PRIVATE);
				boolean checkset = mSharedPreferences.getBoolean("checkset", true);
				long starttime = mSharedPreferences.getLong("starttime", 0);
				long curtime = java.lang.System.currentTimeMillis();
				if(checkset) {
					long timedelay = 3*24*60*60000 + starttime - curtime;
					if(timedelay < 0) {
						timedelay = 60*60000;
					}
					Log.d("OTAUpdatesActivity", "BOOT_COMPLETED starttime=" + android.text.format.DateFormat.format("yy-MM-dd hh:mm:ss", starttime) + " curtime=" + android.text.format.DateFormat.format("yy-MM-dd hh:mm:ss", curtime));
					Log.d("OTAUpdatesActivity", "BOOT_COMPLETED timedelay=" + timedelay);
					Intent mintent = new Intent(AUTOCHECK);
					PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, mintent, PendingIntent.FLAG_UPDATE_CURRENT);
					AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
					am.setRepeating(AlarmManager.RTC, System.currentTimeMillis()+timedelay, 3*24*60*60000, pendingIntent);
				} else {
					Intent mintent = new Intent(AUTOCHECK);
					PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, mintent, PendingIntent.FLAG_UPDATE_CURRENT);
					AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
					am.cancel(pendingIntent);
				}
			}

	    } //  else if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED"))
    } //     public void onReceive(Context context, Intent intent) 
    
    private boolean setApnCmnetBefore(Context context){
		int iret = 0;
		Cursor cursor = null;
		String name = "CMNET";
		ConnectivityManager conManager = 
				(ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		do
		{
			if(conManager == null)	
			{
				break;
			}

			NetworkInfo ni = conManager.getActiveNetworkInfo(); 
			if (ni == null)
			{
				break;
			}
			
			int carr_id = TelephonyManager.getDefaultDataPhoneId(context);
			Log.i("pan", "carr_id="+carr_id);
			if(carr_id == 0) {
				cursor = context.getContentResolver().query(Uri.parse("content://telephony/carriers"),
						new String[]{"_id"}, "name=? and current=?", new String[]{name,"1"}, null);
			} else {
				cursor = context.getContentResolver().query(Uri.parse("content://telephony_sim2/carriers"),
						new String[]{"_id"}, "name=? and current=?", new String[]{name,"1"}, null);
			}
			if (cursor == null)
			{
				break;
			}
			String id = "";
			
			if (cursor.getCount() > 0)
			{
				cursor.moveToFirst();			
				id = cursor.getString(cursor.getColumnIndex("_id"));		
				Log.e("OTAUpdatesActivity", "Settings	onOptionsItemSelected  id = "+id);
			} else {
				break;
			}
			
			if (isConnectedToWifi(ni))
			{
				if (!ni.isConnected())
				{
					break;
				}
				if (cursor.getCount() > 0)
				{
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
					setSelectedApnKey(context, id);
					
					SharedPreferences sharedPref = context.getSharedPreferences(StateValue.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
					SharedPreferences.Editor meditor = context.getSharedPreferences(StateValue.SHARED_PREFERENCES_NAME,Context.MODE_PRIVATE).edit();
					meditor.putString("needresetapn", "1");
					meditor.putString("needlistennetchange", "1");
					meditor.commit();
					Log.e("OTAUpdatesActivity", "set apn for get data cmwap -> cmnet");
					Log.i("pan", "set apn for get data cmwap -> cmnet");
					return true;
				}
				break;
			}
			
		}while(false);

		if (cursor != null)
		{
			cursor.close();
			cursor  = null;
		}
		
		return false;
	}
    
    public boolean isConnectedToWifi(NetworkInfo mNetworkInfo) 
	{
		Log.d("OTAUpdatesActivity", "isConnectedToWifi = " + mNetworkInfo.getType());
		return (mNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI);
	}
    
    private void setSelectedApnKey(Context context,String key)
	 {
       ContentResolver resolver = context.getContentResolver();

       ContentValues values = new ContentValues();
       int carr_id = TelephonyManager.getDefaultDataPhoneId(context);
       Log.i("pan", "carr_id="+carr_id);
		if(carr_id == 0) {
			values.put("apn_id", key);
			resolver.update(Uri.parse("content://telephony/carriers/preferapn"), values, null, null);
		} else {
			values.put("apn_id_sim2", key);
			resolver.update(Uri.parse("content://telephony_sim2/carriers/preferapn"), values, null, null);
		}
   }

}


