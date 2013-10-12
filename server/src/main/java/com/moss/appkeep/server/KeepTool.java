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
package com.moss.appkeep.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.Instant;

import com.moss.appkeep.api.ComponentId;
import com.moss.appkeep.api.endorse.ComponentEndorsement;
import com.moss.appkeep.api.endorse.ComponentEndorsementVisitor;
import com.moss.appkeep.api.endorse.JarsignEndorsement;
import com.moss.appkeep.api.endorse.x509.X509CertId;
import com.moss.appkeep.api.mirror.PeerId;
import com.moss.appkeep.api.select.ComponentHandlesSelector;
import com.moss.appkeep.api.select.ComponentSelector;
import com.moss.appkeep.api.select.ComponentSelectorVisitor;
import com.moss.appkeep.api.select.DirectComponentSelector;
import com.moss.appkeep.server.config.PeerRegistration;
import com.moss.appkeep.server.data.Data;
import com.moss.appkeep.server.endorse.SignedJarRecord;
import com.moss.appkeep.server.maven.MavenGroup;
import com.moss.bdbwrap.SearchVisitor;
import com.moss.bdbwrap.bdbsession.WorkAtom;
import com.moss.identity.tools.IdProover;
import com.moss.launch.components.BuildTimestampComponentHandle;
import com.moss.launch.components.CRC32ComponentHandle;
import com.moss.launch.components.ComponentHandle;
import com.moss.launch.components.ComponentHandleVisitor;
import com.moss.launch.components.ComponentType;
import com.moss.launch.components.MavenCoordinatesHandle;
import com.moss.launch.components.Md5ComponentHandle;
import com.moss.launch.components.util.CRC32ArtifactHandleGrabber;
import com.moss.launch.components.util.MavenArtifactGrabber;
import com.moss.launch.components.util.Md5ArtifactHandleGrabber;
import com.moss.launch.tools.digests.Crc32DigestHandleMaker;
import com.moss.launch.tools.digests.DigestHandleMaker;
import com.moss.launch.tools.digests.Md5DigestHandleMaker;
import com.moss.launch.tools.maven.MavenTools;
import com.moss.lobstore.LobId;
import com.moss.lobstore.LobStore;
import com.moss.rpcutil.proxy.ProxyFactory;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.Transaction;

public class KeepTool {
	
	public interface WorkAtomParticipant {
		void prepare(ComponentId component, Instant now);
		void doWork(Transaction tx);
	}
	
	private final Log log = LogFactory.getLog(getClass());
	private final Data data;
	private final LobStore lobs;
	private final List<PeerRegistration> peers;
	private final ProxyFactory proxies;
	private final PeerId myId;
	private final IdProover meProver;
	
	public KeepTool(Data data, LobStore lobs, List<PeerRegistration> peers, final ProxyFactory proxies, final PeerId myId, IdProover meProver) {
		super();
		this.data = data;
		this.lobs = lobs;
		this.peers = peers;
		this.proxies = proxies;
		this.myId = myId;
		this.meProver = meProver;
	}
	

	public List<SignedJarRecord> signedVersions(final JarsignEndorsement e, final StoredComponent c){
		final List<SignedJarRecord> matches = new LinkedList<SignedJarRecord>();
		final X509CertId firstCert = e.getJarCertSet().get(0);
		
		data.signedJarsByCertificate.keySearchForward(
				c.id(), 
				new SearchVisitor<ComponentId, SignedJarRecord>() {
					public boolean next(ComponentId key, SignedJarRecord value) {
						if(key.equals(c.id())){

							if(value.isSignedByAll(e.getJarCertSet())){
								matches.add(value);
							}

							return true;
						}else{
							return false;
						}

					};
				},
				null
		);

		return matches;
	}
	
	public boolean isSigned(final JarsignEndorsement e, StoredComponent c){
		final List<SignedJarRecord> matches = signedVersions(e, c);

		return matches.size() > 0;
	}
	
	public InputStream getComponent(final StoredComponent c, List<ComponentEndorsement> endorsements){
		InputStream i;
		if(endorsements.size()>1){
			throw new RuntimeException("We only support single endorsements at this time");
		}else if(endorsements.size()==1){
			
			i = endorsements.get(0).accept(new ComponentEndorsementVisitor<InputStream>() {
				public InputStream visit(JarsignEndorsement e) {
					
					List<SignedJarRecord> matches = signedVersions(e, c);
					if(matches.size()==0){
						throw new RuntimeException("Cannot provide endorsement: " + e);
					}
					
					return lobs.getStream(matches.get(0).id().asLobId());
					
				}
			});
			
		}else{
			i = lobs.getStream(c.lobId());
		}
		
		return i;
	}
	
	
	public void basicAmbiguityPrecheck(ComponentSelector selector){
		selector.accept(new ComponentSelectorVisitor<Void>() {
			public Void visit(ComponentHandlesSelector handles) {
				MavenTools.mavenFunctionTokensAmbiguityCheck(handles.handles());
				return null;
			}
			public Void visit(DirectComponentSelector directSelector) {
				return null;
			}
		});
	}
	
//	public void basicAmbiguityPrecheck(ComponentHandle[] handles){
//		if(handles.length==1){
//			handles[0].accept(new LaxComponentHandleVisitor<Void>() {
//				@Override
//				public Void visit(MavenCoordinatesHandle m) {
//					if(m.version().toUpperCase().contains("SNAPSHOT")){
//						throw new RuntimeException("Too much ambiguity: all I got were these lousy SNAPSHOT coordinates.  Be a dear and add an md5 checksum or build time, would-ja?");
//					}
//					
//					return null;
//				}
//				
//				@Override
//				protected Void defaultValue(ComponentHandle h) {
//					return null;
//				}
//			});
//		}
//	}
	
