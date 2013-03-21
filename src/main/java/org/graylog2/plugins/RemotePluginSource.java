/**
 * Copyright 2013 Lennart Koopmann <lennart@socketfeed.com>
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import org.graylog2.plugin.Tools;

import com.google.gson.Gson;

public class RemotePluginSource implements IPluginSource {
    private final static String API_TARGET = "http://plugins.graylog2.org/plugin";

    private String shortname;
    private String version;
    
    public RemotePluginSource(String shortname, String version){
    	this.shortname = shortname;
    	this.version = version;
    }
    
	@Override
	public PluginMetadata getPluginInformation() throws PluginException {
        PluginMetadata result;
        
        HttpURLConnection connection = null;

        try {
            URL endpoint = new URL(buildUrl());
            connection = (HttpURLConnection) endpoint.openConnection();

            connection.setRequestMethod("GET");

            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);

            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("Got HTTP response code "
                        + connection.getResponseCode() + " "
                        + connection.getResponseMessage() + ". Expected HTTP 200.");
            }
            
            BufferedReader rd = new BufferedReader(
                    new InputStreamReader(connection.getInputStream())
            );
            
            result = new Gson().fromJson(rd.readLine(), PluginMetadata.class);
        } catch(Exception e){
        	throw new PluginException(e);
        } finally {
            // Make sure to close connection.
            if(connection != null) {
                connection.disconnect();
            }
        }
        
        return result;
	}

	@Override
	public void fetchJar(PluginMetadata info, File destination) throws PluginException{
        int startTime = Tools.getUTCTimestamp();
         HttpURLConnection connection = null;
        try{
        	URL url = new URL(info.jar);
	        connection = (HttpURLConnection)url.openConnection();
	        InputStream reader = connection.getInputStream();
	
	        FileOutputStream writer = new FileOutputStream(destination);
	        byte[] buffer = new byte[153600];
	        int totalBytesRead = 0;
	        int bytesRead = 0;
	
	        while ((bytesRead = reader.read(buffer)) > 0) {  
	            writer.write(buffer, 0, bytesRead);
	            buffer = new byte[153600];
	            totalBytesRead += bytesRead;
	        }
	
	        long endTime = Tools.getUTCTimestamp();
	
	        System.out.println("Done. " + totalBytesRead + " bytes read "
	                + "(took " + (endTime - startTime) + "s).");
			writer.close();
			reader.close();
        }catch(Exception e){
				throw new PluginException(e);
        }finally{
        	if(connection != null){
        	connection.disconnect();
        	}
        }
	}
	
    private String buildUrl() {
        return API_TARGET + "/" + shortname + "/" + version;
    }
}
