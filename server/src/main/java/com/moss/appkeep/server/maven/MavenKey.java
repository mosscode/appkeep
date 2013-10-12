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
package com.moss.appkeep.server.maven;

import com.moss.launch.components.util.MavenCoordinates;



public class MavenKey {
	private final String groupId;
	private final String artifactId;
	private final String version;
	private final String packaging;
	private final String classifier;
	
	public MavenKey(String toString){
		String[] parts = toString.split(":");
		
		if(parts.length<3){
			groupId = parts[0];
			
			if(parts.length==2){
				artifactId = parts[1];
			}else{
				artifactId = null;
			}
			
			version = null;
			packaging = null;
			classifier = null;
			
		}else{
			MavenCoordinates coordinates = new MavenCoordinates(toString);
			groupId = coordinates.groupId();
			artifactId = coordinates.artifactId();
			version = coordinates.version();
			packaging = coordinates.packaging();
			classifier = coordinates.classifier();
		}
	}
	
	public MavenKey(String groupId, String artifactId, String version) {
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.version = version;
		packaging = null;
		classifier = null;
	}
	
	public MavenKey(MavenCoordinates m) {
		if(m!=null){
			groupId = m.groupId();
			artifactId = m.artifactId();
			version = m.version();
			packaging = m.packaging();
			classifier = m.classifier();
		}else{
			groupId = null;
			artifactId = null;
			version = null;
			packaging = null;
			classifier = null;
		}
	}
	
	public boolean encompasses(MavenKey other){
		return matches(
				new String[]{
						groupId,
						artifactId,
						version
				},
				new String[]{
						other.groupId,
						other.artifactId,
						other.version
				}
		);
	}
	
	private boolean matches(String[] pattern, String[] value){
		for(int x = 0; x<pattern.length ;x++){
			final String expected = pattern[x];
			final String actual = value[x];
			
			if(expected==null){
				return true;
			}else{
				if(!expected.equals(actual)){
					return false;
				}
			}
		}
		return true;
	}

	public MavenCoordinates coordinates(){
		return new MavenCoordinates(groupId, artifactId, version, packaging, classifier);
	}
	
	@Override
	public String toString() {
		return coordinates().toString();
	}
	
	@Override
	public boolean equals(Object o) {
		return o instanceof MavenKey && ((MavenKey)o).toString().equals(toString());
	}
	
	@Override
	public int hashCode() {
		return coordinates().hashCode();
	}
	
	public String artifactId() {
		return artifactId;
	}
	public String groupId() {
		return groupId;
	}
	public String version() {
		return version;
	}
	public String packaging() {
		return packaging;
	}
	
	public String classifier() {
		return classifier;
	}
}
