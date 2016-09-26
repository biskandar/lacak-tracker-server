package org.traccar.protocol;

import java.net.SocketAddress;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.helper.Log;
import org.traccar.model.Position;

import com.google.common.base.Charsets;

public class G15cProtocolDecoder extends BaseProtocolDecoder {
  
  private Object trafficLock = new Object();
  private SimpleDateFormat dtmFormatter = new SimpleDateFormat("HHmmssddMMyy");
  
  public G15cProtocolDecoder(G15cProtocol protocol) {
    super(protocol);
  }
  
  @Override
  protected Object decode(Channel channel, SocketAddress remoteAddress,
      Object msg) throws Exception {
    Object result = null;
    
    // prepare message as channel buffer
    ChannelBuffer buf = (ChannelBuffer) msg;
    
    // header log
    String headerLog = Log.header(channel).concat(
        "[" + getProtocolName() + "] ");
    
    // convert buffer into string
    String str = buf.toString(Charsets.US_ASCII);
    
    // make sure it is clean string
    if ((str == null) || (str.equals(""))) {
      return result;
    }
    
    // log first
    Log.debug(headerLog + "G15cProtocolDecoder decode string : " + str);
    
    // split
    String[] arr = str.split(",", -1);
    
    if (arr.length < 3) {
      Log.warning(headerLog + "G15cProtocolDecoder failed to decode "
          + ", found invalid string");
      return result;
    }
    
    // manufacture
    String manufacture = arr[0];
    if ((manufacture == null) || (manufacture.equals(""))) {
      Log.warning(headerLog + "G15cProtocolDecoder failed to decode "
          + ", found empty manufacture");
      return result;
    }
    
    // imei
    String imei = arr[1];
    if ((imei == null) || (imei.equals(""))) {
      Log.warning(headerLog + "G15cProtocolDecoder failed to decode "
          + ", found empty imei");
      return result;
    }
    
    // version
    String version = arr[2];
    if ((version == null) || (version.equals(""))) {
      Log.warning(headerLog + "G15cProtocolDecoder failed to decode "
          + ", found empty command");
      return result;
    }
    
    try {
      
      // decode based on version :
      
      if (version.equalsIgnoreCase("V1")) {
        result = decodeV1(headerLog, channel, remoteAddress, arr, manufacture,
            imei);
      }
      
    } catch (Exception e) {
      Log.warning(headerLog + "G15cProtocolDecoder failed to decode , " + e);
      return result;
    }
    
    return result;
  }
  
  private Object decodeV1(String headerLog, Channel channel,
      SocketAddress remoteAddress, String[] arr, String manufacture, String imei)
      throws Exception {
    
    // device time
    String deviceTime = arr[3];
    String deviceDate = arr[11];
    Date deviceDtm = dtmFormatter.parse(deviceTime.concat(deviceDate));
    
    // data significance
    String dataSignificance = arr[4];
    boolean valid = dataSignificance.equalsIgnoreCase("A");
    
    // latitude
    String latitudeStr = arr[5];
    String latitudeDegree = latitudeStr.substring(0, 2);
    String latitudeMins = latitudeStr.substring(2);
    String latitudeFlag = arr[6];
    double latitude = Integer.parseInt(latitudeDegree);
    latitude += Double.parseDouble(latitudeMins) / 60.0;
    latitude *= latitudeFlag.equalsIgnoreCase("N") ? 1 : -1;
    
    // longitude
    String longitudeStr = arr[7];
    String longitudeDegree = longitudeStr.substring(0, 3);
    String longitudeMins = longitudeStr.substring(3);
    String longitudeFlag = arr[8];
    double longitude = Integer.parseInt(longitudeDegree);
    longitude += Double.parseDouble(longitudeMins) / 60.0;
    longitude *= longitudeFlag.equalsIgnoreCase("E") ? 1 : -1;
    
    // speed
    String speedStr = arr[9];
    double speedDbl = Double.parseDouble(speedStr);
    
    // direction
    String direction = arr[10];
    
    // vehicle status
    String vehicleStatus = arr[12];
    
    // log decode
    Log.debug(headerLog + "G15cProtocolDecoder decode v1 : manufacture = "
        + manufacture + " , imei = " + imei + " , deviceTime = " + deviceTime
        + ":" + deviceDate + ":" + deviceDtm.getTime()
        + " , dataSignificance = " + dataSignificance + " , latitude = "
        + latitudeDegree + ":" + latitudeMins + ":" + latitudeFlag + ":"
        + latitude + " , longitude = " + longitudeDegree + ":" + longitudeMins
        + ":" + longitudeFlag + ":" + longitude + " , speed = " + speedStr
        + ":" + speedDbl + " , direction = " + direction + " , valid = "
        + valid + " , vehicleStatus = " + vehicleStatus);
    
    // register device and store position
    synchronized (trafficLock) {
      if (identify(imei, channel, remoteAddress)) {
        Position position = new Position();
        position.setDeviceId(getDeviceId());
        position.setProtocol(getProtocolName());
        position.setTime(deviceDtm);
        position.setSpeed(speedDbl);
        position.setCourse(0);
        position.setValid(valid);
        position.setLatitude(latitude);
        position.setLongitude(longitude);
        return position;
      }
    }
    
    return null;
  }
  
}
