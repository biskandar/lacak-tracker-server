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
import java.util.regex.Pattern;

import org.jboss.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.helper.DateBuilder;
import org.traccar.helper.Parser;
import org.traccar.helper.PatternBuilder;
import org.traccar.helper.UnitsConverter;
import org.traccar.model.Position;

public class WatchProtocolDecoder extends BaseProtocolDecoder {
  
  public WatchProtocolDecoder(WatchProtocol protocol) {
    super(protocol);
  }
  
  private static final Pattern PATTERN = new PatternBuilder().text("[")
      .expression("(..)").text("*") // manufacturer
      .number("(d+)").text("*") // equipment id
      .number("xxxx").text("*") // length
      .expression("([^,]+)") // type
      .expression("(.*)") // content
      .compile();
  
  private static final Pattern PATTERN_POSITION = new PatternBuilder()
      .text(",").number("(dd)(dd)(dd),") // date (ddmmyy)
      .number("(dd)(dd)(dd),") // time
      .expression("([AV]),") // validity
      .number(" *-?(d+.d+),") // latitude
      .expression("([NS]),").number(" *-?(d+.d+),") // longitude
      .expression("([EW])?,").number("(d+.d+),") // speed
      .number("(d+.?d*),") // course
      .number("(d+.?d*),") // altitude
      .number("(d+),") // satellites
      .number("(d+),") // gsm
      .number("(d+),") // battery
      .number("(d+),") // steps
      .number("d+,") // tumbles
      .any().compile();
  
  private void sendResponse(Channel channel, String manufacturer, String id,
      String content) {
    if (channel != null) {
      channel.write(String.format("[%s*%s*%04x*%s]", manufacturer, id,
          content.length(), content));
    }
  }
  
  @Override
  protected Object decode(Channel channel, SocketAddress remoteAddress,
      Object msg) throws Exception {
    
    Parser parser = new Parser(PATTERN, (String) msg);
    if (!parser.matches()) {
      return null;
    }
    
    String manufacturer = parser.next();
    String id = parser.next();
    if (!identify(id, channel, remoteAddress)) {
      return null;
    }
    
    String type = parser.next();
    String content = parser.next();
    
    if (type.equals("LK")) {
      
      sendResponse(channel, manufacturer, id, "LK");
      
      if (!content.isEmpty()) {
        String[] values = content.split(",");
        if (values.length >= 4) {
          Position position = new Position();
          position.setProtocol(getProtocolName());
          position.setDeviceId(getDeviceId());
          
          getLastLocation(position, null);
          
          position.set(Position.KEY_BATTERY, values[3]);
          
          return position;
        }
      }
      
    } else if (type.equals("UD") || type.equals("UD2") || type.equals("AL")) {
      
      if (type.equals("AL")) {
        sendResponse(channel, manufacturer, id, "AL");
      }
      
      parser = new Parser(PATTERN_POSITION, content);
      if (!parser.matches()) {
        return null;
      }
      
      Position position = new Position();
      position.setProtocol(getProtocolName());
      position.setDeviceId(getDeviceId());
      
      DateBuilder dateBuilder = new DateBuilder().setDateReverse(
          parser.nextInt(), parser.nextInt(), parser.nextInt()).setTime(
          parser.nextInt(), parser.nextInt(), parser.nextInt());
      position.setTime(dateBuilder.getDate());
      
      position.setValid(parser.next().equals("A"));
      position.setLatitude(parser
          .nextCoordinate(Parser.CoordinateFormat.DEG_HEM));
      position.setLongitude(parser
          .nextCoordinate(Parser.CoordinateFormat.DEG_HEM));
      position.setSpeed(UnitsConverter.knotsFromKph(parser.nextDouble()));
      position.setCourse(parser.nextDouble());
      position.setAltitude(parser.nextDouble());
      
      position.set(Position.KEY_SATELLITES, parser.nextInt());
      position.set(Position.KEY_GSM, parser.nextInt());
      position.set(Position.KEY_BATTERY, parser.nextInt());
      
      position.set("steps", parser.nextInt());
      
      return position;
      
    } else if (type.equals("TKQ")) {
      
      sendResponse(channel, manufacturer, id, "TKQ");
      
    }
    
    return null;
  }
  
}
