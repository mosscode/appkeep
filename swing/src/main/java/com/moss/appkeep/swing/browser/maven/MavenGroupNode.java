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
package com.moss.appkeep.swing.browser.maven;

import java.util.LinkedList;
import java.util.List;

import com.moss.appkeep.api.AppkeepService;
import com.moss.appkeep.api.ComponentInfo;
import com.moss.appkeep.api.maven.MavenGroupInfo;
import com.moss.identity.tools.IdProover;

public class MavenGroupNode {
	
	private final AppkeepService keep;
	private final IdProover idProver;
	
	private final String name;
	private final MavenGroupNode parentGroup;
	private final List<MavenComponentNode> components = new LinkedList<MavenComponentNode>();
	private final List<MavenGroupNode> subgroups = new LinkedList<MavenGroupNode>();
	
	private boolean hasLoaded = false;

	public MavenGroupNode(String name, MavenGroupNode parentGroup, AppkeepService keep, IdProover idProver) {
		super();
		this.name = name;
		this.parentGroup = parentGroup;
		this.keep = keep;
		this.idProver = idProver;
	}
	
	private synchronized void load(){
		MavenGroupInfo info;
		try {
			info = keep.mavenGroupInfo(name, idProver.giveProof());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		for(ComponentInfo c : info.components()){
			components.add(new MavenComponentNode(this, c));
		}
		for(String next : info.subgroups()){
			this.subgroups.add(new MavenGroupNode(next, this, keep, idProver));
		}
		hasLoaded = true;
	}
	
	public List<MavenComponentNode> components() {
		if(!hasLoaded){
			load();
		}
		return components;
	}
	
	public String name() {
		return name;
	}
	public MavenGroupNode parentGroup() {
		return parentGroup;
	}
	public List<MavenGroupNode> subgroups() {
		if(!hasLoaded){
			load();
		}
		return subgroups;
	}
	
	@Override
	public String toString() {
		int lastDotPos = name.lastIndexOf('.');
		if(lastDotPos==-1){
			return name;
		}else{
			return name.substring(lastDotPos + 1);
		}
	}
}
