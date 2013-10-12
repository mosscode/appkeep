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
package com.moss.appkeep.swing.browser;

import java.awt.BorderLayout;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;

import com.moss.appkeep.api.AppkeepService;
import com.moss.appkeep.swing.browser.maven.MavenDataTreeModel;
import com.moss.appkeep.swing.browser.maven.MavenGroupNode;
import com.moss.identity.tools.IdProover;
import com.moss.rpcutil.proxy.ProxyProvider;
import com.moss.rpcutil.proxy.hessian.HessianProxyProvider;
import com.moss.swing.test.TestFrame;

public class AppkeepBrowser extends JPanel {
	public static void main(String[] args) {

		ProxyProvider proxies = new HessianProxyProvider();
		
		AppkeepService appkeep = proxies.getProxy(AppkeepService.class, args[0]);
		
		AppkeepBrowser b = new AppkeepBrowser();
		
		IdProover idProver = null;
		
		new TestFrame(b);
		b.browse(appkeep, idProver);
	}
	
	
	private final AppkeepBrowserView view = new AppkeepBrowserView();
	private AppkeepService keep;
	
	public AppkeepBrowser() {
		setLayout(new BorderLayout());
		add(view);
		
	}
	
	public void browse(AppkeepService keep, IdProover idProver){
		this.keep = keep;
		JTree tree = new JTree(new MavenDataTreeModel(new MavenGroupNode("", null, keep, idProver)));
		tree.setShowsRootHandles(true);
		tree.setRootVisible(false);
		view.holderPanel().add(new JScrollPane(tree));
		view.holderPanel().invalidate();
		view.validate();
		view.repaint();
	}
}
