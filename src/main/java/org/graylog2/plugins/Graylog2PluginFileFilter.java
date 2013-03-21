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

import java.io.File;
import java.io.FilenameFilter;

/**
 * @author Lennart Koopmann <lennart@socketfeed.com>
 */
public class Graylog2PluginFileFilter implements FilenameFilter {

    @Override
    public boolean accept(File file, String string) {
    	if(!string.endsWith(".jar")){
    		return false;
    	}
        return string.endsWith("_gl2plugin.jar") || hasMetadata(new File(file, string));
    }
    
    private boolean hasMetadata(File jarFile){
    	return new LocalPluginSource(jarFile).hasMetadata();
    }   
}
