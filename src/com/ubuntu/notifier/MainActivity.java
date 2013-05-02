package com.ubuntu.notifier;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import dalvik.system.PathClassLoader;

import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class MainActivity extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		this.startService(new Intent(this, ConnectService.class));
		
		final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		//		
		
		
				
		try {
			final Context context = this.createPackageContext("com.android.systemui", Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
			Class ActivityManagerNative = Class.forName("android.app.ActivityManagerNative");
			Method getDefault = ActivityManagerNative.getDeclaredMethod("getDefault", null);
			Object amn = getDefault.invoke(null, null);
			Method killApplicationProcess = amn.getClass().getDeclaredMethod("killApplicationProcess", String.class, int.class);
			
			stopService(new Intent().setComponent(new ComponentName("com.android.systemui", "com.android.systemui.SystemUIService")));
			am.killBackgroundProcesses("com.android.systemui");
			for (RunningAppProcessInfo app :am.getRunningAppProcesses()) {
				if ("com.android.systemui".equals(app.processName)) {
					killApplicationProcess.invoke(amn, app.processName, app.uid);
					break;
				}
			}
			StatusBarService.setContext(context);
			startService(new Intent(this, StatusBarService.class));
		} catch (Exception e) {
			e.printStackTrace();
		}
	
		
		
		
		final Notification notification = new Notification();
		notification.icon = R.drawable.ic_launcher;
		notification.tickerText = "aaa";
		notification.when = System.currentTimeMillis();
		notification.flags = Notification.FLAG_AUTO_CANCEL;
		
		final Intent notificationIntent = new Intent(this, MainActivity.class);
		final PendingIntent contentIntent = PendingIntent.getActivity(this, 0,notificationIntent, 0);
		//notification.number = 3;
		//notification.setLatestEventInfo(this, "xxxx", "你好", contentIntent);
		//nm.notify(110, notification);
		
		final Notification.Builder builder = new Notification.Builder(this.getBaseContext());
		
		Button button1 = (Button)findViewById(R.id.button1);
		button1.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				//final Intent notificationIntent = new Intent(this, MainActivity.class);
				//final PendingIntent contentIntent = PendingIntent.getActivity(this, 0,notificationIntent, 0);
				builder.setContentText("xx");
				builder.setContentTitle("bb");
				builder.setSmallIcon(R.drawable.ic_launcher);
				Notification nn = builder.build();
				nm.notify(110, nn);
			}
			
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
}