	public void putComponent(final ComponentId id, ComponentType type, InputStream data, ComponentHandle[] handles, final WorkAtomParticipant ... participants){
		final Instant now = new Instant();
		
		final List<ComponentHandle> handlesToUse = new LinkedList<ComponentHandle>(Arrays.asList(handles));
		final File diskBufferPath;
		final List<ComponentHandle> generatedHandles;
		
		// REJECT UNACCEPTABLE AMBIGUITY
		MavenTools.mavenFunctionTokensAmbiguityCheck(handles);
		
		// COPY TO DISK & GENERATE CHECKSUMS/HASHES
		try {
			diskBufferPath = File.createTempFile("incoming-component-file", type.name());
			FileOutputStream out = new FileOutputStream(diskBufferPath);
			DigestHandleMaker[] digesters = new DigestHandleMaker[]{
					new Md5DigestHandleMaker(),
					new Crc32DigestHandleMaker()
			};
			{
				
				byte[] b = new byte[1024*200];
				for(int x=data.read(b);x!=-1;x = data.read(b)){
					for(DigestHandleMaker digester : digesters){
						digester.update(b, 0, x);
					}
					out.write(b, 0, x);
				}
				
			}
			generatedHandles = new ArrayList<ComponentHandle>(digesters.length);

			for(DigestHandleMaker digester : digesters){
				generatedHandles.add(digester.handle());
			}
			
		} catch (IOException e2) {
			throw new RuntimeException(e2);
		}
		
		// ADD AND COMPARE GENERATED HANDLES
		for(ComponentHandle generated : generatedHandles){
			generated.accept(new ComponentHandleVisitor<Void>() {
				public Void visit(CRC32ComponentHandle generated) {
					addCompare("CRC32 Checksum", handlesToUse, new CRC32ArtifactHandleGrabber(), generated);
					return null;
				}
				public Void visit(Md5ComponentHandle generated) {
					addCompare("MD5 hash", handlesToUse, new Md5ArtifactHandleGrabber(), generated);
					return null;
				}
				
				public Void visit(MavenCoordinatesHandle m) {
					// NOT A GENERATED HANDLE
					return null;
				}
				public Void visit(BuildTimestampComponentHandle h) {
					// NOT A GENERATED HANDLE
					return null;
				}
			});
		}
		
		// MAINTAIN THE MAVEN GROUPS TREE
		final List<MavenGroup> groupsToRecord = new LinkedList<MavenGroup>();
		{
			MavenCoordinatesHandle m = MavenArtifactGrabber.getMavenArtifact(handlesToUse);
			if(m!=null){
				
				String nextGroup = m.groupId();
				while(!nextGroup.equals("")){
					MavenGroup g = this.data.mavenGroups.get(nextGroup, null, LockMode.READ_COMMITTED);
					if(g==null){
						g = new MavenGroup(nextGroup);
						groupsToRecord.add(g);
					}
					nextGroup = g.parentGroupName();
				}
			}
		}
		
		if(participants!=null){
			for(WorkAtomParticipant next : participants){
				next.prepare(id, now);
			}
		}
		
		// SAVE THE FILE
		LobId lobId = new LobId(id.toString());
		
		try {
			lobs.put(lobId, new FileInputStream(diskBufferPath));
			if(!diskBufferPath.delete()){
				throw new RuntimeException("Could not delete temp file: " + diskBufferPath.getAbsolutePath());
			}
		} catch (FileNotFoundException e1) {
			throw new RuntimeException(e1);
		}
		
		final long length = lobs.length(lobId);
		
		final StoredComponent c = new StoredComponent(id, handlesToUse.toArray(new ComponentHandle[handlesToUse.size()]), type, length, now);

		try {
			new WorkAtom(this.data) {
				@Override
				protected void doWork(Transaction tx) throws Exception {
					KeepTool.this.data.components.put(id, c, tx);
					for(MavenGroup next : groupsToRecord){
						log.info("Found new maven group " + next.name());
						KeepTool.this.data.mavenGroups.put(next.name(), next, tx);
					}
					
					if(participants!=null){
						for(WorkAtomParticipant next : participants){
							next.doWork(tx);
						}
					}
				}
			}.run();
		} catch (Throwable e) {
			lobs.remove(lobId);
			throw new RuntimeException(e);
		}
		
		log.info("Stored " + c.toDto().toString());
	}
	
	private <T extends ComponentHandle> void addCompare(String typeName, List<ComponentHandle> handlesToUse, ComponentHandleVisitor<T> filter, T generated){
		List<T> matches = filterHandlesByType(handlesToUse, filter);
		
		if(matches.size()>1){
			throw new RuntimeException("More than one " + typeName);
		}else if(matches.size()==1){
			T match = matches.get(0);
			if(!generated.equals(match)){
				throw new RuntimeException(typeName + " don't match: I generated " + generated + " but was given " + match);
			}else{
				log.info("Validated " + typeName);
				handlesToUse.remove(match);
			}
		}
		handlesToUse.add(generated);
	}
	private <T extends ComponentHandle> List<T> filterHandlesByType(List<ComponentHandle> handles, ComponentHandleVisitor<T> filter){
		List<T> matches = new LinkedList<T>();
		
		for(ComponentHandle next : handles){
			T match = next.accept(filter);
			if(match!=null){
				matches.add(match);
			}
		}
		
		return matches;
	}
}
