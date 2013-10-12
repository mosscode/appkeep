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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.SignJar;
import org.apache.tools.ant.types.FileSet;
import org.joda.time.Duration;
import org.joda.time.Instant;

import com.moss.appkeep.api.AppkeepService;
import com.moss.appkeep.api.ComponentId;
import com.moss.appkeep.api.ComponentInfo;
import com.moss.appkeep.api.NoMatchingComponentException;
import com.moss.appkeep.api.endorse.ComponentEndorsement;
import com.moss.appkeep.api.endorse.ComponentEndorsementVisitor;
import com.moss.appkeep.api.endorse.JarsignEndorsement;
import com.moss.appkeep.api.endorse.x509.X509CertId;
import com.moss.appkeep.api.endorse.x509.X509CertInfo;
import com.moss.appkeep.api.maven.MavenGroupInfo;
import com.moss.appkeep.api.mirror.ComponentPropogationOverview;
import com.moss.appkeep.api.mirror.PeerId;
import com.moss.appkeep.api.mirror.PeerInfo;
import com.moss.appkeep.api.security.AnonDownloadGrantId;
import com.moss.appkeep.api.security.AnonDownloadToken;
import com.moss.appkeep.api.security.DownloadToken;
import com.moss.appkeep.api.security.DownloadTokenVisitor;
import com.moss.appkeep.api.security.SecurityException;
import com.moss.appkeep.api.security.UserAccountDownloadToken;
import com.moss.appkeep.api.select.ComponentHandlesSelector;
import com.moss.appkeep.api.select.ComponentSelector;
import com.moss.appkeep.api.select.ComponentSelectorVisitor;
import com.moss.appkeep.api.select.DirectComponentSelector;
import com.moss.appkeep.server.KeepTool.WorkAtomParticipant;
import com.moss.appkeep.server.config.CertificateRegistration;
import com.moss.appkeep.server.config.PeerRegistration;
import com.moss.appkeep.server.config.ServerConfiguration;
import com.moss.appkeep.server.config.X509KeystoreCertReference;
import com.moss.appkeep.server.data.Data;
import com.moss.appkeep.server.endorse.SignedJarId;
import com.moss.appkeep.server.endorse.SignedJarRecord;
import com.moss.appkeep.server.maven.MavenGroup;
import com.moss.appkeep.server.maven.MavenKey;
import com.moss.appkeep.server.mirror.Poller;
import com.moss.appkeep.server.mirror.ReplicationQueueItem;
import com.moss.appkeep.server.security.AnonDownloadAccessGrant;
import com.moss.appkeep.server.security.UserAccount;
import com.moss.bdbwrap.SearchVisitor;
import com.moss.bdbwrap.bdbsession.WorkAtom;
import com.moss.fskit.Ant;
import com.moss.identity.Id;
import com.moss.identity.IdProof;
import com.moss.identity.IdVerifier;
import com.moss.identity.tools.IdProover;
import com.moss.identity.tools.IdProovingException;
import com.moss.identity.tools.IdTool;
import com.moss.launch.components.BuildTimestampComponentHandle;
import com.moss.launch.components.CRC32ComponentHandle;
import com.moss.launch.components.ComponentHandle;
import com.moss.launch.components.ComponentHandleVisitor;
import com.moss.launch.components.ComponentType;
import com.moss.launch.components.MavenCoordinatesHandle;
import com.moss.launch.components.Md5ComponentHandle;
import com.moss.rpcutil.proxy.ProxyFactory;
import com.moss.saturn.lobstore.LobStore;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.Transaction;

/**
 * TODO: Distribution 
 *        - of user accounts
 * TODO: x-delta
 */
public class AppkeepServiceImpl implements AppkeepService {
	private final Log log = LogFactory.getLog(getClass());

	private final ServerConfiguration config;
	private final Data data;
	private final LobStore lobs;
	private final KeepTool tool;
	private final Poller replicator;
	private final ProxyFactory proxies;
	private final IdProover meProver;
	private final IdTool idTool;

