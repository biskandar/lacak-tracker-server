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
package org.traccar.database;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.naming.InitialContext;
import javax.sql.DataSource;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.exception.LiquibaseException;
import liquibase.resource.FileSystemResourceAccessor;
import liquibase.resource.ResourceAccessor;

import org.traccar.Config;
import org.traccar.Context;
import org.traccar.helper.Log;
import org.traccar.model.Device;
import org.traccar.model.DeviceGeofence;
import org.traccar.model.DevicePermission;
import org.traccar.model.Event;
import org.traccar.model.Geofence;
import org.traccar.model.GeofencePermission;
import org.traccar.model.Group;
import org.traccar.model.GroupGeofence;
import org.traccar.model.GroupPermission;
import org.traccar.model.Notification;
import org.traccar.model.Position;
import org.traccar.model.Server;
import org.traccar.model.User;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DataManager implements IdentityManager {
  
  private static final long DEFAULT_REFRESH_DELAY = 300;
  
  private final Config config;
  
  private DataSource dataSource;
  
  private final long dataRefreshDelay;
  
  private final ReadWriteLock devicesLock = new ReentrantReadWriteLock();
  private final Map<Long, Device> devicesById = new HashMap<>();
  private final Map<String, Device> devicesByUniqueId = new HashMap<>();
  private long devicesLastUpdate;
  
  private final ReadWriteLock groupsLock = new ReentrantReadWriteLock();
  private final Map<Long, Group> groupsById = new HashMap<>();
  private long groupsLastUpdate;
  
  public DataManager(Config config) throws Exception {
    this.config = config;
    
    initDatabase();
    initDatabaseSchema();
    
    dataRefreshDelay = config.getLong("database.refreshDelay",
        DEFAULT_REFRESH_DELAY) * 1000;
  }
  
  public DataSource getDataSource() {
    return dataSource;
  }
  
  private void initDatabase() throws Exception {
    
    String jndiName = config.getString("database.jndi");
    
    if (jndiName != null) {
      
      dataSource = (DataSource) new InitialContext().lookup(jndiName);
      
    } else {
      
      String driverFile = config.getString("database.driverFile");
      if (driverFile != null) {
        URLClassLoader classLoader = (URLClassLoader) ClassLoader
            .getSystemClassLoader();
        Method method = URLClassLoader.class.getDeclaredMethod("addURL",
            URL.class);
        method.setAccessible(true);
        method.invoke(classLoader, new File(driverFile).toURI().toURL());
      }
      
      String driver = config.getString("database.driver");
      if (driver != null) {
        Class.forName(driver);
      }
      
      HikariConfig hikariConfig = new HikariConfig();
      hikariConfig.setDriverClassName(config.getString("database.driver"));
      hikariConfig.setJdbcUrl(config.getString("database.url"));
      hikariConfig.setUsername(config.getString("database.user"));
      hikariConfig.setPassword(config.getString("database.password"));
      hikariConfig.setConnectionInitSql(config.getString(
          "database.checkConnection", "SELECT 1"));
      hikariConfig.setIdleTimeout(600000);
      
      int maxPoolSize = config.getInteger("database.maxPoolSize");
      
      if (maxPoolSize != 0) {
        hikariConfig.setMaximumPoolSize(maxPoolSize);
      }
      
      dataSource = new HikariDataSource(hikariConfig);
      
    }
  }
  
  private void updateDeviceCache(boolean force) throws SQLException {
    boolean needWrite;
    devicesLock.readLock().lock();
    try {
      needWrite = force
          || System.currentTimeMillis() - devicesLastUpdate > dataRefreshDelay;
    } finally {
      devicesLock.readLock().unlock();
    }
    
    if (needWrite) {
      devicesLock.writeLock().lock();
      try {
        if (force
            || System.currentTimeMillis() - devicesLastUpdate > dataRefreshDelay) {
          devicesById.clear();
          devicesByUniqueId.clear();
          ConnectionManager connectionManager = Context.getConnectionManager();
          GeofenceManager geofenceManager = Context.getGeofenceManager();
          for (Device device : getAllDevices()) {
            devicesById.put(device.getId(), device);
            devicesByUniqueId.put(device.getUniqueId(), device);
            if (connectionManager != null && geofenceManager != null) {
              Position lastPosition = connectionManager.getLastPosition(device
                  .getId());
              if (lastPosition != null) {
                device.setGeofenceIds(geofenceManager
                    .getCurrentDeviceGeofences(lastPosition));
              }
            }
          }
          devicesLastUpdate = System.currentTimeMillis();
        }
      } finally {
        devicesLock.writeLock().unlock();
      }
    }
  }
  
  @Override
  public Device getDeviceById(long id) {
    boolean forceUpdate;
    devicesLock.readLock().lock();
    try {
      forceUpdate = !devicesById.containsKey(id);
    } finally {
      devicesLock.readLock().unlock();
    }
    
    try {
      updateDeviceCache(forceUpdate);
    } catch (SQLException e) {
      Log.warning(e);
    }
    
    devicesLock.readLock().lock();
    try {
      return devicesById.get(id);
    } finally {
      devicesLock.readLock().unlock();
    }
  }
  
  @Override
  public Device getDeviceByUniqueId(String uniqueId) throws SQLException {
    boolean forceUpdate;
    devicesLock.readLock().lock();
    try {
      forceUpdate = !devicesByUniqueId.containsKey(uniqueId)
          && !config.getBoolean("database.ignoreUnknown");
    } finally {
      devicesLock.readLock().unlock();
    }
    
    updateDeviceCache(forceUpdate);
    
    devicesLock.readLock().lock();
    try {
      return devicesByUniqueId.get(uniqueId);
    } finally {
      devicesLock.readLock().unlock();
    }
  }
  
  private void updateGroupCache(boolean force) throws SQLException {
    boolean needWrite;
    groupsLock.readLock().lock();
    try {
      needWrite = force
          || System.currentTimeMillis() - groupsLastUpdate > dataRefreshDelay;
    } finally {
      groupsLock.readLock().unlock();
    }
    
    if (needWrite) {
      groupsLock.writeLock().lock();
      try {
        if (force
            || System.currentTimeMillis() - groupsLastUpdate > dataRefreshDelay) {
          groupsById.clear();
          for (Group group : getAllGroups()) {
            groupsById.put(group.getId(), group);
          }
          groupsLastUpdate = System.currentTimeMillis();
        }
      } finally {
        groupsLock.writeLock().unlock();
      }
    }
  }
  
  public Group getGroupById(long id) {
    boolean forceUpdate;
    groupsLock.readLock().lock();
    try {
      forceUpdate = !groupsById.containsKey(id);
    } finally {
      groupsLock.readLock().unlock();
    }
    
    try {
      updateGroupCache(forceUpdate);
    } catch (SQLException e) {
      Log.warning(e);
    }
    
    groupsLock.readLock().lock();
    try {
      return groupsById.get(id);
    } finally {
      groupsLock.readLock().unlock();
    }
  }
  
  private String getQuery(String key) {
    String query = config.getString(key);
    if (query == null) {
      Log.info("Query not provided: " + key);
    }
    return query;
  }
  
  private void initDatabaseSchema() throws SQLException, LiquibaseException {
    
    if (config.hasKey("database.changelog")) {
      
      ResourceAccessor resourceAccessor = new FileSystemResourceAccessor();
      
      Database database = DatabaseFactory.getInstance().openDatabase(
          config.getString("database.url"), config.getString("database.user"),
          config.getString("database.password"), null, resourceAccessor);
      
      Liquibase liquibase = new Liquibase(
          config.getString("database.changelog"), resourceAccessor, database);
      
      liquibase.clearCheckSums();
      
      liquibase.update(new Contexts());
    }
  }
  
  public User login(String email, String password) throws SQLException {
    User user = QueryBuilder.create(dataSource, getQuery("database.loginUser"))
        .setString("email", email).executeQuerySingle(User.class);
    if (user != null && user.isPasswordValid(password)) {
      return user;
    } else {
      return null;
    }
  }
  
  public Collection<User> getUsers() throws SQLException {
    return QueryBuilder.create(dataSource, getQuery("database.selectUsersAll"))
        .executeQuery(User.class);
  }
  
  public User getUser(long userId) throws SQLException {
    return QueryBuilder.create(dataSource, getQuery("database.selectUser"))
        .setLong("id", userId).executeQuerySingle(User.class);
  }
  
  public void addUser(User user) throws SQLException {
    user.setId(QueryBuilder
        .create(dataSource, getQuery("database.insertUser"), true)
        .setObject(user).executeUpdate());
  }
  
  public void updateUser(User user) throws SQLException {
    QueryBuilder.create(dataSource, getQuery("database.updateUser"))
        .setObject(user).executeUpdate();
    if (user.getHashedPassword() != null) {
      QueryBuilder.create(dataSource, getQuery("database.updateUserPassword"))
          .setObject(user).executeUpdate();
    }
  }
  
  public void removeUser(long userId) throws SQLException {
    QueryBuilder.create(dataSource, getQuery("database.deleteUser"))
        .setLong("id", userId).executeUpdate();
  }
  
  public Collection<DevicePermission> getDevicePermissions()
      throws SQLException {
    return QueryBuilder.create(dataSource,
        getQuery("database.selectDevicePermissions")).executeQuery(
        DevicePermission.class);
  }
  
  public Collection<GroupPermission> getGroupPermissions() throws SQLException {
    return QueryBuilder.create(dataSource,
        getQuery("database.selectGroupPermissions")).executeQuery(
        GroupPermission.class);
  }
  
  private Collection<Device> getAllDevices() throws SQLException {
    return QueryBuilder.create(dataSource,
        getQuery("database.selectDevicesAll")).executeQuery(Device.class);
  }
  
  public Collection<Device> getAllDevicesCached() {
    boolean forceUpdate;
    devicesLock.readLock().lock();
    try {
      forceUpdate = devicesById.values().isEmpty();
    } finally {
      devicesLock.readLock().unlock();
    }
    
    try {
      updateDeviceCache(forceUpdate);
    } catch (SQLException e) {
      Log.warning(e);
    }
    
    devicesLock.readLock().lock();
    try {
      return devicesById.values();
    } finally {
      devicesLock.readLock().unlock();
    }
  }
  
  public Collection<Device> getDevices(long userId) throws SQLException {
    Collection<Device> devices = new ArrayList<>();
    for (long id : Context.getPermissionsManager().getDevicePermissions(userId)) {
      devices.add(getDeviceById(id));
    }
    return devices;
  }
  
  public void addDevice(Device device) throws SQLException {
    device.setId(QueryBuilder
        .create(dataSource, getQuery("database.insertDevice"), true)
        .setObject(device).executeUpdate());
    updateDeviceCache(true);
  }
  
  public void updateDevice(Device device) throws SQLException {
    QueryBuilder.create(dataSource, getQuery("database.updateDevice"))
        .setObject(device).executeUpdate();
    updateDeviceCache(true);
  }
  
  public void updateDeviceStatus(Device device) throws SQLException {
    QueryBuilder.create(dataSource, getQuery("database.updateDeviceStatus"))
        .setObject(device).executeUpdate();
    Device cachedDevice = getDeviceById(device.getId());
    cachedDevice.setStatus(device.getStatus());
    cachedDevice.setMotion(device.getMotion());
  }
  
  public void removeDevice(long deviceId) throws SQLException {
    QueryBuilder.create(dataSource, getQuery("database.deleteDevice"))
        .setLong("id", deviceId).executeUpdate();
    updateDeviceCache(true);
  }
  
  public void linkDevice(long userId, long deviceId) throws SQLException {
    QueryBuilder.create(dataSource, getQuery("database.linkDevice"))
        .setLong("userId", userId).setLong("deviceId", deviceId)
        .executeUpdate();
  }
  
  public void unlinkDevice(long userId, long deviceId) throws SQLException {
    QueryBuilder.create(dataSource, getQuery("database.unlinkDevice"))
        .setLong("userId", userId).setLong("deviceId", deviceId)
        .executeUpdate();
  }
  
  public Collection<Group> getAllGroups() throws SQLException {
    return QueryBuilder
        .create(dataSource, getQuery("database.selectGroupsAll")).executeQuery(
            Group.class);
  }
  
  public Collection<Group> getGroups(long userId) throws SQLException {
    Collection<Group> groups = new ArrayList<>();
    for (long id : Context.getPermissionsManager().getGroupPermissions(userId)) {
      groups.add(getGroupById(id));
    }
    return groups;
  }
  
  private void checkGroupCycles(Group group) {
    groupsLock.readLock().lock();
    try {
      Set<Long> groups = new HashSet<>();
      while (group != null) {
        if (groups.contains(group.getId())) {
          throw new IllegalArgumentException("Cycle in group hierarchy");
        }
        groups.add(group.getId());
        group = groupsById.get(group.getGroupId());
      }
    } finally {
      groupsLock.readLock().unlock();
    }
  }
  
  public void addGroup(Group group) throws SQLException {
    checkGroupCycles(group);
    group.setId(QueryBuilder
        .create(dataSource, getQuery("database.insertGroup"), true)
        .setObject(group).executeUpdate());
    updateGroupCache(true);
  }
  
  public void updateGroup(Group group) throws SQLException {
    checkGroupCycles(group);
    QueryBuilder.create(dataSource, getQuery("database.updateGroup"))
        .setObject(group).executeUpdate();
    updateGroupCache(true);
  }
  
  public void removeGroup(long groupId) throws SQLException {
    QueryBuilder.create(dataSource, getQuery("database.deleteGroup"))
        .setLong("id", groupId).executeUpdate();
  }
  
  public void linkGroup(long userId, long groupId) throws SQLException {
    QueryBuilder.create(dataSource, getQuery("database.linkGroup"))
        .setLong("userId", userId).setLong("groupId", groupId).executeUpdate();
  }
  
  public void unlinkGroup(long userId, long groupId) throws SQLException {
    QueryBuilder.create(dataSource, getQuery("database.unlinkGroup"))
        .setLong("userId", userId).setLong("groupId", groupId).executeUpdate();
  }
  
  public Collection<Position> getPositions(long deviceId, Date from, Date to)
      throws SQLException {
    return QueryBuilder
        .create(dataSource, getQuery("database.selectPositions"))
        .setLong("deviceId", deviceId).setDate("from", from).setDate("to", to)
        .executeQuery(Position.class);
  }
  
  public void addPosition(Position position) throws SQLException {
    position.setId(QueryBuilder
        .create(dataSource, getQuery("database.insertPosition"), true)
        .setDate("now", new Date()).setObject(position).executeUpdate());
  }
  
  public void updateLatestPosition(Position position) throws SQLException {
    QueryBuilder.create(dataSource, getQuery("database.updateLatestPosition"))
        .setDate("now", new Date()).setObject(position).executeUpdate();
    Device device = getDeviceById(position.getDeviceId());
    device.setPositionId(position.getId());
  }
  
  public Collection<Position> getLatestPositions() throws SQLException {
    return QueryBuilder.create(dataSource,
        getQuery("database.selectLatestPositions"))
        .executeQuery(Position.class);
  }
  
  public Server getServer() throws SQLException {
    return QueryBuilder.create(dataSource, getQuery("database.selectServers"))
        .executeQuerySingle(Server.class);
  }
  
  public void updateServer(Server server) throws SQLException {
    QueryBuilder.create(dataSource, getQuery("database.updateServer"))
        .setObject(server).executeUpdate();
  }
  
  public Event getEvent(long eventId) throws SQLException {
    return QueryBuilder.create(dataSource, getQuery("database.selectEvent"))
        .setLong("id", eventId).executeQuerySingle(Event.class);
  }
  
  public void addEvent(Event event) throws SQLException {
    event.setId(QueryBuilder
        .create(dataSource, getQuery("database.insertEvent"), true)
        .setObject(event).executeUpdate());
  }
  
  public Collection<Event> getEvents(long deviceId, String type, Date from,
      Date to) throws SQLException {
    return QueryBuilder.create(dataSource, getQuery("database.selectEvents"))
        .setLong("deviceId", deviceId).setString("type", type)
        .setDate("from", from).setDate("to", to).executeQuery(Event.class);
  }
  
  public Collection<Event> getLastEvents(long deviceId, String type,
      int interval) throws SQLException {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.SECOND, -interval);
    Date from = calendar.getTime();
    return getEvents(deviceId, type, from, new Date());
  }
  
  public Collection<Geofence> getGeofences() throws SQLException {
    return QueryBuilder.create(dataSource,
        getQuery("database.selectGeofencesAll")).executeQuery(Geofence.class);
  }
  
  public Geofence getGeofence(long geofenceId) throws SQLException {
    return QueryBuilder
        .create(dataSource, getQuery("database.selectGeofences"))
        .setLong("id", geofenceId).executeQuerySingle(Geofence.class);
  }
  
  public void addGeofence(Geofence geofence) throws SQLException {
    geofence.setId(QueryBuilder
        .create(dataSource, getQuery("database.insertGeofence"), true)
        .setObject(geofence).executeUpdate());
  }
  
  public void updateGeofence(Geofence geofence) throws SQLException {
    QueryBuilder.create(dataSource, getQuery("database.updateGeofence"))
        .setObject(geofence).executeUpdate();
  }
  
  public void removeGeofence(long geofenceId) throws SQLException {
    QueryBuilder.create(dataSource, getQuery("database.deleteGeofence"))
        .setLong("id", geofenceId).executeUpdate();
  }
  
  public Collection<GeofencePermission> getGeofencePermissions()
      throws SQLException {
    return QueryBuilder.create(dataSource,
        getQuery("database.selectGeofencePermissions")).executeQuery(
        GeofencePermission.class);
  }
  
  public void linkGeofence(long userId, long geofenceId) throws SQLException {
    QueryBuilder.create(dataSource, getQuery("database.linkGeofence"))
        .setLong("userId", userId).setLong("geofenceId", geofenceId)
        .executeUpdate();
  }
  
  public void unlinkGeofence(long userId, long geofenceId) throws SQLException {
    QueryBuilder.create(dataSource, getQuery("database.unlinkGeofence"))
        .setLong("userId", userId).setLong("geofenceId", geofenceId)
        .executeUpdate();
  }
  
  public Collection<GroupGeofence> getGroupGeofences() throws SQLException {
    return QueryBuilder.create(dataSource,
        getQuery("database.selectGroupGeofences")).executeQuery(
        GroupGeofence.class);
  }
  
  public void linkGroupGeofence(long groupId, long geofenceId)
      throws SQLException {
    QueryBuilder.create(dataSource, getQuery("database.linkGroupGeofence"))
        .setLong("groupId", groupId).setLong("geofenceId", geofenceId)
        .executeUpdate();
  }
  
  public void unlinkGroupGeofence(long groupId, long geofenceId)
      throws SQLException {
    QueryBuilder.create(dataSource, getQuery("database.unlinkGroupGeofence"))
        .setLong("groupId", groupId).setLong("geofenceId", geofenceId)
        .executeUpdate();
  }
  
  public Collection<DeviceGeofence> getDeviceGeofences() throws SQLException {
    return QueryBuilder.create(dataSource,
        getQuery("database.selectDeviceGeofences")).executeQuery(
        DeviceGeofence.class);
  }
  
  public void linkDeviceGeofence(long deviceId, long geofenceId)
      throws SQLException {
    QueryBuilder.create(dataSource, getQuery("database.linkDeviceGeofence"))
        .setLong("deviceId", deviceId).setLong("geofenceId", geofenceId)
        .executeUpdate();
  }
  
  public void unlinkDeviceGeofence(long deviceId, long geofenceId)
      throws SQLException {
    QueryBuilder.create(dataSource, getQuery("database.unlinkDeviceGeofence"))
        .setLong("deviceId", deviceId).setLong("geofenceId", geofenceId)
        .executeUpdate();
  }
  
  public Collection<Notification> getNotifications() throws SQLException {
    return QueryBuilder.create(dataSource,
        getQuery("database.selectNotifications")).executeQuery(
        Notification.class);
  }
  
  public void addNotification(Notification notification) throws SQLException {
    notification.setId(QueryBuilder
        .create(dataSource, getQuery("database.insertNotification"), true)
        .setObject(notification).executeUpdate());
  }
  
  public void updateNotification(Notification notification) throws SQLException {
    QueryBuilder.create(dataSource, getQuery("database.updateNotification"))
        .setObject(notification).executeUpdate();
  }
  
  public void removeNotification(Notification notification) throws SQLException {
    QueryBuilder.create(dataSource, getQuery("database.deleteNotification"))
        .setLong("id", notification.getId()).executeUpdate();
  }
}
