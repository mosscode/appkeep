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
package com.moss.appkeep.server.data;

import java.io.File;

import javax.xml.bind.JAXBContext;

import org.joda.time.Instant;

import com.moss.appkeep.api.ComponentId;
import com.moss.appkeep.api.security.AnonDownloadGrantId;
import com.moss.appkeep.server.StoredComponent;
import com.moss.appkeep.server.endorse.SignedJarId;
import com.moss.appkeep.server.endorse.SignedJarRecord;
import com.moss.appkeep.server.maven.MavenGroup;
import com.moss.appkeep.server.maven.MavenKey;
import com.moss.appkeep.server.mirror.ReplicationQueueItem;
import com.moss.appkeep.server.security.AnonDownloadAccessGrant;
import com.moss.appkeep.server.security.UserAccount;
import com.moss.bdbwrap.BinarySerializer;
import com.moss.bdbwrap.DbWrap;
import com.moss.bdbwrap.EmptySerializer;
import com.moss.bdbwrap.EnvironmentWrap;
import com.moss.bdbwrap.SecondaryDbWrap;
import com.moss.bdbwrap.defaults.DefaultJaxbDbWrap;
import com.moss.bdbwrap.defaults.DefaultSingleKeyJaxbSecondaryDbWrap;
import com.moss.bdbwrap.jaxb.JaxbContextProvider;
import com.moss.bdbwrap.tostring.FromStringFactory;
import com.moss.bdbwrap.tostring.StringFromStringFactory;
import com.moss.bdbwrap.tostring.ToStringSerializer;
import com.moss.identity.Id;
import com.moss.identity.simple.SimpleId;
import com.moss.identity.veracity.VeracityId;
import com.moss.launch.components.BuildTimestampComponentHandle;
import com.moss.launch.components.CRC32ComponentHandle;
import com.moss.launch.components.ComponentHandle;
import com.moss.launch.components.LaxComponentHandleVisitor;
import com.moss.launch.components.MavenCoordinatesHandle;
import com.moss.launch.components.Md5ComponentHandle;
import com.moss.launch.components.util.BuildTimeHandleGrabber;
import com.moss.launch.components.util.Md5ArtifactHandleGrabber;
import com.sleepycat.je.LockMode;

public class Data extends EnvironmentWrap {
	
	private final JaxbContextProvider jaxb = new JaxbContextProvider() {
		public JAXBContext context() {
			return jaxbCtx;
		}
	};
	
	public final DbWrap<Id, UserAccount> userAccounts = new DefaultJaxbDbWrap<Id, UserAccount>(
			"user-accounts", 
			new IdentityFromStringFactory(),
			jaxb, 
			this
		);
	
	public final DbWrap<String, String> passwords = new DbWrap<String, String>(
			"passwords",
			new ToStringSerializer<String>(new StringFromStringFactory()),
			new ToStringSerializer<String>(new StringFromStringFactory()),
			this
		);
	
	public final DbWrap<AnonDownloadGrantId, AnonDownloadAccessGrant> anonGrants = new DefaultJaxbDbWrap<AnonDownloadGrantId, AnonDownloadAccessGrant>(
			"anon-download-grants", 
			AnonDownloadGrantId.class, 
			jaxb, 
			this
			);
	
	public final DbWrap<String, MavenGroup> mavenGroups = new DefaultJaxbDbWrap<String, MavenGroup>(
			"maven-groups", 
			String.class, 
			jaxb, 
			this);
	
	public final SecondaryDbWrap<String, MavenGroup> mavenGroupsByParentGroup = new DefaultSingleKeyJaxbSecondaryDbWrap<String, MavenGroup>(
			"maven-groups-by-parent-group",
			mavenGroups,
			String.class
	) {
		@Override
		public String extractKey(MavenGroup data) {
			return data.parentGroupName();
		}
	};
	
	public final DbWrap<ReplicationQueueItem, Void> replicationQueue = new DbWrap<ReplicationQueueItem, Void>(
			"replication-queue", 
			new ToStringSerializer<ReplicationQueueItem>(new FromStringFactory<ReplicationQueueItem>() {
				public ReplicationQueueItem fromString(String text) {
					return new ReplicationQueueItem(text);
				}
			}),
			new EmptySerializer<Void>(),
			this
		);
	
