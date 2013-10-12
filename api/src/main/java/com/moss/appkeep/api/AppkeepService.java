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
package com.moss.appkeep.api;

import java.io.InputStream;
import java.util.List;

import org.joda.time.Duration;

import com.moss.appkeep.api.endorse.ComponentEndorsement;
import com.moss.appkeep.api.endorse.x509.X509CertInfo;
import com.moss.appkeep.api.maven.MavenGroupInfo;
import com.moss.appkeep.api.mirror.ComponentPropogationOverview;
import com.moss.appkeep.api.mirror.PeerId;
import com.moss.appkeep.api.mirror.PeerInfo;
import com.moss.appkeep.api.security.AnonDownloadToken;
import com.moss.appkeep.api.security.DownloadToken;
import com.moss.appkeep.api.security.SecurityException;
import com.moss.appkeep.api.select.ComponentSelector;
import com.moss.identity.IdProof;
import com.moss.launch.components.ComponentHandle;
import com.moss.launch.components.ComponentType;

/**
 * <h2>Overview</h2>
 * <p>
 * Appkeep is a high-performance/throughput, high-availability, near-mission-critical network service for storing/retrieving 
 * application components (modules) in a secure fashion.
 * </p>
 * 
 * <h3>Design notes</h3>
 * <p>
 * Appkeep's most important security concern is authorization: restricting download access to modules.  Wire-security (keeping 
 * people from being able to easily sniff the module off the wire) is desirable, but is a far secondary concern to both 
 * authorization and to speed/throughput.  For this reason, appkeep makes compromises in wire security for the sake of 
 * throughput and authorization.
 * </p>
 * 
 * <h2>Authorization</h2>
 * <p>
 * Appkeep employs two separate authentication+authorization mechanisms: 
 * </p>
 * <ul>
 * 	<li>Download token mechanism</li>
 * 	<li>User accounts mechanism</li>
 * </ul>
 * <h3>Download Token Mechanism</h3>
 * <p>
 * All download operations are authorized via this mechanism.  This mechanism exists to provide a means of granting 
 * temporary download access via a light-weight, transient authorization token, when a full-fledged user account is
 * not warranted.  All download requests are authorized by means of a DownloadToken, where there are two types of 
 * DownloadToken:
 * </p>
 * <ol>
 * 	<li>
 * 		<b>AnonDownloadToken - </b>
 *			A light-weight, temporary access grant that does not correspond to a persistent user account.
 * 	</li>
 *  <li>
 *  	<b>UserAccountDownloadToken - </b>
 *  		A user account token authorizes downloads using the more heavy-weight user account mechanism (see below).
 *  </li>
 * </ol>
 * 
 * <h4>AnonDownloadToken</h4>
 * <p>
 * An anon. token has an expiration date.  Once expired, the server effectively forgets that the token ever existed.
 * Anon. tokens are granted by third parties via grantAccess().  An AnonDownloadToken is only valid at the mirror at
 * which it was granted; a separate anon. token must be granted for each mirror as needed.  
 * </p>
 * <p>
 * The Appkeep is not responsible for distributing anon. tokens: the party that grants the token is responsible for 
 * passing the token on to other agents as needed.  In essence, anon. download tokens are a means of delegating download 
 * authorization to third parties.  
 * </p>
 * 
 * <h3>User Accounts Mechanism</h3>
 * <p>
 * </p>
 * 
 * </p>
 * 
 * <h2>Mirroring</h2>
 * <p>
 * When components are added to an appkeep, they are automatically propagated to its circle of peers (a.k.a its mirrors).  
 * The method of propogation works like this: 
 * </p>
 * <ol>
 * <li>
 * 	Each keep maintains a separate queue for each of it's peers.  Every time a component is added to 
 * 	the keep, it adds an entry for that component to each of the peer queues. 
 * </li>
 * <li>
 * 	Each peer then regularly (every so many minutes) polls to check for new items in it's queue using the next() method.  
 * 	In this way, a peer is notified of the presence of the new component.  
 * </li>
 * <li>
 *  Once a peer is made aware of a new item in it's queue, it then proceeds to download that component using the standard 
 *  download mechanism of its choice.
 * </li>
 * <li>
 *  The next time the peer calls next(), it passes information about the component it just downloaded.  In this way, the 
 *  original keep is notified that the component was successfully propogated to the peer, and, accordingly, the keep then
 *  pops the item off the top of the queue for that peer.
 * </li>
 * </ol>
 * <h3>Optional callback notification method</h3>
 * <p>
 * In order to reduce the several minutes lag inherent in the polling mechanism, a keep can optionally make use of 
 * the peerUpdated() method to synchronously notify a peer that it's queue at that keep has been updated.  If a peer
 * receives such a callback, it should then check its queue (by calling next(), etc) at the indicated keep as soon as 
 * possible.  NOTE: This is just an optional, secondary means of peer-to-peer notification;  A peer should not rely 
 * exclusively on this mechanism for receiving updates. 
 * </p>
 */
