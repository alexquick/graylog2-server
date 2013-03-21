/**
 * Copyright 2012 Lennart Koopmann <lennart@socketfeed.com>
 *
 * This file is part of Graylog2.
 *
 * Graylog2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog2.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.graylog2.plugins;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.Set;
import org.graylog2.Configuration;
import org.graylog2.Core;
import org.graylog2.database.MongoBridge;
import org.graylog2.database.MongoConnection;
import org.graylog2.plugin.Tools;
import org.graylog2.plugin.alarms.callbacks.AlarmCallback;
import org.graylog2.plugin.alarms.transports.Transport;
import org.graylog2.plugin.filters.MessageFilter;
import org.graylog2.plugin.initializers.Initializer;
import org.graylog2.plugin.inputs.MessageInput;
import org.graylog2.plugin.outputs.MessageOutput;

/**
 * @author Lennart Koopmann <lennart@socketfeed.com>
 */
public class PluginInstaller {
    
    // seed mongodb
    // ask user to configure plugin and restart graylog2-server

    private final boolean force;
    private final Configuration configuration;
    
    private MongoBridge mongoBridge;
	private IPluginSource source;
    
    public PluginInstaller(IPluginSource source, Configuration configuration, boolean force) {
        this.configuration = configuration;
        this.force = force;
        this.source = source;
    }
    
    public void install() {
        connectMongo();
        
        System.out.println("Attempting to install plugin <" + source.toString() + ">");
        
        if (force) {
            System.out.println("In force mode. Even installing if not officially"
                    + " compatible to this version of graylog2-server.");
        }
        
        try {
            PluginMetadata pluginInformation = source.getPluginInformation();

            System.out.println("Got plugin information from API.");

            if (!force && !compatible(pluginInformation.compatible_versions)) {
                System.out.println("Plugin is not officially compatible to this version "
                        + "of graylog2-server. Run with --force-plugin to force installation.");
                return;
            }
            
            System.out.println("Fetching JAR: " + pluginInformation.jar);
            File jarFile = new File(jarPath(pluginInformation));
            source.fetchJar(pluginInformation, jarFile);
            System.out.println("Copied JAR to " + jarFile.getAbsolutePath());
            
            LoadedPlugin plugin = loadPlugin(jarFile, pluginInformation.getClassOfPlugin());
            Map<String, String> config = plugin.configuration;
            
            System.out.println("Requested configuration: " + config);
            
            mongoBridge.writeSinglePluginInformation(
                    PluginRegistry.buildStandardInformation(plugin.getClassName(), pluginInformation.name, config),
                    pluginInformation.getRegistryName()
            );
            
            System.out.println("All done. You can now configure this plugin in the web interface. "
                    + "Please restart graylog2-server when you are done so the plugin is loaded.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static boolean compatible(Set<String> versions) {
        if (versions == null) {
            return false;
        }
        
        return versions.contains(Core.GRAYLOG2_VERSION);
    }
    
    public static String jarPath(PluginMetadata info) {
    	try {
    		String jarUrl = info.jar;
        	String path = "plugin/" + info.getPluginTypeName() + "/" + jarUrl.substring(jarUrl.lastIndexOf("/")+1);
            
            // lol just to make sure...
            if (path.startsWith("/")) {
                throw new RuntimeException("Extracted JAR path starts with /. This should never happen.");
            }
            
            return path;
        } catch(Exception e) {
            throw new RuntimeException("Could not build JAR path.");
        }
    }
    
    public LoadedPlugin loadPlugin(File file, Class type) throws Exception {

        ClassLoader loader = URLClassLoader.newInstance(
            new URL[] { file.toURI().toURL() },
            getClass().getClassLoader()
        );

        Class<?> p = Class.forName(PluginLoader.getClassNameFromJar(file), true, loader);
        String className = p.getCanonicalName();
        String name = "";
        Map<String, String> configuration =  Maps.newHashMap();
        Object pluginObj = p.asSubclass(type).newInstance();

        // no shame, time for a second iteration!
        
        if (pluginObj instanceof Initializer) {
            Initializer plugin = (Initializer) pluginObj;
           name = plugin.getName();
           configuration = plugin.getRequestedConfiguration();
        }else if (pluginObj instanceof MessageInput) {
            MessageInput plugin = (MessageInput) pluginObj;
            name = plugin.getName();
            configuration = plugin.getRequestedConfiguration();
        }else if (pluginObj instanceof MessageFilter) {
            MessageFilter plugin = (MessageFilter) pluginObj;
            name = plugin.getName();
            // zomg filters have no config
        }else if (pluginObj instanceof MessageOutput) {
            MessageOutput plugin = (MessageOutput) pluginObj;
            name = plugin.getName();
            configuration = plugin.getRequestedConfiguration();
        }else if (pluginObj instanceof Transport) {
            Transport plugin = (Transport) pluginObj;
            name = plugin.getName();
            configuration = plugin.getRequestedConfiguration();
        }else if (pluginObj instanceof AlarmCallback) {
            AlarmCallback plugin = (AlarmCallback) pluginObj;
            name = plugin.getName();
            configuration = plugin.getRequestedConfiguration();
        }else{
        	throw new RuntimeException("Could not get requested configuration of plugin. Unknown type: " + p.getName() );
        }
        return new LoadedPlugin(name, className, configuration);
    }
    
    private void connectMongo() {
        MongoConnection mongoConnection = new MongoConnection();    // TODO use dependency injection
        mongoConnection.setUser(configuration.getMongoUser());
        mongoConnection.setPassword(configuration.getMongoPassword());
        mongoConnection.setHost(configuration.getMongoHost());
        mongoConnection.setPort(configuration.getMongoPort());
        mongoConnection.setDatabase(configuration.getMongoDatabase());
        mongoConnection.setUseAuth(configuration.isMongoUseAuth());
        mongoConnection.setMaxConnections(configuration.getMongoMaxConnections());
        mongoConnection.setThreadsAllowedToBlockMultiplier(configuration.getMongoThreadsAllowedToBlockMultiplier());
        mongoConnection.setReplicaSet(configuration.getMongoReplicaSet());

        mongoBridge = new MongoBridge(null);
        mongoBridge.setConnection(mongoConnection);
        mongoConnection.connect();
    }
    
    // come up with a better name for this guy or factor him out
    private class LoadedPlugin{
    	private String name;
    	private String className;
    	private Map<String, String> configuration;
    	public LoadedPlugin(String name, String className, Map<String, String> configuration){
    		this.name = name;
    		this.className = className;
    		this.configuration = configuration;
    	}
		public String getName() {
			return name;
		}
		public String getClassName() {
			return className;
		}
		public Map<String, String> getConfiguration() {
			return configuration;
		}
    }
    
}
