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
package org.traccar.protocol;

import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.helper.DateBuilder;
import org.traccar.helper.UnitsConverter;
import org.traccar.model.Position;

public class HuaShengProtocolDecoder extends BaseProtocolDecoder {
  
  public HuaShengProtocolDecoder(HuaShengProtocol protocol) {
    super(protocol);
  }
  
  public static final int MSG_POSITION = 0xAA00;
  public static final int MSG_POSITION_RSP = 0xFF01;
  public static final int MSG_LOGIN = 0xAA02;
  public static final int MSG_LOGIN_RSP = 0xFF03;
  
  private static void sendResponse(Channel channel, int type,
      ChannelBuffer content) {
    if (channel != null) {
      ChannelBuffer response = ChannelBuffers.dynamicBuffer();
      response.writeByte(0xC0);
      response.writeShort(0x0100);
      response.writeShort(12 + content.readableBytes());
      response.writeShort(type);
      response.writeShort(0);
      response.writeInt(1);
      response.writeBytes(content);
      response.writeByte(0xC0);
      channel.write(response);
    }
  }
  
  @Override
  protected Object decode(Channel channel, SocketAddress remoteAddress,
      Object msg) throws Exception {
    
    ChannelBuffer buf = (ChannelBuffer) msg;
    
    buf.skipBytes(1); // start marker
    buf.readUnsignedByte(); // flag
    buf.readUnsignedByte(); // reserved
    buf.readUnsignedShort(); // length
    
    int type = buf.readUnsignedShort();
    
    buf.readUnsignedShort(); // checksum
    int index = buf.readInt();
    
    if (type == MSG_LOGIN) {
      
      while (buf.readableBytes() > 4) {
        int subtype = buf.readUnsignedShort();
        int length = buf.readUnsignedShort() - 4;
        if (subtype == 0x0003) {
          String imei = buf.readBytes(length).toString(
              StandardCharsets.US_ASCII);
          if (identify(imei, channel, remoteAddress) && channel != null) {
            ChannelBuffer content = ChannelBuffers.dynamicBuffer();
            content.writeByte(0); // success
            sendResponse(channel, MSG_LOGIN_RSP, content);
          }
        } else {
          buf.skipBytes(length);
        }
      }
      
    } else if (type == MSG_POSITION) {
      
      Position position = new Position();
      position.setProtocol(getProtocolName());
      position.setDeviceId(getDeviceId());
      
      position.set(Position.KEY_STATUS, buf.readUnsignedShort());
      position.set(Position.KEY_EVENT, buf.readUnsignedShort());
      
      String time = buf.readBytes(12).toString(StandardCharsets.US_ASCII);
      
      DateBuilder dateBuilder = new DateBuilder()
          .setYear(Integer.parseInt(time.substring(0, 2)))
          .setMonth(Integer.parseInt(time.substring(2, 4)))
          .setDay(Integer.parseInt(time.substring(4, 6)))
          .setHour(Integer.parseInt(time.substring(6, 8)))
          .setMinute(Integer.parseInt(time.substring(8, 10)))
          .setSecond(Integer.parseInt(time.substring(10, 12)));
      position.setTime(dateBuilder.getDate());
      
      position.setLongitude(buf.readInt() * 0.00001);
      position.setLatitude(buf.readInt() * 0.00001);
      
      position.setSpeed(UnitsConverter.knotsFromKph(buf.readUnsignedShort()));
      position.setCourse(buf.readUnsignedShort());
      position.setAltitude(buf.readUnsignedShort());
      
      position.set(Position.KEY_ODOMETER, buf.readUnsignedShort());
      
      while (buf.readableBytes() > 4) {
        buf.readUnsignedShort(); // subtype
        int length = buf.readUnsignedShort() - 4;
        buf.skipBytes(length);
      }
      
      ChannelBuffer content = ChannelBuffers.dynamicBuffer();
      content.writeInt(index);
      sendResponse(channel, MSG_POSITION_RSP, content);
      
      return position;
      
    }
    
    return null;
  }
  
}
