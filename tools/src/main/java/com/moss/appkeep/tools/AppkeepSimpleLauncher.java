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
package com.moss.appkeep.tools;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import org.apache.log4j.BasicConfigurator;

import com.moss.appkeep.api.security.AnonDownloadGrantId;
import com.moss.appkeep.api.security.AnonDownloadToken;
import com.moss.appkeep.tools.cache.SimpleAppkeepCacheComponentResolver;
import com.moss.appkeep.tools.cache.SimpleAppkeepComponentCache;
import com.moss.launch.spec.JavaAppSpec;
import com.moss.launch.tools.simplelauncher.ComponentResolver;
import com.moss.launch.tools.simplelauncher.SimpleLauncher;

public class AppkeepSimpleLauncher {
	public static void main(String[] args) throws Exception {
		BasicConfigurator.resetConfiguration();
		BasicConfigurator.configure();
		
		final File launchSpec = new File(args[0]);
		final URL appkeepUrl = new URL(args[1]);
		
		JavaAppSpec spec = new SpecReader().read(launchSpec);
		
		DownloadTokenFactory tokenFactory = new AnonDownloadTokenFactory(new AnonDownloadToken(new AnonDownloadGrantId(args[2])));
		Process p = new AppkeepSimpleLauncher(appkeepUrl.toExternalForm(), tokenFactory).launch(spec);

		System.out.println("PROCESS START");

		// PIPE THE PROGRAM OUTPUT TO THE CONSOLE
		Dumper outDumper = new Dumper(p.getInputStream(), System.out);
		Dumper errDumper = new Dumper(p.getErrorStream(), System.err);

		// WAIT FOR EVERYTHING TO EXECUTE AND FLOW THROUGH
		int exitValue = p.waitFor();
		outDumper.join();
		errDumper.join();

		// REPORT RESULT
		System.out.println("PROCESS EXITED WITH " + exitValue);
	}

	private static class Dumper extends Thread {
		private final InputStream in;
		private final OutputStream out;

		public Dumper(InputStream in, OutputStream out) {
			super();
			this.in = in;
			this.out = out;
			start();
		}
		@Override
		public void run() {
			try {
				byte[] b = new byte[1024*100];
				for(int x = in.read(b);x!=-1;x = in.read(b)){
					out.write(b, 0, x);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	private final SimpleLauncher launcher = new SimpleLauncher();
	private final SimpleAppkeepComponentCache cache;
	private final ComponentResolver repo;
	
	public AppkeepSimpleLauncher(String url, DownloadTokenFactory tokenFactory) {
		super();
		this.cache = new SimpleAppkeepComponentCache(url, tokenFactory);
		this.repo = new SimpleAppkeepCacheComponentResolver(cache);
	}
	
	public Process launch(JavaAppSpec spec) throws IOException {
		return launcher.launch(spec, repo);
	}
}
