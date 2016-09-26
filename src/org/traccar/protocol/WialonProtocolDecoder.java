/*
 * Copyright 2013 - 2014 Anton Tananaev (anton.tananaev@gmail.com)
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
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.helper.DateBuilder;
import org.traccar.helper.Parser;
import org.traccar.helper.PatternBuilder;
import org.traccar.model.Position;

public class WialonProtocolDecoder extends BaseProtocolDecoder {
  
  public WialonProtocolDecoder(WialonProtocol protocol) {
    super(protocol);
  }
  
  private static final Pattern PATTERN = new PatternBuilder()
      .number("(dd)(dd)(dd);") // date (ddmmyy)
      .number("(dd)(dd)(dd);") // time
      .number("(dd)(dd.d+);") // latitude
      .expression("([NS]);").number("(ddd)(dd.d+);") // longitude
      .expression("([EW]);").number("(d+.?d*)?;") // speed
      .number("(d+.?d*)?;") // course
      .number("(?:NA|(d+.?d*));") // altitude
      .number("(?:NA|(d+))") // satellites
      .groupBegin().text(";").number("(?:NA|(d+.?d*));") // hdop
      .number("(?:NA|(d+));") // inputs
      .number("(?:NA|(d+));") // outputs
      .expression("(?:NA|([^;]*));") // adc
      .expression("(?:NA|([^;]*));") // ibutton
      .expression("(?:NA|(.*))") // params
      .groupEnd("?").compile();
  
  private void sendResponse(Channel channel, String prefix, Integer number) {
    if (channel != null) {
      StringBuilder response = new StringBuilder(prefix);
      if (number != null) {
        response.append(number);
      }
      response.append("\r\n");
      channel.write(response.toString());
    }
  }
  
  private Position decodePosition(String substring) {
    
    Parser parser = new Parser(PATTERN, substring);
    if (!hasDeviceId() || !parser.matches()) {
      return null;
    }
    
    Position position = new Position();
    position.setProtocol(getProtocolName());
    position.setDeviceId(getDeviceId());
    
    DateBuilder dateBuilder = new DateBuilder().setDateReverse(
        parser.nextInt(), parser.nextInt(), parser.nextInt()).setTime(
        parser.nextInt(), parser.nextInt(), parser.nextInt());
    position.setTime(dateBuilder.getDate());
    
    position.setLatitude(parser.nextCoordinate());
    position.setLongitude(parser.nextCoordinate());
    position.setSpeed(parser.nextDouble());
    position.setCourse(parser.nextDouble());
    position.setAltitude(parser.nextDouble());
    
    if (parser.hasNext()) {
      int satellites = parser.nextInt();
      position.setValid(satellites >= 3);
      position.set(Position.KEY_SATELLITES, satellites);
    }
    
    position.set(Position.KEY_HDOP, parser.next());
    position.set(Position.KEY_INPUT, parser.next());
    position.set(Position.KEY_OUTPUT, parser.next());
    
    if (parser.hasNext()) {
      String[] values = parser.next().split(",");
      for (int i = 0; i < values.length; i++) {
        position.set(Position.PREFIX_ADC + (i + 1), values[i]);
      }
    }
    
    position.set(Position.KEY_RFID, parser.next());
    
    if (parser.hasNext()) {
      String[] values = parser.next().split(",");
      for (String param : values) {
        Matcher paramParser = Pattern.compile("(.*):[1-3]:(.*)").matcher(param);
        if (paramParser.matches()) {
          position
              .set(paramParser.group(1).toLowerCase(), paramParser.group(2));
        }
      }
    }
    
    return position;
  }
  
  @Override
  protected Object decode(Channel channel, SocketAddress remoteAddress,
      Object msg) throws Exception {
    
    String sentence = (String) msg;
    
    if (sentence.startsWith("#L#")) {
      
      String imei = sentence.substring(3, sentence.indexOf(';'));
      if (identify(imei, channel, remoteAddress)) {
        sendResponse(channel, "#AL#", 1);
      }
      
    } else if (sentence.startsWith("#P#")) {
      
      sendResponse(channel, "#AP#", null); // heartbeat
      
    } else if (sentence.startsWith("#SD#") || sentence.startsWith("#D#")) {
      
      Position position = decodePosition(sentence.substring(sentence.indexOf(
          '#', 1) + 1));
      
      if (position != null) {
        sendResponse(channel, "#AD#", 1);
        return position;
      }
      
    } else if (sentence.startsWith("#B#")) {
      
      String[] messages = sentence.substring(sentence.indexOf('#', 1) + 1)
          .split("\\|");
      List<Position> positions = new LinkedList<>();
      
      for (String message : messages) {
        Position position = decodePosition(message);
        if (position != null) {
          positions.add(position);
        }
      }
      
      sendResponse(channel, "#AB#", messages.length);
      if (!positions.isEmpty()) {
        return positions;
      }
      
    }
    
    return null;
  }
  
}
