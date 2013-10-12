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
package com.moss.appkeep.cli;

import java.io.File;
import java.io.FileFilter;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import com.moss.appkeep.api.endorse.JarsignEndorsement;
import com.moss.appkeep.api.endorse.x509.X509CertInfo;
import com.moss.appkeep.api.select.ComponentHandlesSelector;
import com.moss.appkeep.api.select.ComponentSelector;
import com.moss.appkeep.tools.MavenUploader;
import com.moss.clishell.Command;
import com.moss.launch.components.Component;

public class PostCommand extends Command {
	private final File m2Dir = new File(System.getProperty("user.home"), ".m2");
	
	private final File repo = new File(m2Dir, "repository");
	private final SharedState shared;
	
	public PostCommand(final SharedState shared) {
		super("resolves the components referenced in a launch spec using the local maven repo & posts them to the keep", "post");
		this.shared = shared;
	}
	

	@Override
	public void execute(String commandName, String line, PrintWriter out) {
		if(shared.keep()==null){
			out.println("Not currently connected to a keep.  Use the 'connect' command for this.");
		}else{
			final String[] parts = line.split(" ");
			final String mavenPart = parts[1];
			final String certLabel;
			if(parts.length>2){
				String candiate = parts[2];
				if(candiate.startsWith("-")){
					certLabel = null;
				}else{
					certLabel = parts[2].trim();
				}
			}else{
				certLabel = null;
			}
			
			String[] mavenParts = mavenPart.split(":");
			final String group = mavenParts[0];
			final String artifact = mavenParts[1];
			final String version = mavenParts[2];
			
			boolean force = false;
			for(final String next : parts){
				if(next.equals("-force")){
					force = true;
				}
			}
			
			X509CertInfo  cert = null;
			{
				if(certLabel!=null && certLabel.trim().length()>0){
					List<X509CertInfo> certs = shared.keep().listCertificates();
					
					for(X509CertInfo next : certs){
						if(next.name().equals(certLabel) || next.id().toString().equals(certLabel)){
							cert = next;
						}
					}
					
					if(cert==null){
						StringBuilder text = new StringBuilder("No such certificate: \"" + certLabel + "\".  Options are: ");
						for(X509CertInfo next : certs){
							text.append("\n  \"");
							text.append(next.name());
							text.append('\"');
						}
						throw new RuntimeException(text.toString());
					}
				}
			}
			
			File dir = versionDir(group, artifact, version);
			
			for(File next : dir.listFiles()){
				if(next.getName().endsWith(".launch-spec.xml") || next.getName().endsWith(".applet-spec.xml")){
//					File launchFile = new File(dir, artifact + "-" + version + ".launch-spec.xml");
					File launchFile = next;
					List<Component> components;
					try {
						components = new MavenUploader().upload(launchFile, shared.keep(), shared.idProver(), force);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
					
					if(cert!=null){
						ComponentSelector[] selectors = new ComponentSelector[components.size()];
						
						for(int x=0;x<selectors.length;x++){
							selectors[x] = new ComponentHandlesSelector(components.get(x).artifactHandles());
						}
						
						shared.keep().endorse(selectors, new JarsignEndorsement(cert.id()));
					}
				}
			}
			
		}
		out.flush();
	}
	
	
	@Override
	public int complete(String commandName, String buffer, int cursor, List candidates) {
		String postArgsLine = buffer.substring(commandName.length());
		String[] postArgs = postArgsLine.trim().split(" ");
		
		if(postArgs.length>0){
			String mavenPart = postArgs[0];
			
			if(mavenPart.length()>0){
				String[] mavenParts = mavenPart.split(":");
				
				final String group, artifact, version;
				if(mavenParts.length>0){
					group = mavenParts[0];
				}else{
					group = null;
				}
				if(mavenParts.length>1){
					artifact = mavenParts[1];
				}else{
					artifact = null;
				}
				if(mavenParts.length>2){
					version = mavenParts[2];
				}else{
					version = null;
				}
				
				boolean mavenPartComplete = postArgs.length>1 || (postArgs.length==1 && !postArgsLine.endsWith(""));
				if(mavenPartComplete){
					// AUTOCOMPLETE THE LAUNCH SPEC
					final String launch;
					if(postArgs.length>1){
						launch = postArgs[1];
					}else{
						launch = "";
					}
					File dir = versionDir(group, artifact, version);
					File[] children = dir.listFiles(new FileFilter() {
						public boolean accept(File pathname) {
							System.out.println("Considering " + pathname.getAbsolutePath());
							if(pathname.getName().endsWith(".launch-spec.xml") && (launch.equals("") || pathname.getName().startsWith(launch))){
								return true;
							}else{
								return false;
							}
						}
					});
					
					if(children!=null){
						for(File child : children){
							candidates.add(mavenPart + " " + child.getName());
						}
					}
					
					
				}else{
					boolean endsWithColon = buffer.endsWith(":");
					if(mavenParts.length==0 || (mavenParts.length==1 && !endsWithColon)){
						// AUTOCOMPLETE THE GROUPID
						candidates.addAll(listGroupOptions(group));
					}else if(mavenParts.length==1 || (mavenParts.length==2 && !endsWithColon)){
						// AUTOCOMPLETE THE ARTIFACTID
						candidates.addAll(autocompleteDir(group + ":", "", groupDir(group), artifact));
					}else {
						// AUTOCOMPLETE THE VERSION
						candidates.addAll(autocompleteDir(group + ":" + artifact + ":", "", artifactDir(group, artifact), version));
					}
				}
		}
		
		}
		Collections.sort(candidates);
		return commandName.length() + 1;
	}
	
	private List<String> autocompleteDir(String prefix, String separator, File dir, final String pattern){
		final List<String> options = new LinkedList<String>();
		File[] children = dir.listFiles(new FileFilter() {
			public boolean accept(File pathname) {
				if(pattern==null || pattern.equals("")){
					return true;
				}else{
					return pathname.getName().startsWith(pattern);
				}
			}
		});
		
		if(children!=null){
			if(!prefix.equals("")){
				prefix = prefix + separator;
			}
			for(File child : children){
				options.add(prefix + child.getName());
			}
		}
		
		return options;
	}
	
	private File versionDir(String groupId, String artifactId, String version){
		return new File(artifactDir(groupId, artifactId), version);
	}
	private File artifactDir(String groupId, String artifactId){
		return new File(groupDir(groupId), artifactId);
	}
	
	private File groupDir(String groupId){
		String[] segments = groupId.split(Pattern.quote("."));
		
		File dir = repo;
		for(int x=0;x<segments.length;x++){
			dir = new File(dir, segments[x]);
		}
		
		return dir;
	}
	
	/*
	 * com  // complete the segment name
	 * com. // list the next segment options
	 * com: // list the artifact options
	 */
	private List<String> listGroupOptions(String pattern){
		String[] segments = pattern.split(Pattern.quote("."));
		
		File dir = repo;
		String prefix = "";
		for(int x=0;x<segments.length;x++){
			final String name = segments[x];
			File match = new File(dir, name);
			
//			System.out.println("  Match: " + match.getAbsolutePath());
			
			if(match.exists()){
				prefix = prefix.equals("")?name:prefix + "." + name;
				if(x==segments.length-1){
//					System.out.println("last");
					if(pattern.endsWith(".")){
						return completeGroupSubOptions(prefix + ".", match);
					}
				}else{
					dir = match;
				}
			}else{
				File[] options = dir.listFiles(new FileFilter() {
					public boolean accept(File pathname) {
						if(name.equals("")){
							return true;
						}else{
							return pathname.getName().startsWith(name);
						}
					}
				});
				List<String> groupIds = new LinkedList<String>();
				
				if(options!=null){
					if(!prefix.equals("")){
						prefix = prefix + ".";
					}
					for(File next : options){
						groupIds.add(prefix + next.getName());
				
					}
				}
				
				return groupIds;
			}
			
			
		}
		
		return Collections.emptyList();
	}
	
	private List<String> completeGroupSubOptions(String prefix, File dir){
		List<String> groupIds = new LinkedList<String>();
		
		File[] children = dir.listFiles();
		if(children!=null && children.length>0){
			for(File next : children){
				groupIds.add(prefix + next.getName());
			}
		}
		
		return groupIds;
	}
}
