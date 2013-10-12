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
package com.moss.appkeep.server.mirror;

import java.io.InputStream;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.moss.appkeep.api.AppkeepService;
import com.moss.appkeep.api.ComponentId;
import com.moss.appkeep.api.ComponentInfo;
import com.moss.appkeep.api.mirror.PeerId;
import com.moss.appkeep.api.security.UserAccountDownloadToken;
import com.moss.appkeep.api.select.ComponentSelector;
import com.moss.appkeep.api.select.DirectComponentSelector;
import com.moss.appkeep.server.KeepTool;
import com.moss.appkeep.server.config.PeerRegistration;
import com.moss.appkeep.server.data.Data;
import com.moss.identity.tools.IdProover;
import com.moss.rpcutil.proxy.ProxyFactory;
import com.moss.saturn.lobstore.LobStore;

public class Poller implements Runnable {
	private final Log log = LogFactory.getLog(getClass());
	private final long pollIntervalMillis = 1000 * 60;// every minute
	private final PeerId myId;
	private final List<PeerRegistration> peers;
	private final ProxyFactory proxies;
	private final IdProover prover;
	private final KeepTool tool;
	
	public Poller(PeerId myId, List<PeerRegistration> peers, ProxyFactory proxies, IdProover prover, final LobStore lobs, final Data data) {
		super();
		this.myId = myId;
		this.peers = peers;
		this.proxies = proxies;
		this.prover = prover;
		this.tool = new KeepTool(data, lobs, peers, proxies, myId, prover);
		new Thread(this).start();
	}

	public void run() {
		try {
			Thread.sleep(1000 * 10); // 10 second initial wait (just to let things get settled down)
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		while(true){
			log.info("Scanning peers for new components");
			for(PeerRegistration peerInfo: peers){
				log.info("Scanning peer at " + peerInfo.url());
				try {
					AppkeepService peer = proxies.create(AppkeepService.class, peerInfo.url());
					ComponentId id = peer.next(null, myId, prover.giveProof());
					while(id!=null){
						log.info("Found component to download: " + id);
						ComponentSelector selector = new DirectComponentSelector(id);
						ComponentInfo info = peer.getInfo(selector, new UserAccountDownloadToken(prover.giveProof()));
						log.info("Downloading " + info);
						InputStream data = peer.download(selector, new UserAccountDownloadToken(prover.giveProof()));
						try {
							tool.putComponent(id, info.type(), data, info.handles());
						} finally {
							data.close();
						}
						id = peer.next(id, myId, prover.giveProof());
					}
				} catch (Throwable t) {
					log.error("Error polling peer at " + peerInfo.url() + ": " + t.getMessage(), t);
				}
			}
			log.info("Scan complete");
			try {
				// PAUSE TILL SOMEBODY WAKES US UP OR OTHERWISE UNTIL THE NEXT POLLING INTERVAL COMES AROUND 
				synchronized(this){
					wait(this.pollIntervalMillis);
				}
				log.info("Poller woke-up from sleep");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void pollNow(){
		synchronized(this){
			notifyAll();
		}
	}
}
