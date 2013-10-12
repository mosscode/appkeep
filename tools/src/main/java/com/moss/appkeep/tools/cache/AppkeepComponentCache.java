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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.moss.appkeep.api.ComponentId;
import com.moss.appkeep.api.ComponentInfo;
import com.moss.appkeep.api.security.SecurityException;
import com.moss.appkeep.api.select.ComponentHandlesSelector;
import com.moss.appkeep.api.select.ComponentSelector;
import com.moss.appkeep.api.select.DirectComponentSelector;
import com.moss.identity.tools.IdProovingException;
import com.moss.jaxbhelper.JAXBHelper;
import com.moss.launch.components.Component;
import com.moss.launch.components.ComponentType;

public class AppkeepComponentCache {
	private final Log log = LogFactory.getLog(getClass());
	private final File cacheLocation;
	private final JAXBHelper helper;
	
	public AppkeepComponentCache(File cacheLocation) {
		super();
		this.cacheLocation = cacheLocation;
		
		if(!cacheLocation.exists() && ! cacheLocation.mkdirs()){
			throw new RuntimeException("Could not create directory : " + cacheLocation.getAbsolutePath());
		}
		
		try {
			this.helper = new JAXBHelper(CacheEntryInfo.class);
		} catch (JAXBException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static class CacheResolution {
		public final File localPath;
		public final ComponentId componentId;
		
		public CacheResolution(File localPath, ComponentId componentId) {
			super();
			this.localPath = localPath;
			this.componentId = componentId;
		}
	}
	
	public File getLocal(ComponentId id, Component c){
		return dataPathFor(id, c.type());
	}
	
	/**
	 * @deprecated Use resolve() instead
	 */
	@Deprecated
	public File get(Component c, AppkeepConnector source) throws SecurityException, IdProovingException {
		return resolve(c, source).localPath;
	}
	
	public CacheResolution resolve(Component c, AppkeepConnector source) throws SecurityException, IdProovingException {
		ComponentSelector selector = new ComponentHandlesSelector(c.artifactHandles());
		
		final ComponentInfo info = source.keep().getInfo(selector, source.produceToken());
		if(info==null){
			throw new RuntimeException("Keep does not have component: " + c);
		}else {
			File localPath = dataPathFor(info);
			if(!localPath.exists()){
				try {
					log.info("Downloading: " + c);
					copyAndClose(source.keep().download(new DirectComponentSelector(info.id()), source.produceToken()), new FileOutputStream(localPath));
					
					helper.writeToFile(helper.writeToXmlString(new CacheEntryInfo(info)), metadataPathFor(info));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			
			log.debug(localPath.getAbsolutePath());
			return new CacheResolution(localPath, info.id());
		}
	}

	private final File metadataPathFor(ComponentInfo c){
		return new File(cacheLocation, c.id().toString() + ".info.xml");
	}
	
	private final File dataPathFor(ComponentId id, ComponentType type){
		return new File(cacheLocation, id.toString() + "." + type.name().toLowerCase());
	}
	
	private final File dataPathFor(ComponentInfo c){
		return dataPathFor(c.id(), c.type());
	}

	private static final void copyAndClose(InputStream in, OutputStream out) throws IOException {
		Throwable rootError = null;
		List<Throwable> cleanupErrors = new LinkedList<Throwable>();
		
		try {
			byte[] buffer = new byte[100*1024];
			for(int x=in.read(buffer);x!=-1;x=in.read(buffer)){
				out.write(buffer, 0, x);
			}
		} catch(Throwable t){
			rootError = t;
		} finally {
			try {
				in.close();
			} catch (Exception e) {
				cleanupErrors.add(e);
			}
			try {
				out.close();
			} catch (Exception e) {
				cleanupErrors.add(e);
			}
		}
		
		if(rootError!=null || cleanupErrors.size()>0){
			if(rootError!=null){
				throw new RuntimeException("Error", rootError);
			}else{
				String message = "Error";
				
				if(cleanupErrors.size()>0){
					message += " (Cleanup errors too - see logs)";
					for(Throwable next : cleanupErrors){
						next.printStackTrace();
					}
				}
				throw new RuntimeException(message, rootError);
			}
		}
	}
}
