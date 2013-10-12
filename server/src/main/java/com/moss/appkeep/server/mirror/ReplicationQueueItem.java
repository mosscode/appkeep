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

import org.joda.time.Instant;

import com.moss.appkeep.api.ComponentId;
import com.moss.appkeep.api.mirror.PeerId;

/**
 * This basically is an index key: things get sorted first by peer, then by component, then by whenQueued
 */
public class ReplicationQueueItem {
	private PeerId peer;
	private ComponentId component;
	private Instant whenQueued;
	
	public ReplicationQueueItem(String text) {
		try {
			String[] parts = text.split("\\|");
			if(parts.length>0){
				peer = new PeerId(parts[0]);
			}
			if(parts.length>1){
				component = new ComponentId(parts[1]);
			}
			if(parts.length>2){
				whenQueued = new Instant(Long.parseLong(parts[2]));
			}
		} catch (Throwable t) {
			throw new RuntimeException("Error parsing \"" + text + "\" : " + t.getMessage(), t);
		}
	}
	
	public ReplicationQueueItem(PeerId peer, ComponentId component, Instant whenQueued) {
		super();
		this.peer = peer;
		this.component = component;
		this.whenQueued = whenQueued;
	}

	public boolean encompasses(ReplicationQueueItem o){
		if(peer==null){
			return true;
		}else if(peer.equals(o.peer)){
			if(component==null){
				return true;
			}else if(component.equals(o.component)){
				if(whenQueued==null){
					return true;
				}else{
					return whenQueued.equals(o.whenQueued);
				}
			}else{
				return false;
			}
		}else{
			return false;
		}
	}
	
	@Override
	public String toString() {
		final StringBuilder text = new StringBuilder();
		
		if(peer!=null){
			text.append(peer.toString());
			if(component!=null){
				text.append("|");
				text.append(component.toString());
				if(whenQueued!=null){
					text.append("|");
					text.append(whenQueued.getMillis());
				}
			}
		}
		
		return text.toString();
	}
	
	public PeerId peer() {
		return peer;
	}
	public ComponentId component() {
		return component;
	}
	public Instant whenQueued() {
		return whenQueued;
	}
}