	public AppkeepServiceImpl(Data data, LobStore lobs, Poller replicator, ServerConfiguration config, ProxyFactory proxies, IdProover meProver, IdTool idTool) {
		super();
		this.data = data;
		this.lobs = lobs;
		this.config = config;
		this.tool = new KeepTool(data, lobs, config.peers(), proxies, config.id(), meProver);
		this.replicator = replicator;
		this.proxies = proxies;
		this.meProver = meProver;
		this.idTool = idTool;
	}

	interface Asserter {
		void assertIt() throws SecurityException;
	}

	private void assertDownloadPrivileges(final ComponentId component, DownloadToken token) throws SecurityException {
		if(token==null){
			throw new SecurityException("missing download token");
		}
		Asserter a = token.accept(new DownloadTokenVisitor<Asserter>() {
			public Asserter visit(final AnonDownloadToken t) {
				return new Asserter(){
					public void assertIt() throws SecurityException {
						assertDownloadPrivileges(component, t);
					}
				};
			}
			public Asserter visit(final UserAccountDownloadToken t) {
				return new Asserter(){
					public void assertIt() throws SecurityException {
						assertDownloadPrivileges(component, t.proof());
					}
				};
			}
		});

		a.assertIt();
	}


	private void assertDownloadPrivileges(ComponentId component, AnonDownloadToken token) throws SecurityException {
		AnonDownloadAccessGrant grant = data.anonGrants.get(token.id(), null, LockMode.READ_COMMITTED);
		if(grant==null){
			throw new SecurityException("Invalid grant");
		}else{
			boolean granted = false;

			for(ComponentId next : grant.components()){
				if(next.equals(component)){
					granted = true;
				}
			}

			if(!granted){
				throw new SecurityException("Access to component " + component + " is not covered under this grant");
			}
		}
	}

	private void assertDownloadPrivileges(ComponentId component, IdProof proof) throws SecurityException {
		if(proof==null){
			throw new SecurityException("No ID proof was specified");
		}

		try {
			final IdVerifier v = idTool.getVerifier(proof);
			if(v.verify()){
				final Id id = v.id();
				// FIRST, LET'S SEE IF THIS IS A REQUEST FROM A PEER
				PeerRegistration r = null;
				{
					for(PeerRegistration next : config.peers()){
						if(next.authenticationId().equals(id)){
							r = next;
						}
					}
				}
				if(r==null){
					// IT'S NOT A PEER - CHECK TO SEE IF IT IS A REGULAR USER ACCOUNT
					UserAccount account = data.userAccounts.get(id, null, LockMode.READ_COMMITTED);
					if(account==null){
						// at this point, if it's not a peer or a user account, we deny it
						throw new SecurityException("There is no user account for " + id);
					}else{
						if(log.isDebugEnabled()){
							log.debug("Approving download access for user " + account.id());
						}
					}
				}else{
					if(log.isDebugEnabled()){
						log.debug("Approving download access for peer at " + r.url());
					}
				}
			}else{
				throw new SecurityException("Invalid credentials");
			}
		} catch (IdProovingException e) {
			log.info("Error prooving identity - I'm throwing a " + SecurityException.class.getSimpleName() + ", but this is the root:", e);
			throw new SecurityException(e.getMessage());
		}
	}


	private UserAccount authenticateUser(IdProof proof) throws SecurityException {
		if(proof==null){
			throw new SecurityException("No ID proof was specified");
		}
		try {
			IdVerifier v = idTool.getVerifier(proof);
			if(v.verify()){
				Id id = v.id();
				UserAccount account = data.userAccounts.get(id, null, LockMode.READ_COMMITTED);
				if(account==null){
					throw new SecurityException("There is no user account for " + id);
				}else{
					return account;
				}
			}else{
				throw new SecurityException("Invalid credentials");
			}
		} catch (IdProovingException e) {
			log.info("Error prooving identity - I'm throwing a " + SecurityException.class.getSimpleName() + ", but this is the root:", e);
			throw new SecurityException(e.getMessage());
		}
	}

