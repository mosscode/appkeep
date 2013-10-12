/**
 * Copyright (C) 2013, Moss Computing Inc.
 *
 * This file is part of appkeep.
 *
 * appkeep is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * appkeep is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with appkeep; see the file COPYING.  If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 */
package com.moss.appkeep.tools.cache;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.moss.appkeep.api.AppkeepService;
import com.moss.appkeep.api.security.DownloadToken;
import com.moss.appkeep.api.security.SecurityException;
import com.moss.appkeep.tools.DownloadTokenFactory;
import com.moss.identity.tools.IdProovingException;
import com.moss.launch.components.Component;
import com.moss.rpcutil.proxy.hessian.HessianProxyProvider;

public class SimpleAppkeepComponentCache {
	private static final String APPKEEP_DOWNLOAD_CACHE_DIR_NAME="appkeep-cache";
	
	private static final File cacheRoot(){
		final File homeDir = new File(System.getProperty("user.home"));
		final File configDir;
		
		if(System.getProperty("os.name").toLowerCase().contains("windows")){
			File appDataDir = new File(homeDir, "Application Data");
			configDir = new File(appDataDir, APPKEEP_DOWNLOAD_CACHE_DIR_NAME);
			if(!configDir.exists()){
				if(!configDir.mkdirs())
					throw new RuntimeException("Could not create directory: " + configDir.getAbsolutePath());
			}
		}else{
			configDir = new File(homeDir, "." + APPKEEP_DOWNLOAD_CACHE_DIR_NAME);
		}
		
		return configDir;
	}
	
	
	private static String dirNameForUrl(String url){
		try {
			URL u = new URL(url);
			return u.getProtocol() + "_" + u.getHost() + "_" + u.getPort() + "_" + u.getPath().replaceAll(Pattern.quote("/"), "_");
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}
	
	private final Log log = LogFactory.getLog(getClass());
	private final AppkeepComponentCache cache;
	private final AppkeepConnector connector;
	
	public SimpleAppkeepComponentCache(String url, DownloadTokenFactory tokenFactory) {
		this(new HessianProxyProvider().getProxy(AppkeepService.class, url), new File(cacheRoot(), dirNameForUrl(url)), tokenFactory);
	}
	
	SimpleAppkeepComponentCache(final AppkeepService keep, File cacheLocation, final DownloadTokenFactory tokenFactory) {
		cache = new AppkeepComponentCache(cacheLocation);
		this.connector = new AppkeepConnector() {
			public DownloadToken produceToken() {
				return tokenFactory.produce();
			}
			public AppkeepService keep() {
				return keep;
			}
		};
	}

	public File get(Component c) throws SecurityException, IdProovingException {
		return cache.get(c, connector);
	}

}
