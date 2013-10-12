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
package com.moss.clishell;

import java.util.Collections;
import java.util.List;

import jline.Completor;

public class CompletorImpl implements Completor {
	private final List<Command> commands;
	
	public CompletorImpl(List<Command> commands) {
		super();
		this.commands = commands;
	}


	public int complete(String buffer, int cursor, List candidates) {
		
		if (buffer == null || buffer.trim().length()==0) {
			for(Command next : commands){
				for(String name : next.names()){
					candidates.add(name);
				}
			}
			Collections.sort(candidates);
			return 0;
		}
		else {

			String start = (buffer == null) ? "" : buffer;

			String[] pieces = start.split("\\s");
			if(pieces.length==1 && !buffer.endsWith(" ")){
				// AUTOCOMPLETE A PARTIAL COMMAND
				String entry = pieces[0];
				for(Command next : commands){
					for(String command : next.names()){
						if(command.startsWith(entry)){
							candidates.add(command);
						}
					}
				}
				Collections.sort(candidates);
				return 0;
			}else if (pieces.length > 0) {
				// AUTOCOMPLETE A COMMAND'S PARAMETERS
				String commandName = pieces[0];
				
				for(Command c : commands){
					if(c.answersTo(commandName)){
						return c.complete(commandName, buffer, cursor, candidates);
					}
				}
				
				Collections.sort(candidates);
				return pieces[0].length() + 1;
			}
		}
		return 0;
	}

}