	private PeerRegistration authenticatePeer(PeerId peer, IdProof proof) throws SecurityException {
		try {
			IdVerifier v = idTool.getVerifier(proof);
			if(v.verify()){
				Id id = v.id();


				PeerRegistration r = null;

				for(PeerRegistration next : config.peers()){
					if(next.id().equals(peer)){
						r = next;
					}
				}
				if(r==null){
					throw new SecurityException("There is no peer registration for " + id);
				}else{
					return r;
				}
			}else{
				throw new SecurityException("Invalid credentials");
			}
		} catch (IdProovingException e) {
			log.info("Error prooving identity - I'm throwing a " + SecurityException.class.getSimpleName() + ", but this is the root:", e);
			throw new SecurityException(e.getMessage());
		}
	}

	/**
	 *   This is a tool for handling the 'AND' logic of the component handle matching algorithm:
	 *   a {@link ComponentHandlesSelector} is only considered to be a match against a given {@link StoredComponent}
	 *   if ALL the handles match it.
	 */
	private static class ComponentHandlesMatchJoiner {
		private final Log log = LogFactory.getLog(getClass());
		private final Map<ComponentId, StoredComponent> cache = new HashMap<ComponentId, StoredComponent>();
		private final Map<ComponentHandle, Set<ComponentId>> matchesMap = new HashMap<ComponentHandle, Set<ComponentId>>();

		public ComponentHandlesMatchJoiner(ComponentHandlesSelector selector) {
			for (ComponentHandle next : selector.handles()) {
				matchesMap.put(next, new HashSet<ComponentId>());
			}
		}

		public void addMatch(ComponentHandle handle, StoredComponent ... matches){

			Set<ComponentId> ids = matchesMap.get(handle);

			if(matches!=null){
				for(StoredComponent next : matches){
					ids.add(next.id());
					cache.put(next.id(), next);
				}
			}
		}

		/**
		 * Returns all the components that matched ALL of the handles
		 */
		public List<StoredComponent> fullMatches(){
			List<StoredComponent> fullMatches = new LinkedList<StoredComponent>();

			for(ComponentId next : cache.keySet()){
				final StoredComponent c = cache.get(next);

				boolean matchesEveryHandle = true;
				for(Map.Entry<ComponentHandle, Set<ComponentId>> nextHandleMatchSet: matchesMap.entrySet()){
					if(!nextHandleMatchSet.getValue().contains(next)){
						final ComponentHandle h = nextHandleMatchSet.getKey();

						log.info(c.toDto() + " doesn't match " + h.getClass().getSimpleName() + ": " + h
								+ " (there were " + nextHandleMatchSet.getValue().size() + " other components that did match)");
						matchesEveryHandle = false;
					}
				}

				if(matchesEveryHandle){
					fullMatches.add(c);
				}
			}

			return fullMatches;
		}
	}

