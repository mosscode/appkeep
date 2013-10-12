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
package com.moss.appkeep.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.JAXBContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.BasicConfigurator;

import com.moss.appkeep.api.AppkeepService;
import com.moss.appkeep.api.ComponentInfo;
import com.moss.appkeep.api.security.SecurityException;
import com.moss.appkeep.api.security.UserAccountDownloadToken;
import com.moss.appkeep.api.select.ComponentHandlesSelector;
import com.moss.identity.tools.IdProover;
import com.moss.identity.tools.IdProovingException;
import com.moss.launch.components.Component;
import com.moss.launch.components.ComponentHandle;
import com.moss.launch.components.MavenCoordinatesHandle;
import com.moss.launch.components.util.MavenArtifactGrabber;
import com.moss.launch.spec.JavaAppSpec;
import com.moss.launch.spec.JavaAppletSpec;
import com.moss.launch.spec.app.AppProfile;
import com.moss.launch.spec.app.bundle.BundleSpec;
import com.moss.launch.spec.applet.AppletProfile;
import com.moss.maven.util.SimpleArtifactFinder;
import com.moss.rpcutil.proxy.ProxyProvider;
import com.moss.rpcutil.proxy.hessian.HessianProxyProvider;

public class MavenUploader {
	private final Log log = LogFactory.getLog(getClass());
	
	// "http://localhost/rpc";
	public static void main(String[] args) throws Exception {
		BasicConfigurator.resetConfiguration();
		BasicConfigurator.configure();
		
		File launchSpec = new File(args[0]);

		ProxyProvider proxies = new HessianProxyProvider();
		
		AppkeepService appkeep = proxies.getProxy(AppkeepService.class, args[1]);
		
		new MavenUploader().upload(launchSpec, appkeep, null, false);
	}
	
	public List<Component> upload(File launchSpec, AppkeepService appkeep, IdProover idProver, boolean forceUpload) throws IdProovingException, SecurityException {

		// 1) select launch-spec
		Object o;
		try {
			JAXBContext jaxb = JAXBContext.newInstance(JavaAppSpec.class, JavaAppletSpec.class);
			
			o = jaxb.createUnmarshaller().unmarshal(launchSpec.toURL());
		} catch (Throwable e) {
			throw new RuntimeException("Error reading file \"" + launchSpec + "\"", e);
		}
		
		final List<Component> components = new LinkedList<Component>();
		
		if(o instanceof JavaAppSpec){
			JavaAppSpec spec =  (JavaAppSpec) o;
			
			{
				components.addAll(spec.components());
				
				for(BundleSpec b : spec.bundles()){
					components.addAll(b.components());
				}
				
				for(AppProfile p : spec.profiles()){
					components.addAll(p.components());
				}
			}
		}else if(o instanceof JavaAppletSpec){
			JavaAppletSpec spec = (JavaAppletSpec) o;
			
			
			{
				components.addAll(spec.components());
				
				for(AppletProfile p : spec.profiles()){
					components.addAll(p.components());
				}
			}
		}
		

		// 2) for each component (in each profile):
		for(Component c : components){
			// a) locate the component in the maven repo (local, remote?)
			MavenCoordinatesHandle m = MavenArtifactGrabber.getMavenArtifact(c.artifactHandles());
			if(m==null){
				throw new RuntimeException("Component has no maven handle: " + c);
			}
			
			final String logPrefix = c.type() + ": " + m;
			
			ComponentInfo info = appkeep.getInfo(new ComponentHandlesSelector(c.artifactHandles()), new UserAccountDownloadToken(idProver.giveProof()));
			
//			List<ComponentInfo> matches = appkeep.listByMavenInfo(m.groupId(), m.artifactId(), m.version(), idProver.giveProof());
			
			if(forceUpload || info==null){
				if(forceUpload){
					log.info("[FORCING] " + logPrefix + " ");
				}else{
					log.info("[NOT IN KEEP] " + logPrefix + " ");
				}
				// b) download it
				SimpleArtifactFinder finder = new SimpleArtifactFinder();
				File location = finder.findLocal(m.groupId(), m.artifactId(), m.version());
				if(!location.exists()){
					throw new RuntimeException("Cannot find " + m + " in local repository.  I was expecting to find it at " + location.getAbsolutePath());
				}
				
				log.info(" found in local repository: " + location.getAbsoluteFile());
				
				InputStream data;
				try {
					data = new FileInputStream(location);
				} catch (FileNotFoundException e) {
					throw new RuntimeException(e);
				}
				try {
					log.info("uploading");
					// c) upload it if necessary
					appkeep.post(c.artifactHandles().toArray(new ComponentHandle[]{}), c.type(), idProver.giveProof(), data);
					log.info("done");
				} catch (SecurityException e) {
					throw new RuntimeException(e);
				} finally{
					try {
						data.close();
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			}else{
				log.info("[ALREADY IN KEEP] " + logPrefix + " ");
			}
		}
		/*
		 * 3) upload the launch-spec itself?
		 */
		
		return components;
	}
}
