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
package org.traccar.protocol;

import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.TimeZone;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.Context;
import org.traccar.helper.BitUtil;
import org.traccar.helper.DateBuilder;
import org.traccar.helper.UnitsConverter;
import org.traccar.model.Position;

public class Gt06ProtocolDecoder extends BaseProtocolDecoder {
  
  private boolean forceTimeZone = false;
  private final TimeZone timeZone = TimeZone.getTimeZone("UTC");
  
  public Gt06ProtocolDecoder(Gt06Protocol protocol) {
    super(protocol);
    
    if (Context.getConfig().hasKey(getProtocolName() + ".timezone")) {
      forceTimeZone = true;
      timeZone.setRawOffset(Context.getConfig().getInteger(
          getProtocolName() + ".timezone") * 1000);
    }
  }
  
  private void decodeGps(Position position, ChannelBuffer buf) {
    
    DateBuilder dateBuilder = new DateBuilder(timeZone).setDate(
        buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedByte())
        .setTime(buf.readUnsignedByte(), buf.readUnsignedByte(),
            buf.readUnsignedByte());
    position.setTime(dateBuilder.getDate());
    
    int length = buf.readUnsignedByte();
    position.set(Position.KEY_SATELLITES, BitUtil.to(length, 4));
    length = BitUtil.from(length, 4);
    
    double latitude = buf.readUnsignedInt() / 60.0 / 30000.0;
    double longitude = buf.readUnsignedInt() / 60.0 / 30000.0;
    position.setSpeed(UnitsConverter.knotsFromKph(buf.readUnsignedByte()));
    
    int flags = buf.readUnsignedShort();
    position.setCourse(BitUtil.to(flags, 10));
    position.setValid(BitUtil.check(flags, 12));
    
    if (!BitUtil.check(flags, 10)) {
      latitude = -latitude;
    }
    if (BitUtil.check(flags, 11)) {
      longitude = -longitude;
    }
    
    position.setLatitude(latitude);
    position.setLongitude(longitude);
    
    if (BitUtil.check(flags, 14)) {
      position.set(Position.KEY_IGNITION, BitUtil.check(flags, 15));
    }
    
    buf.skipBytes(length - 12); // skip reserved
  }
  
  private void decodeLbs(Position position, ChannelBuffer buf, boolean hasLength) {
    
    int lbsLength = 0;
    if (hasLength) {
      lbsLength = buf.readUnsignedByte();
    }
    
    position.set(Position.KEY_MCC, buf.readUnsignedShort());
    position.set(Position.KEY_MNC, buf.readUnsignedByte());
    position.set(Position.KEY_LAC, buf.readUnsignedShort());
    position.set(Position.KEY_CID, buf.readUnsignedMedium());
    
    if (lbsLength > 0) {
      buf.skipBytes(lbsLength - 9);
    }
  }
  
  private void decodeStatus(Position position, ChannelBuffer buf) {
    
    position.set(Position.KEY_ALARM, true);
    
    int flags = buf.readUnsignedByte();
    
    position.set(Position.KEY_IGNITION, BitUtil.check(flags, 1));
    position.set(Position.KEY_STATUS, flags);
    position.set(Position.KEY_POWER, buf.readUnsignedByte());
    position.set(Position.KEY_GSM, buf.readUnsignedByte());
  }
  
  @Override
  protected Object decode(Channel channel, SocketAddress remoteAddress,
      Object msg) throws Exception {
    
    ChannelBuffer buf = (ChannelBuffer) msg;
    
    int header = buf.readShort();
    
    if (header == 0x7878) {
      
      int length = buf.readUnsignedByte();
      int dataLength = length - 5;
      
      int type = buf.readUnsignedByte();
      
      if (type == Gt06Protocol.MSG_LOGIN) {
        
        String imei = ChannelBuffers.hexDump(buf.readBytes(8)).substring(1);
        buf.readUnsignedShort(); // type
        
        // Timezone offset
        if (dataLength > 10) {
          int extensionBits = buf.readUnsignedShort();
          int hours = (extensionBits >> 4) / 100;
          int minutes = (extensionBits >> 4) % 100;
          int offset = (hours * 60 + minutes) * 60;
          if ((extensionBits & 0x8) != 0) {
            offset = -offset;
          }
          if (!forceTimeZone) {
            timeZone.setRawOffset(offset * 1000);
          }
        }
        
        if (identify(imei, channel, remoteAddress)) {
          buf.skipBytes(buf.readableBytes() - 6);
          channel.write(Gt06Protocol.createResponse(type,
              buf.readUnsignedShort()));
        }
        
      } else if (hasDeviceId()) {
        
        if (type == Gt06Protocol.MSG_STRING) {
          
          Position position = new Position();
          position.setDeviceId(getDeviceId());
          position.setProtocol(getProtocolName());
          
          getLastLocation(position, null);
          
          int commandLength = buf.readUnsignedByte();
          
          if (commandLength > 0) {
            buf.readUnsignedByte(); // server flag (reserved)
            position.set(
                "command",
                buf.readBytes(commandLength - 1).toString(
                    StandardCharsets.US_ASCII));
          }
          
          buf.readUnsignedShort(); // language
          
          channel.write(Gt06Protocol.createResponse(type,
              buf.readUnsignedShort()));
          
          return position;
          
        } else if (Gt06Protocol.isSupported(type)) {
          
          Position position = new Position();
          position.setDeviceId(getDeviceId());
          position.setProtocol(getProtocolName());
          
          if (Gt06Protocol.hasGps(type)) {
            decodeGps(position, buf);
          } else {
            getLastLocation(position, null);
          }
          
          if (Gt06Protocol.hasLbs(type)) {
            decodeLbs(position, buf, Gt06Protocol.hasStatus(type));
          }
          
          if (Gt06Protocol.hasStatus(type)) {
            decodeStatus(position, buf);
          }
          
          if (type == Gt06Protocol.MSG_GPS_LBS_1
              && buf.readableBytes() == 4 + 6) {
            position.set(Position.KEY_ODOMETER, buf.readUnsignedInt());
          }
          
          if (buf.readableBytes() > 6) {
            buf.skipBytes(buf.readableBytes() - 6);
          }
          int index = buf.readUnsignedShort();
          position.set(Position.KEY_INDEX, index);
          channel.write(Gt06Protocol.createResponse(type, index));
          
          return position;
          
        } else {
          
          buf.skipBytes(dataLength);
          if (type != Gt06Protocol.MSG_COMMAND_0
              && type != Gt06Protocol.MSG_COMMAND_1
              && type != Gt06Protocol.MSG_COMMAND_2) {
            channel.write(Gt06Protocol.createResponse(type,
                buf.readUnsignedShort()));
          }
          
        }
        
      }
      
    } else if (header == 0x7979) {
      
      buf.readUnsignedShort(); // length
      int type = buf.readUnsignedByte();
      
      if (type == Gt06Protocol.MSG_INFO) {
        int subType = buf.readUnsignedByte();
        
        if (subType == 0x05) {
          
          Position position = new Position();
          position.setDeviceId(getDeviceId());
          position.setProtocol(getProtocolName());
          
          getLastLocation(position, null);
          
          int flags = buf.readUnsignedByte();
          
          position.set("door", BitUtil.check(flags, 0));
          position.set(Position.PREFIX_IO + 1, BitUtil.check(flags, 2));
          
          return position;
          
        }
      }
      
    }
    
    return null;
  }
}