	private StoredComponent resolve(ComponentSelector selector){
		final StoredComponent result;

		final List<StoredComponent> matches = selector.accept(new ComponentSelectorVisitor<List<StoredComponent>>() {
			public List<StoredComponent> visit(ComponentHandlesSelector handles) {
				final ComponentHandlesMatchJoiner joiner = new ComponentHandlesMatchJoiner(handles);


				for(ComponentHandle handle : handles.handles()){
					handle.accept(new ComponentHandleVisitor<Void>() {

						public Void visit(final CRC32ComponentHandle h) {

							data.componentsByCrc32.keySearchForward(
									h.value(), 
									new SearchVisitor<Long, StoredComponent>() {
										public boolean next(Long key, StoredComponent value) {
											if(key.equals(h.value())){

												joiner.addMatch(h, value);

												return true;
											}else{
												return false;
											}
										}
									}, 
									null
							);

							return null;
						}

						public Void visit(final MavenCoordinatesHandle m) {
							final MavenKey k = new MavenKey(m.coordinates());

							data.componentsByMavenId.keySearchForward(
									k,
									new SearchVisitor<MavenKey, StoredComponent>() {
										public boolean next(MavenKey key, StoredComponent value) {
											if(key.equals(k)){
												joiner.addMatch(m, value);
												return true;
											}else{
												return false;
											}
										}
									},
									null
							);

							return null;
						}
						public Void visit(final Md5ComponentHandle h) {
							final byte[] hash = h.hash();

							data.componentsByMd5.keySearchForward(
									hash,
									new SearchVisitor<byte[], StoredComponent>() {
										public boolean next(byte[] key, StoredComponent value) {
											if(AppkeepServiceImpl.equals(hash, key)){
												joiner.addMatch(h, value);
												return true;
											}else{
												return false;
											}
										}
									},
									null
							);
							return null;
						}
						public Void visit(final BuildTimestampComponentHandle h) {

							data.componentsByWhenBuilt.keySearchForward(
									h.getWhenBuilt(),
									new SearchVisitor<Instant, StoredComponent>() {
										public boolean next(Instant key, StoredComponent value) {
											if(key.equals(h.getWhenBuilt())){
												joiner.addMatch(h, value);
												return true;
											}else{
												return false;
											}
										}
									},
									null
							);

							return null;
						}

					});
				}
				return joiner.fullMatches();
			}

			public List<StoredComponent> visit(DirectComponentSelector directSelector) {
				return Collections.singletonList(data.components.get(directSelector.id(), null, LockMode.READ_COMMITTED));
			}
		});

		// HANDLE MULTIPLE MATCHES
		if(matches.size()==0){
			result = null;
		}else if(matches.size()==1){
			result = matches.get(0);
		}else{
			StoredComponent latest = null;
			for(StoredComponent next : matches){
				if(latest==null || next.whenAdded().isAfter(latest.whenAdded())){
					latest = next;
				}
			}
			result = latest;
		}
		return result;
	}


	private static boolean equals(byte[] a, byte[] b){
		if(a.length != b.length){
			return false;
		}else{
			for(int x=0; x<a.length; x++){
				if(a[x]!=b[x]){
					return false;
				}
			}
			return true;
		}
	}

	public List<X509CertInfo> listCertificates() {
		final List<X509CertInfo> infos = new ArrayList<X509CertInfo>(config.certificates().size());

		for(CertificateRegistration next : config.certificates()){
			infos.add(next.toInfoDto());
		}

		return infos;
	}

	
	
	private void doJarsignEndorsements(JarsignEndorsement e, List<StoredComponent> components){
		final List<X509CertId> jarCertSet = e.getJarCertSet();

		for(StoredComponent resolved : components){

			if(!tool.isSigned(e, resolved)){
				// we need to sign

				try {
					// COPY THE FILE TO A TEMP LOCATION
					File temp = File.createTempFile("jar-signing", ".jar");
					temp.deleteOnExit();

					{
						byte[] buf = new byte[1024*50];
						InputStream in = lobs.getStream(resolved.lobId());
						OutputStream out = new FileOutputStream(temp);
						for(int x = in.read(buf); x != -1; x = in.read(buf)){
							out.write(buf, 0, x);
						}
						in.close();
						out.close();
					}
					
					// SIGN IT WITH ALL THE CERTIFICATES
					for(X509CertId nextCert : jarCertSet){
						FileSet files = new FileSet();

						files.setDir(temp.getParentFile());
						files.appendIncludes(new String[]{temp.getName()});

						final CertificateRegistration cert = config.certificate(nextCert);
						X509KeystoreCertReference ref = cert.keystoreCertHandle();
						
						if(!ref.keystorePath().exists()){
							throw new IOException("No such file: " + ref.keystorePath().getAbsolutePath());
						}
						try{

							SignJar sj = new SignJar();
							sj.setKeystore(ref.keystorePath().getAbsolutePath());
							sj.setStorepass(ref.keystorePass());
							sj.setAlias(ref.certAlias());
							sj.addFileset(files);

							Ant ant = new Ant();
							ant.addFileset(files);
							ant.run(sj);
						}catch(BuildException err){
							err.printStackTrace();
							throw new RuntimeException("Error signing jars: " + err.getMessage());
						}
					}

					// COPY INTO THE LOB STORE

					final SignedJarId jarId = SignedJarId.random();
					final SignedJarRecord record = new SignedJarRecord(jarId, resolved.id(), jarCertSet.toArray(new X509CertId[jarCertSet.size()]));
					lobs.put(jarId.asLobId(), new FileInputStream(temp));
					data.signedJars.put(jarId, record, null);

					// CLEAN-UP
					if(!temp.delete()){
						throw new IOException("Could not delete temp file: " + temp.getAbsolutePath());
					}


				} catch (IOException err) {
					throw new RuntimeException("Error signing jars: " + err.getMessage(), err);
				}


			}
		}

	}

