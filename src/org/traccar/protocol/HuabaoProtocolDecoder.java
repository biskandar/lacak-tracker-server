/*
 * Copyright 2015 Anton Tananaev (anton.tananaev@gmail.com)
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
import java.util.TimeZone;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.helper.BcdUtil;
import org.traccar.helper.BitUtil;
import org.traccar.helper.Checksum;
import org.traccar.helper.DateBuilder;
import org.traccar.helper.UnitsConverter;
import org.traccar.model.Position;

public class HuabaoProtocolDecoder extends BaseProtocolDecoder {
  
  public HuabaoProtocolDecoder(HuabaoProtocol protocol) {
    super(protocol);
  }
  
  public static final int MSG_GENERAL_RESPONSE = 0x8001;
  public static final int MSG_TERMINAL_REGISTER = 0x0100;
  public static final int MSG_TERMINAL_REGISTER_RESPONSE = 0x8100;
  public static final int MSG_TERMINAL_AUTH = 0x0102;
  public static final int MSG_LOCATION_REPORT = 0x0200;
  
  public static final int RESULT_SUCCESS = 0;
  
  private void sendResponse(Channel channel, SocketAddress remoteAddress,
      int type, ChannelBuffer id, ChannelBuffer data) {
    ChannelBuffer response = ChannelBuffers.dynamicBuffer();
    response.writeByte(0x7e);
    response.writeShort(type);
    response.writeShort(data.readableBytes());
    response.writeBytes(id);
    response.writeShort(1); // index
    response.writeBytes(data);
    response.writeByte(Checksum.xor(response.toByteBuffer(1,
        response.readableBytes() - 1)));
    response.writeByte(0x7e);
    if (channel != null) {
      channel.write(response, remoteAddress);
    }
  }
  
  private void sendGeneralResponse(Channel channel,
      SocketAddress remoteAddress, ChannelBuffer id, int type, int index) {
    ChannelBuffer response = ChannelBuffers.dynamicBuffer();
    response.writeShort(index);
    response.writeShort(type);
    response.writeByte(RESULT_SUCCESS);
    sendResponse(channel, remoteAddress, MSG_GENERAL_RESPONSE, id, response);
  }
  
  @Override
  protected Object decode(Channel channel, SocketAddress remoteAddress,
      Object msg) throws Exception {
    
    ChannelBuffer buf = (ChannelBuffer) msg;
    
    buf.readUnsignedByte(); // start marker
    int type = buf.readUnsignedShort();
    buf.readUnsignedShort(); // body length
    ChannelBuffer id = buf.readBytes(6); // phone number
    int index = buf.readUnsignedShort();
    
    if (!identify(ChannelBuffers.hexDump(id), channel, remoteAddress)) {
      return null;
    }
    
    if (type == MSG_TERMINAL_REGISTER) {
      
      ChannelBuffer response = ChannelBuffers.dynamicBuffer();
      response.writeShort(index);
      response.writeByte(RESULT_SUCCESS);
      response.writeBytes("authentication".getBytes(StandardCharsets.US_ASCII));
      sendResponse(channel, remoteAddress, MSG_TERMINAL_REGISTER_RESPONSE, id,
          response);
      
    } else if (type == MSG_TERMINAL_AUTH) {
      
      sendGeneralResponse(channel, remoteAddress, id, type, index);
      
    } else if (type == MSG_LOCATION_REPORT) {
      
      Position position = new Position();
      position.setProtocol(getProtocolName());
      position.setDeviceId(getDeviceId());
      
      position.set(Position.KEY_ALARM, buf.readUnsignedInt());
      
      int flags = buf.readInt();
      
      position.set(Position.KEY_IGNITION, BitUtil.check(flags, 0));
      
      position.setValid(BitUtil.check(flags, 1));
      
      double lat = buf.readUnsignedInt() * 0.000001;
      double lon = buf.readUnsignedInt() * 0.000001;
      
      if (BitUtil.check(flags, 2)) {
        position.setLatitude(-lat);
      } else {
        position.setLatitude(lat);
      }
      
      if (BitUtil.check(flags, 3)) {
        position.setLongitude(-lon);
      } else {
        position.setLongitude(lon);
      }
      
      position.setAltitude(buf.readShort());
      position
          .setSpeed(UnitsConverter.knotsFromKph(buf.readUnsignedShort() * 0.1));
      position.setCourse(buf.readUnsignedShort());
      
      DateBuilder dateBuilder = new DateBuilder(TimeZone.getTimeZone("GMT+8"))
          .setYear(BcdUtil.readInteger(buf, 2))
          .setMonth(BcdUtil.readInteger(buf, 2))
          .setDay(BcdUtil.readInteger(buf, 2))
          .setHour(BcdUtil.readInteger(buf, 2))
          .setMinute(BcdUtil.readInteger(buf, 2))
          .setSecond(BcdUtil.readInteger(buf, 2));
      position.setTime(dateBuilder.getDate());
      
      // additional information
      
      return position;
      
    }
    
    return null;
  }
  
}
