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
package org.traccar.notification;

import java.sql.SQLException;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.traccar.Config;
import org.traccar.Context;
import org.traccar.database.DataManager;
import org.traccar.helper.Log;
import org.traccar.model.Event;
import org.traccar.model.Extensible;
import org.traccar.model.Position;
import org.traccar.model.User;

public final class NotificationMail {
  
  private NotificationMail() {
  }
  
  private static Properties getConfigProperies() {
    Config config = Context.getConfig();
    Properties result = new Properties();
    String host = config.getString("mail.smtp.host", null);
    if (host != null) {
      result.put("mail.smtp.host", host);
      result.put("mail.smtp.port", config.getString("mail.smtp.port", "25"));
      
      if (config.getBoolean("mail.smtp.starttls.enable")) {
        result.put("mail.smtp.starttls.enable",
            config.getBoolean("mail.smtp.starttls.enable"));
      } else if (config.getBoolean("mail.smtp.ssl.enable")) {
        result.put("mail.smtp.socketFactory.port",
            result.getProperty("mail.smtp.port"));
        result.put("mail.smtp.socketFactory.class",
            "javax.net.ssl.SSLSocketFactory");
      }
      
      result.put("mail.smtp.auth", config.getBoolean("mail.smtp.auth"));
      result
          .put("mail.smtp.user", config.getString("mail.smtp.username", null));
      result.put("mail.smtp.password",
          config.getString("mail.smtp.password", null));
      result.put("mail.smtp.from", config.getString("mail.smtp.from", null));
    }
    return result;
  }
  
  private static Properties getAttributesProperties(Extensible object) {
    Properties result = new Properties();
    
    if (object.getAttributes().containsKey("mail.smtp.host")) {
      result
          .put("mail.smtp.host", object.getAttributes().get("mail.smtp.host"));
      String port = (String) object.getAttributes().get("mail.smtp.port");
      result.put("mail.smtp.port", (port != null) ? port : "25");
      if (object.getAttributes().containsKey("mail.smtp.starttls.enable")) {
        boolean tls = Boolean.parseBoolean((String) object.getAttributes().get(
            "mail.smtp.starttls.enable"));
        result.put("mail.smtp.starttls.enable", tls);
      } else if (object.getAttributes().containsKey("mail.smtp.ssl.enable")) {
        boolean ssl = Boolean.parseBoolean((String) object.getAttributes().get(
            "mail.smtp.ssl.enable"));
        if (ssl) {
          result.put("mail.smtp.socketFactory.port",
              result.getProperty("mail.smtp.port"));
          result.put("mail.smtp.socketFactory.class",
              "javax.net.ssl.SSLSocketFactory");
        }
      }
      boolean auth = Boolean.parseBoolean((String) object.getAttributes().get(
          "mail.smtp.auth"));
      result.put("mail.smtp.auth", auth);
      
      result.put("mail.smtp.username",
          object.getAttributes().get("mail.smtp.username"));
      result.put("mail.smtp.password",
          object.getAttributes().get("mail.smtp.password"));
      result
          .put("mail.smtp.from", object.getAttributes().get("mail.smtp.from"));
    }
    return result;
  }
  
  public static void sendMailSync(long userId, Event event, Position position) {
    DataManager dataManager = Context.getDataManager();
    
    Properties mailServerProperties;
    Session mailSession;
    MimeMessage mailMessage;
    
    try {
      User user = dataManager.getUser(userId);
      
      mailServerProperties = getConfigProperies();
      if (!mailServerProperties.containsKey("mail.smtp.host")) {
        mailServerProperties = getAttributesProperties(user);
        if (!mailServerProperties.containsKey("mail.smtp.host")) {
          return;
        }
      }
      mailSession = Session.getDefaultInstance(mailServerProperties, null);
      
      mailMessage = new MimeMessage(mailSession);
      
      mailMessage.addRecipient(Message.RecipientType.TO, new InternetAddress(
          Context.getDataManager().getUser(userId).getEmail()));
      mailMessage.setSubject(NotificationFormatter.formatTitle(userId, event,
          position));
      mailMessage.setText(NotificationFormatter.formatMessage(userId, event,
          position));
      
      Transport transport = mailSession.getTransport("smtp");
      transport.connect(mailServerProperties.getProperty("mail.smtp.host"),
          mailServerProperties.getProperty("mail.smtp.username"),
          mailServerProperties.getProperty("mail.smtp.password"));
      transport.sendMessage(mailMessage, mailMessage.getAllRecipients());
      transport.close();
      
    } catch (MessagingException | SQLException error) {
      Log.warning(error);
    }
  }
  
  public static void sendMailAsync(final long userId, final Event event,
      final Position position) {
    Runnable runnableSend = new Runnable() {
      public void run() {
        sendMailSync(userId, event, position);
      }
    };
    
    new Thread(runnableSend).start();
  }
}
