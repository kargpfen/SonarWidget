package com.vaadin.sonarwidget.data;

public interface Ping {
	byte[] getSoundings();
	float getLowLimit();
	float getTemp();
	float getDepth();
}