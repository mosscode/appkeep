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
package com.moss.appkeep.server.config;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.moss.appkeep.api.endorse.x509.X509CertId;
import com.moss.appkeep.api.mirror.PeerId;
import com.moss.appkeep.server.jaxb.FileAdapter;
import com.moss.identity.standard.PasswordProofRecipie;

@XmlRootElement
public class ServerConfiguration {
	
	@XmlElement
	private PeerId id;
	
	@XmlElement(name="network-logon")
	private PasswordProofRecipie idProofRecipie;
	
	@XmlJavaTypeAdapter(FileAdapter.class)
	private File storageDir = new File("/var/businessmapserver");
	
	@XmlElement
	private String publishAddress = "localhost";
	
	@XmlElement
	private int publishPort = 4555;
	
	@XmlElement
	private String bindAddress = "localhost";
	
	@XmlElement
	private int bindPort = 4555;
	
	
	@XmlElement(name="administrator")
	private List<AdministratorConfig> administrators = new LinkedList<AdministratorConfig>();
	
	@XmlElement(name="peer")
	private List<PeerRegistration> peers = new LinkedList<PeerRegistration>();
	
	@XmlElement(name="x509-certificate")
	private List<CertificateRegistration> certificates = new LinkedList<CertificateRegistration>();
	
	public void validate(){
		if(id==null){
			throw new RuntimeException("You must specify an id");
		}
		if(idProofRecipie==null){
			throw new RuntimeException("You must specify a password");
		}
	}
	
	public PeerId id() {
		return id;
	}
	public void id(PeerId id) {
		this.id = id;
	}
	public List<PeerRegistration> peers() {
		return peers;
	}
	
	public File storageDir() {
		return storageDir;
	}
	
	public List<AdministratorConfig> administrators() {
		return administrators;
	}
	
	public void storageDir(File storageDir) {
		this.storageDir = storageDir;
	}

	public String publishAddress() {
		return publishAddress;
	}

	public void publishAddress(String publishAddress) {
		this.publishAddress = publishAddress;
	}

	public String bindAddress() {
		return bindAddress;
	}

	public void bindAddress(String bindAddress) {
		this.bindAddress = bindAddress;
	}

	public int publishPort() {
		return publishPort;
	}

	public void publishPort(int publishPort) {
		this.publishPort = publishPort;
	}

	public int bindPort() {
		return bindPort;
	}

	public void bindPort(int bindPort) {
		this.bindPort = bindPort;
	}
	
	public PasswordProofRecipie idProofRecipie() {
		return idProofRecipie;
	}
	public void idProofRecipie(PasswordProofRecipie idProofRecipie) {
		this.idProofRecipie = idProofRecipie;
	}
	
	public CertificateRegistration certificate(X509CertId id){
		for(CertificateRegistration r : certificates){
			if(r.id().equals(id)){
				return r;
			}
		}
		return null;
	}
	
	public List<CertificateRegistration> certificates() {
		return certificates;
	}
}
