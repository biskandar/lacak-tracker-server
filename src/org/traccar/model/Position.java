/*
 * Copyright 2012 - 2016 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.model;

import java.util.Date;

public class Position extends Message {
  
  // Words separated by dashes (word-second-third)
  public static final String KEY_ORIGINAL = "raw";
  public static final String KEY_INDEX = "index";
  public static final String KEY_HDOP = "hdop";
  public static final String KEY_SATELLITES = "sat";
  public static final String KEY_GSM = "gsm";
  public static final String KEY_GPS = "gps";
  public static final String KEY_EVENT = "event";
  public static final String KEY_ALARM = "alarm";
  public static final String KEY_STATUS = "status";
  public static final String KEY_ODOMETER = "odometer";
  public static final String KEY_HOURS = "hours";
  public static final String KEY_INPUT = "input";
  public static final String KEY_OUTPUT = "output";
  public static final String KEY_POWER = "power";
  public static final String KEY_BATTERY = "battery";
  public static final String KEY_MCC = "mcc";
  public static final String KEY_MNC = "mnc";
  public static final String KEY_LAC = "lac";
  public static final String KEY_CID = "cid";
  public static final String KEY_FUEL = "fuel";
  public static final String KEY_RFID = "rfid";
  public static final String KEY_VERSION = "version";
  public static final String KEY_TYPE = "type";
  public static final String KEY_IGNITION = "ignition";
  public static final String KEY_FLAGS = "flags";
  public static final String KEY_CHARGE = "charge";
  public static final String KEY_IP = "ip";
  public static final String KEY_ARCHIVE = "archive";
  public static final String KEY_DISTANCE = "distance";
  public static final String KEY_RPM = "rpm";
  public static final String KEY_VIN = "vin";
  public static final String KEY_APPROXIMATE = "approximate";
  public static final String KEY_THROTTLE = "throttle";
  public static final String KEY_MOTION = "motion";
  
  public static final String KEY_OBD_SPEED = "obd-speed";
  public static final String KEY_OBD_ODOMETER = "obd-odometer";
  
  public static final String KEY_RESULT = "result";
  
  // Starts with 1 not 0
  public static final String PREFIX_TEMP = "temp";
  public static final String PREFIX_ADC = "adc";
  public static final String PREFIX_IO = "io";
  public static final String PREFIX_COUNT = "count";
  
  private String protocol;
  
  public String getProtocol() {
    return protocol;
  }
  
  public void setProtocol(String protocol) {
    this.protocol = protocol;
  }
  
  private Date serverTime;
  
  public Date getServerTime() {
    if (serverTime != null) {
      return new Date(serverTime.getTime());
    } else {
      return null;
    }
  }
  
  public void setServerTime(Date serverTime) {
    if (serverTime != null) {
      this.serverTime = new Date(serverTime.getTime());
    } else {
      this.serverTime = null;
    }
  }
  
  private Date deviceTime;
  
  public Date getDeviceTime() {
    if (deviceTime != null) {
      return new Date(deviceTime.getTime());
    } else {
      return null;
    }
  }
  
  public void setDeviceTime(Date deviceTime) {
    if (deviceTime != null) {
      this.deviceTime = new Date(deviceTime.getTime());
    } else {
      this.deviceTime = null;
    }
  }
  
  private Date fixTime;
  
  public Date getFixTime() {
    if (fixTime != null) {
      return new Date(fixTime.getTime());
    } else {
      return null;
    }
  }
  
  public void setFixTime(Date fixTime) {
    if (fixTime != null) {
      this.fixTime = new Date(fixTime.getTime());
    } else {
      this.fixTime = null;
    }
  }
  
  public void setTime(Date time) {
    setDeviceTime(time);
    setFixTime(time);
  }
  
  private boolean outdated;
  
  public boolean getOutdated() {
    return outdated;
  }
  
  public void setOutdated(boolean outdated) {
    this.outdated = outdated;
  }
  
  private boolean valid;
  
  public boolean getValid() {
    return valid;
  }
  
  public void setValid(boolean valid) {
    this.valid = valid;
  }
  
  private double latitude;
  
  public double getLatitude() {
    return latitude;
  }
  
  public void setLatitude(double latitude) {
    this.latitude = latitude;
  }
  
  private double longitude;
  
  public double getLongitude() {
    return longitude;
  }
  
  public void setLongitude(double longitude) {
    this.longitude = longitude;
  }
  
  private double altitude;
  
  public double getAltitude() {
    return altitude;
  }
  
  public void setAltitude(double altitude) {
    this.altitude = altitude;
  }
  
  private double speed; // value in knots
  
  public double getSpeed() {
    return speed;
  }
  
  public void setSpeed(double speed) {
    this.speed = speed;
  }
  
  private double course;
  
  public double getCourse() {
    return course;
  }
  
  public void setCourse(double course) {
    this.course = course;
  }
  
  private String address;
  
  public String getAddress() {
    return address;
  }
  
  public void setAddress(String address) {
    this.address = address;
  }
  
}