	public void endorse(final ComponentSelector[] components, ComponentEndorsement... endorsements) {

		//		final List<X509CertId> jarCertSet = new LinkedList<X509CertId>();
		//		for(ComponentEndorsement next : endorsements){
		//			next.accept(new ComponentEndorsementVisitor<Void>() {
		//				public Void visit(final JarsignEndorsement e) {
		//					jarCertSet.add(e.certId());
		//					return null;
		//				}
		//			});
		//		}

		final List<StoredComponent> resolutions = new ArrayList<StoredComponent>(components.length);
		for(ComponentSelector nextComponent : components){
			final StoredComponent resolved = resolve(nextComponent);
			resolutions.add(resolved);
		}
		for(ComponentEndorsement next : endorsements){
			next.accept(new ComponentEndorsementVisitor<Void>() {
				public Void visit(final JarsignEndorsement e) {
					doJarsignEndorsements(e, resolutions);
					return null;
				}
			});
		}

	}

	
	public InputStream download(ComponentSelector selector, DownloadToken token, ComponentEndorsement ... endorsements) throws SecurityException, NoMatchingComponentException {
		tool.basicAmbiguityPrecheck(selector);
		
		final StoredComponent result = resolve(selector);
		
		if(result==null){
			throw new NoMatchingComponentException(selector.toString());
		}
		
		if(!result.isPublic()){
			assertDownloadPrivileges(result.id(), token);
		}
		
		return tool.getComponent(result, Arrays.asList(endorsements));
	}

	public ComponentInfo getInfo(ComponentSelector selector, DownloadToken token) throws SecurityException {
		StoredComponent result = resolve(selector);

		if(result==null){
			return null;
		}else{
			assertDownloadPrivileges(result.id(), token);		
			return result.toDto();
		}
	}

	public List<ComponentInfo> getInfos(List<ComponentSelector> selectors, DownloadToken token) throws SecurityException {
		if(selectors.size()>300){
			throw new RuntimeException("Too many selectors: break this request down into multiple requests");
		}

		List<ComponentInfo> results = new ArrayList<ComponentInfo>(selectors.size());

		for(ComponentSelector next : selectors){
			StoredComponent c = resolve(next);

			results.add(c==null?null:c.toDto());
		}

		return results;
	}

	public void grantWorldAccess(List<ComponentSelector> components, IdProof credentials) 
		throws SecurityException, NoMatchingComponentException {
		
		UserAccount user = authenticateUser(credentials);

		if(!user.canGrantAccess()){
			throw new SecurityException("User does not have access grant privileges");
		}
		
		final List<StoredComponent> matches = new LinkedList<StoredComponent>();
		for(ComponentSelector next : components){
			StoredComponent match = resolve(next);
			if(match==null){
				throw new NoMatchingComponentException("Could not find a component matching " + next);
			}
			matches.add(match);
			match.setPublic(true);
		}
		
		new WorkAtom(data) {
			@Override
			protected void doWork(Transaction tx) throws Exception {
				for(StoredComponent next : matches){
					data.components.put(next.id(), next, tx);
				}
			}
		}.run();
		
	}
	
