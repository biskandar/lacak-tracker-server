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
package org.traccar;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Date;

import org.jboss.netty.channel.Channel;
import org.traccar.helper.Log;
import org.traccar.model.Device;
import org.traccar.model.Position;

public abstract class BaseProtocolDecoder extends ExtendedObjectDecoder {
  
  private final Protocol protocol;
  
  public String getProtocolName() {
    return protocol.getName();
  }
  
  private long deviceId;
  
  public boolean hasDeviceId() {
    return deviceId != 0;
  }
  
  public long getDeviceId() {
    return deviceId;
  }
  
  public boolean identify(String uniqueId, Channel channel,
      SocketAddress remoteAddress, boolean logWarning) {
    try {
      Device device = Context.getIdentityManager()
          .getDeviceByUniqueId(uniqueId);
      if (device != null) {
        deviceId = device.getId();
        Context.getConnectionManager().addActiveDevice(deviceId, protocol,
            channel, remoteAddress);
        return true;
      } else {
        deviceId = 0;
        if (logWarning) {
          String message = "Unknown device - " + uniqueId;
          if (remoteAddress != null) {
            message += " ("
                + ((InetSocketAddress) remoteAddress).getHostString() + ")";
          }
          Log.warning(message);
        }
        return false;
      }
    } catch (Exception error) {
      deviceId = 0;
      Log.warning(error);
      return false;
    }
  }
  
  public boolean identify(String uniqueId, Channel channel,
      SocketAddress remoteAddress) {
    return identify(uniqueId, channel, remoteAddress, true);
  }
  
  public BaseProtocolDecoder(Protocol protocol) {
    this.protocol = protocol;
  }
  
  public void getLastLocation(Position position, Date deviceTime) {
    position.setOutdated(true);
    
    Position last = Context.getConnectionManager().getLastPosition(
        getDeviceId());
    if (last != null) {
      position.setFixTime(last.getFixTime());
      position.setValid(last.getValid());
      position.setLatitude(last.getLatitude());
      position.setLongitude(last.getLongitude());
      position.setAltitude(last.getAltitude());
      position.setSpeed(last.getSpeed());
      position.setCourse(last.getCourse());
    } else {
      position.setFixTime(new Date(0));
    }
    
    if (deviceTime != null) {
      position.setDeviceTime(deviceTime);
    } else {
      position.setDeviceTime(new Date());
    }
  }
  
  @Override
  protected void onMessageEvent(Channel channel, SocketAddress remoteAddress,
      Object msg) {
    if (hasDeviceId()) {
      Context.getConnectionManager().updateDevice(deviceId,
          Device.STATUS_ONLINE, new Date());
    }
  }
  
  @Override
  protected Object handleEmptyMessage(Channel channel,
      SocketAddress remoteAddress, Object msg) {
    if (Context.getConfig().getBoolean("database.saveEmpty") && hasDeviceId()) {
      Position position = new Position();
      position.setProtocol(getProtocolName());
      position.setDeviceId(getDeviceId());
      getLastLocation(position, null);
      return position;
    } else {
      return null;
    }
  }
  
}
