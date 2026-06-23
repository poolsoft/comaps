package com.acloud.stub.service.aidl;

import com.acloud.stub.service.aidl.IPlayServiceCallback;

interface IPlayService {
	void setCallback(in IPlayServiceCallback callback);
	void init();
	void setAction(String action);
	void play(String path, int musicId, int resMode);
	void start();
	void pause();
	int getDuration();
	int getPosition();
	int getState();
	void seekTo(int msec);
	void stop();
	void release();
	void nativeToWidget(in String control);
	String getWidgetMsg();
	void unRegiestMediaButton();
}