	public AnonDownloadToken grantAccess(List<ComponentSelector> components, Duration length, IdProof credentials) 
		throws SecurityException, NoMatchingComponentException {

		UserAccount user = authenticateUser(credentials);

		if(!user.canGrantAccess()){
			throw new SecurityException("User does not have access grant privileges");
		}
		
		List<ComponentId> componentIds = new LinkedList<ComponentId>();
		for(ComponentSelector next : components){
			StoredComponent match = resolve(next);
			if(match==null){
				throw new NoMatchingComponentException("Could not find a component matching " + next);
			}else{
				componentIds.add(match.id());
			}
		}
		AnonDownloadAccessGrant grant = new AnonDownloadAccessGrant(
				AnonDownloadGrantId.random(), 
				new Instant().plus(length), 
				componentIds
		);

		data.anonGrants.put(grant.id(), grant, null);
		return new AnonDownloadToken(grant.id());

	}

	public List<ComponentInfo> listByMavenInfo(String groupId, String artifactId, String version, IdProof credentials) throws SecurityException {

		authenticateUser(credentials);

		final MavenKey pattern = new MavenKey(groupId, artifactId, version);

		final List<ComponentInfo> components = new LinkedList<ComponentInfo>();

		data.componentsByMavenId.keySearchForward(
				pattern,
				new SearchVisitor<MavenKey, StoredComponent>() {
					public boolean next(MavenKey key, StoredComponent value) {
						if(pattern.encompasses(key)){
							components.add(value.toDto());
							return true;
						}else{
							return false;
						}
					}
				}, 
				null);

		return components;
	}

	public MavenGroupInfo mavenGroupInfo(final String groupId, IdProof credentials) throws SecurityException {
		authenticateUser(credentials);

		final MavenKey pattern = new MavenKey(groupId, null, null);

		final List<ComponentInfo> components = new LinkedList<ComponentInfo>();

		data.componentsByMavenId.keySearchForward(
				pattern,
				new SearchVisitor<MavenKey, StoredComponent>() {
					public boolean next(MavenKey key, StoredComponent value) {
						if(key.groupId().equals(groupId)){
							components.add(value.toDto());
							return true;
						}else{
							return false;
						}
					}
				}, 
				null);

		final Set<String> subgroups = new TreeSet<String>();
		data.mavenGroupsByParentGroup.keySearchForward(
				groupId, 
				new SearchVisitor<String, MavenGroup>() {
					public boolean next(String key, MavenGroup value) {
						if(value.parentGroupName().equals(groupId)){
							subgroups.add(value.name());
							return true;
						}else{
							return false;
						}
					}
				},
				null);
		subgroups.remove(groupId);
		return new MavenGroupInfo(groupId, components, new LinkedList<String>(subgroups));
	}



	class ReplicationWorkPutParticipant implements WorkAtomParticipant {
		final List<ReplicationQueueItem> replicationTasks = new LinkedList<ReplicationQueueItem>();
		public void prepare(ComponentId id, Instant now) {
			// REPLICATION TASKS
			for(PeerRegistration peer : config.peers()){
				replicationTasks.add(new ReplicationQueueItem(peer.id(), id, now));
			}
		}
		public void doWork(Transaction tx) {
			for(ReplicationQueueItem next : replicationTasks){
				data.replicationQueue.put(next, null, tx);
			}	

		}
	}

	public ComponentId post(ComponentHandle[] handles, ComponentType type, IdProof credentials,	InputStream fileData) throws SecurityException {

		UserAccount user = authenticateUser(credentials);

		if(user.canUpload()){
			final ComponentId id = ComponentId.random();

			tool.putComponent(id, type, fileData, handles, new ReplicationWorkPutParticipant());

			if(config.peers().size()>0){
				log.info("notifying peers");
				// REPLICATION TASKS
				for(PeerRegistration peer : config.peers()){
					log.info("notifying "  + peer.url());
					try {
						proxies.create(AppkeepService.class, peer.url()).mirrorUpdated(config.id(), meProver.giveProof());
					} catch (Throwable e) {
						log.error("Error notifying " + peer.url() + " of mirror update: " + e.getMessage(), e);
					}
				}
				log.info("done notifying peers");
			}
			return id;
		}else{
			throw new SecurityException("User does not have posting privileges");
		}
	}


	/*--------------------------------------------------------------------------------------
	 * MIRROR MECHANISM
	 *-------------------------------------------------------------------------------------*/

