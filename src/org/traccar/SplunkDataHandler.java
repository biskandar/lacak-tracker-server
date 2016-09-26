package org.traccar;

import java.util.HashMap;
import java.util.Map;

import org.traccar.helper.Log;
import org.traccar.model.Device;
import org.traccar.model.MiscFormatter;
import org.traccar.model.Position;

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.Response;

public class SplunkDataHandler extends BaseDataHandler {
  
  private final String url;
  private final String token;
  private final String host;
  private final String source;
  private final String sourceType;
  private final String index;
  
  public SplunkDataHandler(String url, String token, String host,
      String source, String sourceType, String index) {
    this.url = url;
    this.token = token;
    this.host = host;
    this.source = source;
    this.sourceType = sourceType;
    this.index = index;
  }
  
  public String formatRequest(Position position) {
    Device device = Context.getIdentityManager().getDeviceById(
        position.getDeviceId());
    
    Map<String, Object> eventAttributes = new HashMap<>();
    eventAttributes.put("id", device.getUniqueId());
    eventAttributes.put("protocol", String.valueOf(position.getProtocol()));
    eventAttributes.put("latitude", String.valueOf(position.getLatitude()));
    eventAttributes.put("longitude", String.valueOf(position.getLongitude()));
    eventAttributes.put("altitude", String.valueOf(position.getAltitude()));
    eventAttributes.put("speed", String.valueOf(position.getSpeed()));
    eventAttributes.put("course", String.valueOf(position.getCourse()));
    eventAttributes.putAll(position.getAttributes());
    
    Map<String, Object> mainAttributes = new HashMap<>();
    mainAttributes.put("host", host);
    mainAttributes.put("source", source);
    mainAttributes.put("sourcetype", sourceType);
    mainAttributes.put("index", index);
    mainAttributes.put("event", MiscFormatter.toJsonString(eventAttributes));
    
    return MiscFormatter.toJsonString(mainAttributes);
  }
  
  @Override
  protected Position handlePosition(Position position) {
    
    final String body = formatRequest(position);
    
    Context.getAsyncHttpClient().preparePost(url)
        .addHeader("Content-Type", "application/json")
        .addHeader("Authorization", "Splunk ".concat(token)).setBody(body)
        .execute(new AsyncCompletionHandler<Response>() {
          @Override
          public Response onCompleted(Response response) throws Exception {
            Log.debug("[SplunkDataHandler] Handled position : request.url = "
                + url + " , request.body = " + body
                + " , response.statusCode = " + response.getStatusCode()
                + " , response.body = " + response.getResponseBody());
            return response;
          }
          
          @Override
          public void onThrowable(Throwable throwable) {
            Log.debug("[SplunkDataHandler] Handled position : request.url = "
                + url + " , request.body = " + body + " , throwable = "
                + throwable);
          }
        });
    
    return position;
  }
  
}
