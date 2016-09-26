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

import java.util.List;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelPipeline;
import org.traccar.BaseProtocol;
import org.traccar.TrackerServer;
import org.traccar.helper.Checksum;
import org.traccar.model.Command;

public class Gt06Protocol extends BaseProtocol {
  
  public Gt06Protocol() {
    super("gt06");
    setSupportedCommands(Command.TYPE_ENGINE_STOP, Command.TYPE_ENGINE_RESUME);
  }
  
  @Override
  public void initTrackerServers(List<TrackerServer> serverList) {
    
    serverList.add(new TrackerServer(new ServerBootstrap(), this.getName()) {
      @Override
      protected void addSpecificHandlers(ChannelPipeline pipeline) {
        pipeline.addLast("objectForwarder", new Gt06ProtocolForwarder(
            Gt06Protocol.this));
        pipeline.addLast("frameDecoder", new Gt06FrameDecoder());
        pipeline.addLast("objectEncoder", new Gt06ProtocolEncoder());
        pipeline.addLast("objectDecoder", new Gt06ProtocolDecoder(
            Gt06Protocol.this));
      }
    });
    
  }
  
  public static final int MSG_LOGIN = 0x01;
  public static final int MSG_GPS = 0x10;
  public static final int MSG_LBS = 0x11;
  public static final int MSG_GPS_LBS_1 = 0x12;
  public static final int MSG_GPS_LBS_2 = 0x22;
  public static final int MSG_STATUS = 0x13;
  public static final int MSG_SATELLITE = 0x14;
  public static final int MSG_STRING = 0x15;
  public static final int MSG_GPS_LBS_STATUS_1 = 0x16;
  public static final int MSG_GPS_LBS_STATUS_2 = 0x26;
  public static final int MSG_GPS_LBS_STATUS_3 = 0x27;
  public static final int MSG_LBS_PHONE = 0x17;
  public static final int MSG_LBS_EXTEND = 0x18;
  public static final int MSG_LBS_STATUS = 0x19;
  public static final int MSG_GPS_PHONE = 0x1A;
  public static final int MSG_GPS_LBS_EXTEND = 0x1E;
  public static final int MSG_COMMAND_0 = 0x80;
  public static final int MSG_COMMAND_1 = 0x81;
  public static final int MSG_COMMAND_2 = 0x82;
  public static final int MSG_INFO = 0x94;
  
  public static boolean isSupported(int type) {
    return hasGps(type) || hasLbs(type) || hasStatus(type);
  }
  
  public static boolean hasGps(int type) {
    return type == MSG_GPS || type == MSG_GPS_LBS_1 || type == MSG_GPS_LBS_2
        || type == MSG_GPS_LBS_STATUS_1 || type == MSG_GPS_LBS_STATUS_2
        || type == MSG_GPS_LBS_STATUS_3 || type == MSG_GPS_PHONE
        || type == MSG_GPS_LBS_EXTEND;
  }
  
  public static boolean hasLbs(int type) {
    return type == MSG_LBS || type == MSG_LBS_STATUS || type == MSG_GPS_LBS_1
        || type == MSG_GPS_LBS_2 || type == MSG_GPS_LBS_STATUS_1
        || type == MSG_GPS_LBS_STATUS_2 || type == MSG_GPS_LBS_STATUS_3;
  }
  
  public static boolean hasStatus(int type) {
    return type == MSG_STATUS || type == MSG_LBS_STATUS
        || type == MSG_GPS_LBS_STATUS_1 || type == MSG_GPS_LBS_STATUS_2
        || type == MSG_GPS_LBS_STATUS_3;
  }
  
  public static boolean isCommand(int type) {
    return type == MSG_COMMAND_0 || type == MSG_COMMAND_1
        || type == MSG_COMMAND_2;
  }
  
  public static ChannelBuffer createResponse(int type, int index) {
    ChannelBuffer response = ChannelBuffers.dynamicBuffer();
    response.writeByte(0x78);
    response.writeByte(0x78); // header
    response.writeByte(5); // size
    response.writeByte(type);
    response.writeShort(index);
    response.writeShort(Checksum.crc16(Checksum.CRC16_X25,
        response.toByteBuffer(2, 4)));
    response.writeByte(0x0D);
    response.writeByte(0x0A); // ending
    return response;
  }
  
  public static String readCommandText(ChannelBuffer channelBuffer) {
    String commandText = null;
    try {
      
      if (channelBuffer == null) {
        return commandText;
      }
      
      if (channelBuffer.readShort() != (short) 0x7878) {
        return commandText;
      }
      
      int messageLength = channelBuffer.readByte();
      // System.out.println("messageLength = " +
      // Integer.toHexString(messageLength));
      
      if (channelBuffer.readByte() != (byte) 0x80) {
        return commandText;
      }
      
      int commandLength = channelBuffer.readByte() - 4;
      // System.out.println("commandLength = " + commandLength);
      
      if (commandLength < 1) {
        return commandText;
      }
      
      int serverFlagBits = channelBuffer.readInt();
      // System.out.println("serverFlagBits = " +
      // Integer.toHexString(serverFlagBits));
      
      StringBuilder sb = new StringBuilder();
      for (int idx = 0; idx < commandLength; idx++) {
        sb.append((char) channelBuffer.readByte());
      }
      commandText = sb.toString();
      
    } catch (Exception e) {
    }
    return commandText;
  }
  
}