	public PeerInfo[] mirrors(IdProof credentials) throws SecurityException {

		authenticateUser(credentials);

		PeerInfo[] results = new PeerInfo[config.peers().size()];
		for(int x=0;x<results.length;x++){
			results[x] = config.peers().get(x).toDto();
		}
		return results;
	}

	public void mirrorUpdated(PeerId mirror, IdProof credentials) throws SecurityException {
		authenticatePeer(mirror, credentials);
		replicator.pollNow();
	}


	public ComponentId next(ComponentId lastDownload, PeerId mirror, IdProof credentials) throws SecurityException {
		authenticatePeer(mirror, credentials);

		if(lastDownload!=null){
			// THE PEER IS REPORTING SUCCESS WITH THE LAST QUEUE ITEM.
			// WE NEED TO FIND THAT ITEM IN THE QUEUE IN ORDER TO
			// REMOVE IT
			final ReplicationQueueCollector collector = new ReplicationQueueCollector(new ReplicationQueueItem(mirror, lastDownload, null));
			data.replicationQueue.search(collector);

			if(collector.results().isEmpty()){
				throw new RuntimeException("Component " + lastDownload + " is not in your queue");
			}else{
				new WorkAtom(this.data) {
					@Override
					protected void doWork(Transaction tx) throws Exception {

						for(ReplicationQueueItem next : collector.results()){
							data.replicationQueue.delete(next, tx);
						}				
					}
				}.run();
			}
		}

		// FIND THE NEXT ITEM IN THE QUEUE
		ReplicationQueueTopFinder topFinder = new ReplicationQueueTopFinder(mirror);
		data.replicationQueue.search(topFinder);

		return topFinder.top()==null?null:topFinder.top().component();
	}

	public List<ComponentPropogationOverview> propogationStatus(final ComponentId[] components, IdProof credentials) throws SecurityException {

		UserAccount user = authenticateUser(credentials);

		final Map<ComponentId, ComponentPropogationOverview> results = new HashMap<ComponentId, ComponentPropogationOverview>();

		data.replicationQueue.search(
				new SearchVisitor<ReplicationQueueItem, Void>() {
					public boolean next(ReplicationQueueItem key, Void value) {
						for(final ComponentId id : components){
							if(id.equals(key.component())){
								ComponentPropogationOverview o = results.get(id);
								if(o==null){
									o = new ComponentPropogationOverview(id);
									results.put(id, o);
								}
								o.peerInQueue(key.peer());
							}
						}
						return true;
					}
				}
		);

		return new ArrayList<ComponentPropogationOverview>(results.values());
	}

	private static class ReplicationQueueCollector implements SearchVisitor<ReplicationQueueItem, Void> {
		private final ReplicationQueueItem pattern;
		private final List<ReplicationQueueItem> results = new LinkedList<ReplicationQueueItem>();

		public ReplicationQueueCollector(ReplicationQueueItem pattern) {
			super();
			this.pattern = pattern;
		}

		public boolean next(ReplicationQueueItem key, Void value) {
			if(pattern.encompasses(key)){
				results.add(key);
			}
			return true;
		}

		public List<ReplicationQueueItem> results() {
			return results;
		}
	}

	private static class ReplicationQueueTopFinder implements SearchVisitor<ReplicationQueueItem, Void> {
		private final PeerId peer;
		private final List<ComponentId> componentsToExclude = new LinkedList<ComponentId>();
		private ReplicationQueueItem top;

		public ReplicationQueueTopFinder(PeerId peer) {
			super();
			this.peer = peer;
		}

		public boolean next(ReplicationQueueItem key, Void value) {
			if(key.peer().equals(peer)){
				if(top == null || key.whenQueued().isBefore(top.whenQueued())){
					boolean isExcluded = false;
					for(ComponentId next : componentsToExclude){
						if(next.equals(key.component())){
							isExcluded = true;
						}
					}
					if(!isExcluded){
						top = key;
					}
				}
				return true;
			}else{
				return false;
			}
		}

		public void exclude(ComponentId id){
			this.componentsToExclude.add(id);
		}

		public ReplicationQueueItem top() {
			return top;
		}
	}

}
