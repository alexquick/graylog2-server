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
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.io.FileUtils;

import com.google.gson.Gson;

public class LocalPluginSource implements IPluginSource {
	private File pluginFile;
	public LocalPluginSource(File pluginFile){
		this.pluginFile = pluginFile;
	}
	
	@Override
	public PluginMetadata getPluginInformation() throws PluginException {
		if(!pluginFile.exists()){
			throw new PluginException("Plugin file " + pluginFile.getAbsolutePath() + " does not exist. ");
		}
		try{
			URL url = new URL("jar:file:" + pluginFile.getAbsolutePath() + "!/plugin.json");
			BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
			PluginMetadata metadata = new Gson().fromJson(br, PluginMetadata.class);
			br.close();
			metadata.jar = pluginFile.toURI().toString();
			return metadata;
		}catch(MalformedURLException e){
			throw new PluginException("The given jar is in an invalid format", e);
		}catch(IOException e){
			throw new PluginException(e);
		}
	}

	@Override
	public void fetchJar(PluginMetadata metadata, File destination)
			throws PluginException {
		try{
			FileUtils.copyFile(pluginFile, destination);
		}catch(IOException e){
			throw new PluginException();
		}
	}

}
