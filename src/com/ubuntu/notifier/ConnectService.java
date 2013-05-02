package com.ubuntu.notifier;

import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class ConnectService extends Service implements Runnable {
	
	static Socket clientOSD;
	static Socket clientMenu;
	
	public static boolean connectOSD() {
		try {
			clientOSD = new Socket("192.168.1.126", 5210);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return clientOSD != null;
	}
	
	public static boolean connectMenu() {
		try {
			clientMenu = new Socket("172.18.216.218", 5211);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return clientMenu != null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void run() {		
		
	}
	
	public static final byte[] intToCharArray(int value) {
	    return new byte[] {
	            (byte)(value >> 24),
	            (byte)(value >> 16),
	            (byte)(value >> 8),
	            (byte)value};
	}
	
	public static void sendOSD(String data) {
		try {
			OutputStream out = clientOSD.getOutputStream();	
			byte[] bytes = data.getBytes();
			out.write(intToCharArray(bytes.length));
			out.write(bytes);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void sendMenu(String data) {
		try {
			OutputStream out = clientMenu.getOutputStream();	
			byte[] bytes = data.getBytes();
			out.write(intToCharArray(bytes.length));
			out.write(bytes);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void closeOSD() {
		try {
			clientOSD.close();
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}
	
	public static void closeMenu() {
		try {
			clientMenu.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
