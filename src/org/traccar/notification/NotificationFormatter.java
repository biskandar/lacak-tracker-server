/*
 * Copyright 2016 Anton Tananaev (anton.tananaev@gmail.com)
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
package org.traccar.notification;

import java.util.Formatter;
import java.util.Locale;

import org.traccar.Context;
import org.traccar.model.Device;
import org.traccar.model.Event;
import org.traccar.model.Position;

public final class NotificationFormatter {
  
  private NotificationFormatter() {
  }
  
  public static final String TITLE_TEMPLATE_TYPE_COMMAND_RESULT = "%1$s: command result received";
  public static final String MESSAGE_TEMPLATE_TYPE_COMMAND_RESULT = "Device: %1$s%n"
      + "Result: %3$s%n" + "Time: %2$tc%n";
  
  public static final String TITLE_TEMPLATE_TYPE_DEVICE_ONLINE = "%1$s: online";
  public static final String MESSAGE_TEMPLATE_TYPE_DEVICE_ONLINE = "Device: %1$s%n"
      + "Online%n" + "Time: %2$tc%n";
  public static final String TITLE_TEMPLATE_TYPE_DEVICE_OFFLINE = "%1$s: offline";
  public static final String MESSAGE_TEMPLATE_TYPE_DEVICE_OFFLINE = "Device: %1$s%n"
      + "Offline%n" + "Time: %2$tc%n";
  
  public static final String TITLE_TEMPLATE_TYPE_DEVICE_MOVING = "%1$s: moving";
  public static final String MESSAGE_TEMPLATE_TYPE_DEVICE_MOVING = "Device: %1$s%n"
      + "Moving%n"
      + "Point: http://www.openstreetmap.org/?mlat=%3$f&mlon=%4$f#map=16/%3$f/%4$f%n"
      + "Time: %2$tc%n";
  public static final String TITLE_TEMPLATE_TYPE_DEVICE_STOPPED = "%1$s: stopped";
  public static final String MESSAGE_TEMPLATE_TYPE_DEVICE_STOPPED = "Device: %1$s%n"
      + "Stopped%n"
      + "Point: http://www.openstreetmap.org/?mlat=%3$f&mlon=%4$f#map=16/%3$f/%4$f%n"
      + "Time: %2$tc%n";
  
  public static final String TITLE_TEMPLATE_TYPE_DEVICE_OVERSPEED = "%1$s: exeeds the speed";
  public static final String MESSAGE_TEMPLATE_TYPE_DEVICE_OVERSPEED = "Device: %1$s%n"
      + "Exeeds the speed: %5$f%n"
      + "Point: http://www.openstreetmap.org/?mlat=%3$f&mlon=%4$f#map=16/%3$f/%4$f%n"
      + "Time: %2$tc%n";
  
  public static final String TITLE_TEMPLATE_TYPE_GEOFENCE_ENTER = "%1$s: has entered geofence";
  public static final String MESSAGE_TEMPLATE_TYPE_GEOFENCE_ENTER = "Device: %1$s%n"
      + "Has entered geofence: %5$s%n"
      + "Point: http://www.openstreetmap.org/?mlat=%3$f&mlon=%4$f#map=16/%3$f/%4$f%n"
      + "Time: %2$tc%n";
  public static final String TITLE_TEMPLATE_TYPE_GEOFENCE_EXIT = "%1$s: has exited geofence";
  public static final String MESSAGE_TEMPLATE_TYPE_GEOFENCE_EXIT = "Device: %1$s%n"
      + "Has exited geofence: %5$s%n"
      + "Point: http://www.openstreetmap.org/?mlat=%3$f&mlon=%4$f#map=16/%3$f/%4$f%n"
      + "Time: %2$tc%n";
  
  public static String formatTitle(long userId, Event event, Position position) {
    Device device = Context.getIdentityManager().getDeviceById(
        event.getDeviceId());
    StringBuilder stringBuilder = new StringBuilder();
    Formatter formatter = new Formatter(stringBuilder, Locale.getDefault());
    
    switch (event.getType()) {
    case Event.TYPE_COMMAND_RESULT:
      formatter.format(TITLE_TEMPLATE_TYPE_COMMAND_RESULT, device.getName());
      break;
    case Event.TYPE_DEVICE_ONLINE:
      formatter.format(TITLE_TEMPLATE_TYPE_DEVICE_ONLINE, device.getName());
      break;
    case Event.TYPE_DEVICE_OFFLINE:
      formatter.format(TITLE_TEMPLATE_TYPE_DEVICE_OFFLINE, device.getName());
      break;
    case Event.TYPE_DEVICE_MOVING:
      formatter.format(TITLE_TEMPLATE_TYPE_DEVICE_MOVING, device.getName());
      break;
    case Event.TYPE_DEVICE_STOPPED:
      formatter.format(TITLE_TEMPLATE_TYPE_DEVICE_STOPPED, device.getName());
      break;
    case Event.TYPE_DEVICE_OVERSPEED:
      formatter.format(TITLE_TEMPLATE_TYPE_DEVICE_OVERSPEED, device.getName());
      break;
    case Event.TYPE_GEOFENCE_ENTER:
      formatter.format(TITLE_TEMPLATE_TYPE_GEOFENCE_ENTER, device.getName());
      break;
    case Event.TYPE_GEOFENCE_EXIT:
      formatter.format(TITLE_TEMPLATE_TYPE_GEOFENCE_EXIT, device.getName());
      break;
    default:
      formatter.format("Unknown type");
      break;
    }
    String result = formatter.toString();
    formatter.close();
    return result;
  }
  
  public static String formatMessage(long userId, Event event, Position position) {
    Device device = Context.getIdentityManager().getDeviceById(
        event.getDeviceId());
    StringBuilder stringBuilder = new StringBuilder();
    Formatter formatter = new Formatter(stringBuilder, Locale.getDefault());
    
    switch (event.getType()) {
    case Event.TYPE_COMMAND_RESULT:
      formatter.format(MESSAGE_TEMPLATE_TYPE_COMMAND_RESULT, device.getName(),
          event.getServerTime(), position.getAttributes().get("result"));
      break;
    case Event.TYPE_DEVICE_ONLINE:
      formatter.format(MESSAGE_TEMPLATE_TYPE_DEVICE_ONLINE, device.getName(),
          event.getServerTime());
      break;
    case Event.TYPE_DEVICE_OFFLINE:
      formatter.format(MESSAGE_TEMPLATE_TYPE_DEVICE_OFFLINE, device.getName(),
          event.getServerTime());
      break;
    case Event.TYPE_DEVICE_MOVING:
      formatter.format(MESSAGE_TEMPLATE_TYPE_DEVICE_MOVING, device.getName(),
          position.getFixTime(), position.getLatitude(),
          position.getLongitude());
      break;
    case Event.TYPE_DEVICE_STOPPED:
      formatter.format(MESSAGE_TEMPLATE_TYPE_DEVICE_STOPPED, device.getName(),
          position.getFixTime(), position.getLatitude(),
          position.getLongitude());
      break;
    case Event.TYPE_DEVICE_OVERSPEED:
      formatter.format(MESSAGE_TEMPLATE_TYPE_DEVICE_OVERSPEED,
          device.getName(), position.getFixTime(), position.getLatitude(),
          position.getLongitude(), position.getSpeed());
      break;
    case Event.TYPE_GEOFENCE_ENTER:
      formatter.format(MESSAGE_TEMPLATE_TYPE_GEOFENCE_ENTER, device.getName(),
          position.getFixTime(), position.getLatitude(),
          position.getLongitude(),
          Context.getGeofenceManager().getGeofence(event.getGeofenceId())
              .getName());
      break;
    case Event.TYPE_GEOFENCE_EXIT:
      formatter.format(MESSAGE_TEMPLATE_TYPE_GEOFENCE_EXIT, device.getName(),
          position.getFixTime(), position.getLatitude(),
          position.getLongitude(),
          Context.getGeofenceManager().getGeofence(event.getGeofenceId())
              .getName());
      break;
    default:
      formatter.format("Unknown type");
      break;
    }
    String result = formatter.toString();
    formatter.close();
    return result;
  }
}
