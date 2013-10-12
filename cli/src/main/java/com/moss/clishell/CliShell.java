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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import jline.ConsoleReader;

public class CliShell {

	public CliShell(final String appName, final String prompt, final Command[] commands) throws IOException {
		final List<Command> allCommands = new LinkedList<Command>();
		allCommands.addAll(Arrays.asList(commands));
		allCommands.add(new Command("prints out this help message", "help"){
				@Override
				public void execute(String commandName, String line, PrintWriter out) {
					printHelp(out);
					out.flush();
				}
			});
		
		ConsoleReader reader = new ConsoleReader();
		reader.setBellEnabled(false);
		reader.getHistory().setHistoryFile(new File(System.getProperty("user.home"), "." + appName + ".history"));
		reader.setUseHistory(true);
		
		reader.addCompletor(new CompletorImpl(allCommands));

		String line;
		final PrintWriter out = new PrintWriter(System.out);
		
		while ((line = reader.readLine(prompt)) != null) {
			{
				String[] parts = line.split(" ");
				if(parts.length>0){
					String commandName = parts[0];
					Command c = null;
					for(Command next : allCommands){
						if(next.answersTo(commandName)){
							c = next;
						}
					}
					if(c!=null){
						try {
							c.execute(commandName, line, out);
						} catch (Throwable e) {
							e.printStackTrace();
						}
					}else{
						out.write(">> unknown command: " + commandName + "\n");
						printHelp(out);
					}
					out.flush();
				}else{
					out.write(">> you must enter a command \n");
					out.flush();
				}
			}
			
		}
	}
	
	protected void printHelp(PrintWriter out ){
	}
}
