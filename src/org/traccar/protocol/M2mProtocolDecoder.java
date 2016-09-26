/*
 * Copyright 2013 Anton Tananaev (anton.tananaev@gmail.com)
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
package org.traccar.protocol;

import java.net.SocketAddress;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.helper.DateBuilder;
import org.traccar.model.Position;

public class M2mProtocolDecoder extends BaseProtocolDecoder {
  
  public M2mProtocolDecoder(M2mProtocol protocol) {
    super(protocol);
  }
  
  private boolean firstPacket = true;
  
  @Override
  protected Object decode(Channel channel, SocketAddress remoteAddress,
      Object msg) throws Exception {
    
    ChannelBuffer buf = (ChannelBuffer) msg;
    
    // Remove offset
    for (int i = 0; i < buf.readableBytes(); i++) {
      int b = buf.getByte(i);
      if (b != 0x0b) {
        buf.setByte(i, b - 0x20);
      }
    }
    
    if (firstPacket) {
      
      firstPacket = false;
      
      StringBuilder imei = new StringBuilder();
      for (int i = 0; i < 8; i++) {
        int b = buf.readByte();
        if (i != 0) {
          imei.append(b / 10);
        }
        imei.append(b % 10);
      }
      
      identify(imei.toString(), channel, remoteAddress);
      
    } else if (hasDeviceId()) {
      
      Position position = new Position();
      position.setProtocol(getProtocolName());
      position.setDeviceId(getDeviceId());
      
      DateBuilder dateBuilder = new DateBuilder()
          .setDay(buf.readUnsignedByte() & 0x3f)
          .setMonth(buf.readUnsignedByte() & 0x3f)
          .setYear(buf.readUnsignedByte())
          .setHour(buf.readUnsignedByte() & 0x3f)
          .setMinute(buf.readUnsignedByte() & 0x7f)
          .setSecond(buf.readUnsignedByte() & 0x7f);
      position.setTime(dateBuilder.getDate());
      
      int degrees = buf.readUnsignedByte();
      double latitude = buf.readUnsignedByte();
      latitude += buf.readUnsignedByte() / 100.0;
      latitude += buf.readUnsignedByte() / 10000.0;
      latitude /= 60;
      latitude += degrees;
      
      int b = buf.readUnsignedByte();
      
      degrees = (b & 0x7f) * 100 + buf.readUnsignedByte();
      double longitude = buf.readUnsignedByte();
      longitude += buf.readUnsignedByte() / 100.0;
      longitude += buf.readUnsignedByte() / 10000.0;
      longitude /= 60;
      longitude += degrees;
      
      if ((b & 0x80) != 0) {
        longitude = -longitude;
      }
      if ((b & 0x40) != 0) {
        latitude = -latitude;
      }
      
      position.setValid(true);
      position.setLatitude(latitude);
      position.setLongitude(longitude);
      position.setSpeed(buf.readUnsignedByte());
      
      int satellites = buf.readUnsignedByte();
      if (satellites == 0) {
        return null; // cell information
      }
      position.set(Position.KEY_SATELLITES, satellites);
      
      // decode other data
      
      return position;
      
    }
    
    return null;
  }
  
}