	public final DbWrap<ComponentId, StoredComponent> components = new DefaultJaxbDbWrap<ComponentId, StoredComponent>(
			"components", 
			ComponentId.class,
			jaxb, 
			this
		);
	
	
	public final SecondaryDbWrap<Long, StoredComponent> componentsByCrc32 = new DefaultSingleKeyJaxbSecondaryDbWrap<Long, StoredComponent>(
			"components-by-crc32",
			components,
			Long.class
		) {
				@Override
				public Long extractKey(StoredComponent data) {
					Long crc32 = null;
					
					for(ComponentHandle next : data.handles()){
						CRC32ComponentHandle h = next.accept(new LaxComponentHandleVisitor<CRC32ComponentHandle>() {
							@Override
							public CRC32ComponentHandle visit(CRC32ComponentHandle h) {
								return h;
							}
							@Override
							protected CRC32ComponentHandle defaultValue(ComponentHandle h) {
								return null;
							}
						});
						if(h!=null){
							
							if(crc32!=null){
								throw new RuntimeException("There is more than one crc32 hash for component " + data.id());
							}else{
								crc32 = h.value();
							}
						}
					}
					
					if(crc32 == null){
						throw new RuntimeException("Component is missing CRC 32 data");
					}
					
					return crc32;
				}
			}.withAllowDuplicates(true);
			
			
	public final SecondaryDbWrap<byte[], StoredComponent> componentsByMd5 = new DefaultSingleKeyJaxbSecondaryDbWrap<byte[], StoredComponent>(
			"components-by-md5-hash",
			components,
			new BinarySerializer()
		) {
				@Override
				public byte[] extractKey(StoredComponent data) {
					byte[] hash = null;
					
					for(ComponentHandle next : data.handles()){
						Md5ComponentHandle h = next.accept(new Md5ArtifactHandleGrabber());
						if(h!=null){
							if(hash!=null){
								throw new RuntimeException("There is more than one md5 sum for component " + data.id());
							}else{
								hash = h.hash();
							}
						}
					}
					
					if(hash==null){
						throw new RuntimeException("Component is missing md5 data");
					}
					return hash;
				}
			};
			
	public final SecondaryDbWrap<Instant, StoredComponent> componentsByWhenBuilt = new DefaultSingleKeyJaxbSecondaryDbWrap<Instant, StoredComponent>(
			"components-by-when-built",
			components,
			new InstantSerializer()
		) {
				@Override
				public Instant extractKey(StoredComponent data) {
					Instant hash = null;
					
					for(ComponentHandle next : data.handles()){
						BuildTimestampComponentHandle h = next.accept(new BuildTimeHandleGrabber());
						if(h!=null){
							if(hash!=null){
								throw new RuntimeException("There is more than one build time for component " + data.id());
							}else{
								hash = h.getWhenBuilt();
							}
						}
					}
					
					return hash;
				}
			}.withAllowDuplicates(true);
			
	public final SecondaryDbWrap<MavenKey, StoredComponent> componentsByMavenId = new DefaultSingleKeyJaxbSecondaryDbWrap<MavenKey, StoredComponent>(
			"components-by-maven-artifact",
			components,
			MavenKey.class
		) {
				@Override
				public MavenKey extractKey(StoredComponent data) {
					MavenCoordinatesHandle m = null;
					
					for(ComponentHandle next : data.handles()){
						MavenCoordinatesHandle h = next.accept(new LaxComponentHandleVisitor<MavenCoordinatesHandle>() {
							@Override
							public MavenCoordinatesHandle visit(com.moss.launch.components.MavenCoordinatesHandle m) {
								return m;
							}
							@Override
							protected MavenCoordinatesHandle defaultValue(ComponentHandle h) {
								return null;
							}
						});
						if(h!=null){
							m = h;
						}
					}
					
					return new MavenKey(m.coordinates());
				}
			}.withAllowDuplicates(true);
			
			
	public final DbWrap<SignedJarId, SignedJarRecord> signedJars = new DefaultJaxbDbWrap<SignedJarId, SignedJarRecord>(
			"signed-jars", 
			SignedJarId.class,
			jaxb, 
			this
		);
	
	public final SecondaryDbWrap<ComponentId, SignedJarRecord> signedJarsByCertificate = new DefaultSingleKeyJaxbSecondaryDbWrap<ComponentId, SignedJarRecord>(
			"signed-jars-by-component-id",
			signedJars,
			ComponentId.class
	) {
		@Override
		public ComponentId extractKey(SignedJarRecord data) {
			return data.component();
		}
	};
	
//	public final SecondaryDbWrap<X509CertId, SignedJarRecord> signedJarsByCertificate = new DefaultMultiKeyJaxbSecondaryDbWrap<X509CertId, SignedJarRecord>(
//			"signed-jars-by-certificate",
//			signedJars,
//			X509CertId.class
//	) {
//		@Override
//		protected List<X509CertId> createKeys(SignedJarRecord data) {
//			return Arrays.asList(data.certificateIds());
//		}
//	};
	
	private final JAXBContext jaxbCtx;
	
	public Data(File location, long cacheSize) throws Exception {
		super(location,  cacheSize);
		
		jaxbCtx = JAXBContext.newInstance(
				VeracityId.class,
				SimpleId.class,
				
				UserAccount.class,
				StoredComponent.class,
				MavenGroup.class,
				AnonDownloadAccessGrant.class,
				SignedJarRecord.class
				
				);
		
		load();
		
		MavenGroup root = mavenGroups.get("", null, LockMode.READ_COMMITTED);
		if(root==null){
			root = new MavenGroup("");
			mavenGroups.put(root.name(), root, null);
		}
	}
}