public interface AppkeepService {
	
	/**
	 * "Normal" (slow) component download
	 */
	InputStream download(ComponentSelector selector, DownloadToken token, ComponentEndorsement ... endorsements) throws SecurityException, NoMatchingComponentException;
	
	/**
	 * "Normal" (slow) component upload
	 */
	ComponentId post(ComponentHandle[] handles, ComponentType type, IdProof credentials, InputStream data) throws SecurityException;
	
	/**
	 * Returns descriptive metadata regarding the (closest) matching component
	 */
	ComponentInfo getInfo(ComponentSelector selector, DownloadToken token) throws SecurityException;
	
	List<ComponentInfo> getInfos(List<ComponentSelector> selector, DownloadToken token) throws SecurityException;
	
	/**
	 * Provides a means of browsing the repository by querying based on maven info.  An null
	 * in any parameter is interpreted as a wildcard.
	 * 
	 * e.g. groupId="com.moss" returns all components  
	 */
	List<ComponentInfo> listByMavenInfo(String groupId, String artifactId, String version, IdProof credentials) throws SecurityException;
	
	MavenGroupInfo mavenGroupInfo(String groupId, IdProof credentials) throws SecurityException;
	
	AnonDownloadToken grantAccess(List<ComponentSelector> components, Duration length, IdProof credentials) throws SecurityException, NoMatchingComponentException;
	
	void grantWorldAccess(List<ComponentSelector> components, IdProof credentials) throws SecurityException, NoMatchingComponentException;
	
	/*--------------------------------------------------------------------------------------
	 * ENDORSEMENTS MECHANISM
	 *-------------------------------------------------------------------------------------*/
	
	List<X509CertInfo> listCertificates();
	void endorse(ComponentSelector[] components, ComponentEndorsement ... endorsements);
	
	/*--------------------------------------------------------------------------------------
	 * MIRROR MECHANISM
	 *-------------------------------------------------------------------------------------*/
	
	/**
	 * Clients can use this to figure out whether a component has fully propogated
	 * to all the mirrors.
	 */
	List<ComponentPropogationOverview> propogationStatus(ComponentId[] components, IdProof credentials) throws SecurityException;
	
	/**
	 * Lists information about this keep's known mirrors
	 */
	PeerInfo[] mirrors(IdProof credentials) throws SecurityException;
	
	/**
	 * This is a mirror's way of advancing to the next item in the download queue.
	 */
	ComponentId next(ComponentId lastDownload, PeerId mirror, IdProof credentials) throws SecurityException;
	
	
	/**
	 * The regular (reliable) method of propogation works like this: each mirror regularly (every so many minutes) polls 
	 * to check for new items in their queue at each of their peers.  This method constitutes a secondary (less reliable,
	 * but faster) mechanism for notifying mirrors that stuff has been added to their queue.  No guarantees are made
	 * that a given mirror will ever get one of these calls - they are purely optional; if a mirror does not receive one
	 * of these calls, it should eventually (within a handful of minutes) detect the new queue contents when it does its 
	 * next poll.
	 */
	void mirrorUpdated(PeerId mirror, IdProof credentials) throws SecurityException;
}